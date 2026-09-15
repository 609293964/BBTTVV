package com.bbttvv.app.core.store

import android.content.Context
import com.bbttvv.app.core.cache.PlayUrlCache
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.NavData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredAccountSession(
    val mid: Long,
    val name: String = "",
    val face: String = "",
    val sessData: String = "",
    val csrf: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val buvid3: String = "",
    val isVip: Boolean = false,
    val vipLabel: String = "",
    val lastUsedAt: Long = 0L
)

class AccountSessionStorageException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

object AccountSessionStore {
    private const val TAG = "AccountSessionStore"
    private const val SP_NAME = "multi_account_sessions"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_ACTIVE_MID = "active_mid"

    private val sessionSwitchMutex = Mutex()
    private val storageMutationLock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Strict read used by all mutation paths. Corrupt encrypted/JSON data is a failure,
     * never an empty account list, so a later mutation cannot silently overwrite it.
     */
    fun getAccountsResult(context: Context): Result<List<StoredAccountSession>> {
        val prefs = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ACCOUNTS, null).orEmpty()
        if (raw.isBlank()) return Result.success(emptyList())

        return runCatching {
            val payload = SecureStorageCipher.decryptOrPlaintext(raw)
                ?: throw AccountSessionStorageException("Stored account payload is empty")
            val accounts = json.decodeFromString<List<StoredAccountSession>>(payload)
                .sortedByDescending { it.lastUsedAt }

            if (!SecureStorageCipher.isEncrypted(raw)) {
                persistAccountsOrThrow(context, accounts)
            }
            accounts
        }.recoverCatching { error ->
            throw if (error is AccountSessionStorageException) {
                error
            } else {
                AccountSessionStorageException("Stored account sessions are corrupt or unreadable", error)
            }
        }
    }

    /** Compatibility read for UI display. Mutations must use [getAccountsResult]. */
    fun getAccounts(context: Context): List<StoredAccountSession> {
        return getAccountsResult(context).getOrElse { error ->
            Logger.e(TAG, "Unable to read stored accounts; preserving persisted data", error)
            emptyList()
        }
    }

    fun getActiveAccountMid(context: Context): Long? {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_ACTIVE_MID, 0L)
            .takeIf { it > 0L }
    }

    fun clearActiveAccount(context: Context) {
        val committed = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_MID)
            .commit()
        if (!committed) {
            Logger.w(TAG, "Failed to persist active-account clear")
            return
        }
        NetworkModule.clearRuntimeCookies()
        PlayUrlCache.rotateAccountScope()
    }

    fun removeAccount(context: Context, mid: Long): Boolean {
        synchronized(storageMutationLock) {
            val current = getAccountsResult(context).getOrElse { error ->
                Logger.e(TAG, "Refusing account removal because storage is unreadable", error)
                return false
            }
            if (current.none { it.mid == mid }) return false

            val updated = current.filterNot { it.mid == mid }
            val activeMid = getActiveAccountMid(context)
            val nextActiveMid = activeMid?.takeUnless { it == mid }
            val committed = persistAccountState(context, updated, nextActiveMid)
            if (!committed) return false

            if (activeMid == mid) {
                NetworkModule.clearRuntimeCookies()
                PlayUrlCache.rotateAccountScope()
            }
            return true
        }
    }

    suspend fun upsertCurrentAccount(
        context: Context,
        navData: NavData? = null
    ): StoredAccountSession? {
        val mid = navData?.mid?.takeIf { it > 0L } ?: TokenManager.midCache ?: return null
        val sessData = TokenManager.sessDataCache?.takeIf { it.isNotBlank() } ?: return null
        val accounts = getAccountsResult(context).getOrElse { error ->
            Logger.e(TAG, "Refusing account upsert because storage is unreadable", error)
            return null
        }
        val existing = accounts.associateBy { it.mid }
        val previous = existing[mid]
        val timestamp = System.currentTimeMillis()

        val updated = StoredAccountSession(
            mid = mid,
            name = navData?.uname?.ifBlank { previous?.name.orEmpty() } ?: previous?.name.orEmpty(),
            face = navData?.face?.ifBlank { previous?.face.orEmpty() } ?: previous?.face.orEmpty(),
            sessData = sessData,
            csrf = TokenManager.csrfCache.orEmpty(),
            accessToken = TokenManager.accessTokenCache.orEmpty(),
            refreshToken = TokenManager.refreshTokenCache.orEmpty(),
            buvid3 = TokenManager.buvid3Cache.orEmpty(),
            isVip = navData?.vip?.status == 1 || TokenManager.isVipCache,
            vipLabel = navData?.vip?.label?.text.orEmpty().ifBlank { previous?.vipLabel.orEmpty() },
            lastUsedAt = timestamp
        )

        val merged = existing.values
            .filterNot { it.mid == mid }
            .plus(updated)
            .sortedByDescending { it.lastUsedAt }

        return if (persistAccountState(context, merged, mid)) updated else null
    }

    suspend fun activateAccount(context: Context, mid: Long): Boolean {
        return sessionSwitchMutex.withLock {
            val accounts = getAccountsResult(context).getOrElse { error ->
                Logger.e(TAG, "Refusing account switch because storage is unreadable", error)
                return@withLock false
            }
            val target = accounts.firstOrNull { it.mid == mid } ?: return@withLock false
            if (target.sessData.isBlank()) return@withLock false

            val previousRuntime = captureRuntimeSession()
            try {
                // Prevent the old account's runtime cookies from being mixed with the target.
                NetworkModule.clearRuntimeCookies()
                TokenManager.applyStoredSession(
                    context = context,
                    sessData = target.sessData,
                    csrf = target.csrf,
                    mid = target.mid,
                    accessToken = target.accessToken,
                    refreshToken = target.refreshToken,
                    buvid3 = target.buvid3,
                    isVip = target.isVip
                )

                val timestamp = System.currentTimeMillis()
                val updatedAccounts = accounts.map {
                    if (it.mid == mid) it.copy(lastUsedAt = timestamp) else it
                }.sortedByDescending { it.lastUsedAt }

                if (!persistAccountState(context, updatedAccounts, mid)) {
                    throw AccountSessionStorageException("Failed to commit active account state")
                }

                // The transition is now committed. All account-sensitive play URLs belong
                // to the old epoch and must be invalidated before callers resume.
                PlayUrlCache.rotateAccountScope()
                NetworkModule.clearRuntimeCookies()
                true
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    restoreRuntimeSession(context, previousRuntime)
                }
                throw error
            } catch (error: Throwable) {
                Logger.e(TAG, "Account switch failed; restoring previous runtime session", error)
                withContext(NonCancellable) {
                    restoreRuntimeSession(context, previousRuntime)
                }
                false
            }
        }
    }

    private data class RuntimeSessionSnapshot(
        val sessData: String?,
        val csrf: String?,
        val mid: Long?,
        val accessToken: String?,
        val refreshToken: String?,
        val buvid3: String?,
        val isVip: Boolean
    )

    private fun captureRuntimeSession(): RuntimeSessionSnapshot {
        return RuntimeSessionSnapshot(
            sessData = TokenManager.sessDataCache,
            csrf = TokenManager.csrfCache,
            mid = TokenManager.midCache,
            accessToken = TokenManager.accessTokenCache,
            refreshToken = TokenManager.refreshTokenCache,
            buvid3 = TokenManager.buvid3Cache,
            isVip = TokenManager.isVipCache
        )
    }

    private suspend fun restoreRuntimeSession(
        context: Context,
        snapshot: RuntimeSessionSnapshot
    ) {
        val sessData = snapshot.sessData
        if (sessData.isNullOrBlank()) {
            TokenManager.clear(context)
        } else {
            TokenManager.applyStoredSession(
                context = context,
                sessData = sessData,
                csrf = snapshot.csrf.orEmpty(),
                mid = snapshot.mid ?: 0L,
                accessToken = snapshot.accessToken.orEmpty(),
                refreshToken = snapshot.refreshToken.orEmpty(),
                buvid3 = snapshot.buvid3.orEmpty(),
                isVip = snapshot.isVip
            )
        }
        NetworkModule.clearRuntimeCookies()
    }

    private fun persistAccountsOrThrow(
        context: Context,
        accounts: List<StoredAccountSession>
    ) {
        val activeMid = getActiveAccountMid(context)
        if (!persistAccountState(context, accounts, activeMid)) {
            throw AccountSessionStorageException("Failed to persist migrated account sessions")
        }
    }

    private fun persistAccountState(
        context: Context,
        accounts: List<StoredAccountSession>,
        activeMid: Long?
    ): Boolean {
        return synchronized(storageMutationLock) {
            val payload = SecureStorageCipher.encrypt(json.encodeToString(accounts))
            val editor = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCOUNTS, payload)
            if (activeMid != null && activeMid > 0L) {
                editor.putLong(KEY_ACTIVE_MID, activeMid)
            } else {
                editor.remove(KEY_ACTIVE_MID)
            }
            editor.commit()
        }
    }
}
