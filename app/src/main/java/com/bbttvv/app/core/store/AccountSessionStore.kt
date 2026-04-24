package com.bbttvv.app.core.store

import android.content.Context
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.NavData
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

object AccountSessionStore {
    private const val SP_NAME = "multi_account_sessions"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_ACTIVE_MID = "active_mid"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getAccounts(context: Context): List<StoredAccountSession> {
        val raw = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNTS, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredAccountSession>>(raw)
        }.getOrDefault(emptyList()).sortedByDescending { it.lastUsedAt }
    }

    fun getActiveAccountMid(context: Context): Long? {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_ACTIVE_MID, 0L)
            .takeIf { it > 0L }
    }

    fun clearActiveAccount(context: Context) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_MID)
            .apply()
        NetworkModule.clearRuntimeCookies()
    }

    fun removeAccount(context: Context, mid: Long): Boolean {
        val current = getAccounts(context)
        if (current.none { it.mid == mid }) return false

        val updated = current.filterNot { it.mid == mid }
        persistAccounts(context, updated)

        val editor = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
        if (getActiveAccountMid(context) == mid) {
            editor.remove(KEY_ACTIVE_MID)
        }
        editor.apply()
        return true
    }

    suspend fun upsertCurrentAccount(
        context: Context,
        navData: NavData? = null
    ): StoredAccountSession? {
        val mid = navData?.mid?.takeIf { it > 0L } ?: TokenManager.midCache ?: return null
        val sessData = TokenManager.sessDataCache?.takeIf { it.isNotBlank() } ?: return null
        val existing = getAccounts(context).associateBy { it.mid }
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
        persistAccounts(context, merged)
        setActiveAccountMid(context, mid)
        return updated
    }

    suspend fun activateAccount(context: Context, mid: Long): Boolean {
        val target = getAccounts(context).firstOrNull { it.mid == mid } ?: return false
        if (target.sessData.isBlank()) return false

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
        val updatedAccounts = getAccounts(context).map {
            if (it.mid == mid) it.copy(lastUsedAt = timestamp) else it
        }.sortedByDescending { it.lastUsedAt }
        persistAccounts(context, updatedAccounts)
        setActiveAccountMid(context, mid)
        return true
    }

    private fun persistAccounts(
        context: Context,
        accounts: List<StoredAccountSession>
    ) {
        val payload = json.encodeToString(accounts)
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNTS, payload)
            .apply()
    }

    private fun setActiveAccountMid(context: Context, mid: Long) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACTIVE_MID, mid)
            .apply()
    }
}

