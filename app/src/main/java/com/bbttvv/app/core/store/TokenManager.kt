// 文件路径: core/store/TokenManager.kt
package com.bbttvv.app.core.store

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

object TokenManager {
    private val SESSDATA_KEY = stringPreferencesKey("sessdata")
    private val BUVID3_KEY = stringPreferencesKey("buvid3")
    private val buvid3Lock = Any()
    private val dataStoreObserverLock = Any()
    private val warmupLock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var dataStoreObserverJob: Job? = null

    @Volatile
    private var warmupSignal: CompletableDeferred<Unit>? = null

    @Volatile
    private var warmupCompleted = false

    // SharedPreferences 快速缓存。所有可复用认证凭据均使用 Android Keystore 加密后落盘。
    private const val SP_NAME = "token_backup_sp"
    private const val SP_KEY_SESS = "sessdata_backup"
    private const val SP_KEY_BUVID = "buvid3_backup"
    private const val SP_KEY_CSRF = "bili_jct_backup"
    private const val SP_KEY_MID = "mid_backup"
    private const val SP_KEY_ACCESS_TOKEN = "access_token_backup"
    private const val SP_KEY_REFRESH_TOKEN = "refresh_token_backup"
    private const val DEFAULT_BLOCKING_WARMUP_TIMEOUT_MS = 800L

    @Volatile
    var sessDataCache: String? = null
        private set

    @Volatile
    private var buvid3CacheBacking: String? = null

    var buvid3Cache: String?
        get() = synchronized(buvid3Lock) { buvid3CacheBacking }
        set(value) {
            synchronized(buvid3Lock) {
                buvid3CacheBacking = value?.takeIf { it.isNotBlank() }
            }
        }

    @Volatile
    var isVipCache: Boolean = false

    @Volatile
    var csrfCache: String? = null

    @Volatile
    var midCache: Long? = null

    @Volatile
    var accessTokenCache: String? = null
        private set

    @Volatile
    var refreshTokenCache: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        Logger.d("TokenManager", " init: lightweight context ready")
    }

    fun warmup(context: Context? = null) {
        val claim = claimWarmup(context) ?: return
        if (claim.shouldRun) {
            runWarmup(claim)
        } else {
            runBlocking {
                claim.signal.await()
            }
        }
    }

    suspend fun awaitWarmup(context: Context? = null) {
        if (warmupCompleted) return
        val claim = claimWarmup(context) ?: return
        if (claim.shouldRun) {
            withContext(Dispatchers.IO) {
                runWarmup(claim)
            }
        } else {
            claim.signal.await()
        }
    }

    fun awaitWarmupBlocking(
        context: Context? = null,
        timeoutMs: Long = DEFAULT_BLOCKING_WARMUP_TIMEOUT_MS
    ) {
        if (warmupCompleted) return
        val claim = claimWarmup(context) ?: return
        if (claim.shouldRun) {
            runWarmup(claim)
        } else {
            val completed = runBlocking {
                withTimeoutOrNull(timeoutMs) {
                    claim.signal.await()
                    true
                } ?: false
            }
            if (!completed) {
                Logger.w("TokenManager", " token warmup wait timed out after ${timeoutMs}ms")
            }
        }
    }

    private data class WarmupClaim(
        val context: Context,
        val signal: CompletableDeferred<Unit>,
        val shouldRun: Boolean
    )

    private fun claimWarmup(context: Context?): WarmupClaim? {
        val resolvedContext = resolveAppContext(context) ?: return null
        synchronized(warmupLock) {
            if (warmupCompleted) {
                return WarmupClaim(
                    context = resolvedContext,
                    signal = CompletableDeferred(Unit),
                    shouldRun = false
                )
            }

            val activeSignal = warmupSignal
            if (activeSignal != null) {
                return WarmupClaim(
                    context = resolvedContext,
                    signal = activeSignal,
                    shouldRun = false
                )
            }

            val newSignal = CompletableDeferred<Unit>()
            warmupSignal = newSignal
            return WarmupClaim(
                context = resolvedContext,
                signal = newSignal,
                shouldRun = true
            )
        }
    }

    private fun resolveAppContext(context: Context?): Context? {
        val resolved = context?.applicationContext ?: appContext
        if (resolved != null && appContext == null) {
            appContext = resolved
        }
        return resolved
    }

    private fun runWarmup(claim: WarmupClaim) {
        runCatching {
            performWarmup(claim.context)
        }.onFailure { error ->
            Logger.e("TokenManager", " token warmup failed", error)
        }

        synchronized(warmupLock) {
            warmupCompleted = true
            warmupSignal = null
        }
        claim.signal.complete(Unit)
    }

    private fun performWarmup(appContext: Context) {
        val sp = appContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sessDataCache = readSecureString(sp, SP_KEY_SESS)
        buvid3Cache = sp.getString(SP_KEY_BUVID, null)
        csrfCache = readSecureString(sp, SP_KEY_CSRF)
        midCache = sp.getLong(SP_KEY_MID, 0L).takeIf { it > 0 }
        accessTokenCache = readSecureString(sp, SP_KEY_ACCESS_TOKEN)
        refreshTokenCache = readSecureString(sp, SP_KEY_REFRESH_TOKEN)

        Logger.d(
            "TokenManager",
            " warmup: sessionLoaded=${!sessDataCache.isNullOrEmpty()}, " +
                "accessTokenLoaded=${!accessTokenCache.isNullOrEmpty()}, mid=$midCache"
        )
        startDataStoreObserver(appContext, sp)
    }

    fun getOrCreateBuvid3(): String {
        return synchronized(buvid3Lock) {
            val cached = buvid3CacheBacking
            if (!cached.isNullOrBlank()) {
                cached
            } else {
                generateBuvid3().also { buvid3CacheBacking = it }
            }
        }
    }

    private fun startDataStoreObserver(context: Context, sp: SharedPreferences) {
        synchronized(dataStoreObserverLock) {
            if (dataStoreObserverJob?.isActive == true) return
            dataStoreObserverJob = AppScope.ioScope.launch {
                context.dataStore.data.collect { prefs ->
                    val dsSessRaw = prefs[SESSDATA_KEY]
                    val dsSess = SecureStorageCipher.decryptOrPlaintext(dsSessRaw)
                    val dsBuvid = prefs[BUVID3_KEY]

                    if (!dsSess.isNullOrEmpty()) {
                        sessDataCache = dsSess
                        if (!SecureStorageCipher.isEncrypted(dsSessRaw)) {
                            context.dataStore.edit { mutablePrefs ->
                                mutablePrefs[SESSDATA_KEY] = SecureStorageCipher.encrypt(dsSess)
                            }
                        }
                    }

                    if (dsBuvid == null) {
                        val newBuvid = generateBuvid3()
                        saveBuvid3(context, newBuvid)
                    } else {
                        buvid3Cache = dsBuvid
                    }

                    val spSess = readSecureString(sp, SP_KEY_SESS)
                    if (sessDataCache != null && sessDataCache != spSess) {
                        sp.edit()
                            .putString(SP_KEY_SESS, SecureStorageCipher.encrypt(sessDataCache!!))
                            .apply()
                    }
                    if (buvid3Cache != null && buvid3Cache != sp.getString(SP_KEY_BUVID, null)) {
                        sp.edit().putString(SP_KEY_BUVID, buvid3Cache).apply()
                    }
                }
            }
        }
    }

    private suspend fun stopDataStoreObserver() {
        val job = synchronized(dataStoreObserverLock) {
            dataStoreObserverJob.also { dataStoreObserverJob = null }
        }
        job?.cancelAndJoin()
    }

    fun saveCsrf(context: Context, csrf: String) {
        csrfCache = csrf
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SP_KEY_CSRF, SecureStorageCipher.encrypt(csrf))
            .apply()
        Logger.d("TokenManager", " saveCsrf: credential updated")
    }

    fun saveMid(context: Context, mid: Long) {
        midCache = mid
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putLong(SP_KEY_MID, mid).apply()
        Logger.d("TokenManager", " saveMid: $mid")
    }

    fun saveAccessToken(context: Context, accessToken: String, refreshToken: String) {
        accessTokenCache = accessToken
        refreshTokenCache = refreshToken
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SP_KEY_ACCESS_TOKEN, SecureStorageCipher.encrypt(accessToken))
            .putString(SP_KEY_REFRESH_TOKEN, SecureStorageCipher.encrypt(refreshToken))
            .apply()
        Logger.d("TokenManager", " saveAccessToken: credentials updated")
    }

    fun saveVipStatus(isVip: Boolean) {
        isVipCache = isVip
    }

    suspend fun saveCookies(context: Context, sessData: String) {
        sessDataCache = sessData
        Logger.d("TokenManager", " saveCookies: session credential updated")

        val encrypted = SecureStorageCipher.encrypt(sessData)
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putString(SP_KEY_SESS, encrypted).apply()

        context.dataStore.edit { prefs ->
            prefs[SESSDATA_KEY] = encrypted
        }
    }

    suspend fun saveBuvid3(context: Context, buvid3: String) {
        buvid3Cache = buvid3

        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putString(SP_KEY_BUVID, buvid3).apply()

        context.dataStore.edit { prefs ->
            prefs[BUVID3_KEY] = buvid3
        }
    }

    suspend fun applyStoredSession(
        context: Context,
        sessData: String,
        csrf: String,
        mid: Long,
        accessToken: String,
        refreshToken: String,
        buvid3: String,
        isVip: Boolean
    ) {
        saveCookies(context, sessData)

        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        csrfCache = csrf.ifBlank { null }
        midCache = mid.takeIf { it > 0L }
        accessTokenCache = accessToken.ifBlank { null }
        refreshTokenCache = refreshToken.ifBlank { null }
        isVipCache = isVip

        sp.edit()
            .putString(SP_KEY_CSRF, csrfCache?.let(SecureStorageCipher::encrypt))
            .putLong(SP_KEY_MID, midCache ?: 0L)
            .putString(SP_KEY_ACCESS_TOKEN, accessTokenCache?.let(SecureStorageCipher::encrypt))
            .putString(SP_KEY_REFRESH_TOKEN, refreshTokenCache?.let(SecureStorageCipher::encrypt))
            .apply()

        if (buvid3.isNotBlank()) {
            saveBuvid3(context, buvid3)
        } else if (buvid3Cache.isNullOrBlank()) {
            saveBuvid3(context, generateBuvid3())
        }
    }

    fun getSessData(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            SecureStorageCipher.decryptOrPlaintext(prefs[SESSDATA_KEY])
        }
    }

    suspend fun clear(context: Context) {
        stopDataStoreObserver()

        sessDataCache = null
        buvid3Cache = null
        csrfCache = null
        midCache = null
        isVipCache = false
        accessTokenCache = null
        refreshTokenCache = null

        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(SP_KEY_SESS)
            .remove(SP_KEY_BUVID)
            .remove(SP_KEY_CSRF)
            .remove(SP_KEY_MID)
            .remove(SP_KEY_ACCESS_TOKEN)
            .remove(SP_KEY_REFRESH_TOKEN)
            .apply()

        context.dataStore.edit {
            it.remove(SESSDATA_KEY)
            it.remove(BUVID3_KEY)
        }
    }

    private fun readSecureString(sp: SharedPreferences, key: String): String? {
        val raw = sp.getString(key, null) ?: return null
        val plaintext = SecureStorageCipher.decryptOrPlaintext(raw)
        if (!plaintext.isNullOrEmpty() && !SecureStorageCipher.isEncrypted(raw)) {
            sp.edit().putString(key, SecureStorageCipher.encrypt(plaintext)).apply()
        }
        return plaintext
    }

    private fun generateBuvid3(): String {
        return UUID.randomUUID().toString().replace("-", "") + "infoc"
    }
}
