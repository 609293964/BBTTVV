// 文件路径: core/network/WbiKeyManager.kt
package com.bbttvv.app.core.network

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WBI 签名密钥管理器
 * 
 * 统一管理 WBI 签名所需的 imgKey 和 subKey，
 * 支持内存缓存、持久化存储和并发安全的刷新机制。
 */
object WbiKeyManager {
    
    private const val TAG = "WbiKeyManager"
    private const val SP_NAME = "wbi_keys_sp"
    private const val SP_KEY_IMG = "wbi_img_key"
    private const val SP_KEY_SUB = "wbi_sub_key"
    private const val SP_KEY_TIMESTAMP = "wbi_timestamp"
    
    // 缓存有效期：24 小时
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    // 预刷新阈值：剩余时间少于 1 小时时预刷新
    private const val PREFRESH_THRESHOLD_MS = 60 * 60 * 1000L
    
    // 内存缓存
    @Volatile
    private var cachedKeys: Pair<String, String>? = null
    @Volatile
    private var cacheTimestamp: Long = 0
    
    // 刷新互斥锁，防止并发刷新
    private val refreshMutex = Mutex()
    
    /**
     * 获取 WBI 密钥
     * 
     * 优先从内存缓存获取，如果缓存无效则从网络刷新。
     * 
     * @return Result 包含 (imgKey, subKey) 或错误
     */
    suspend fun getWbiKeys(): Result<Pair<String, String>> {
        // 1. 检查内存缓存
        val cached = cachedKeys
        if (cached != null && isCacheValid()) {
            com.bbttvv.app.core.util.Logger.d(TAG, " Using cached WBI keys")
            return Result.success(cached)
        }
        
        // 2. 需要刷新，使用互斥锁确保单次刷新
        return refreshMutex.withLock {
            // 双重检查：可能在等待锁的过程中其他协程已刷新
            val rechecked = cachedKeys
            if (rechecked != null && isCacheValid()) {
                com.bbttvv.app.core.util.Logger.d(TAG, " Using cached WBI keys (after lock)")
                return@withLock Result.success(rechecked)
            }
            
            // 执行刷新
            refreshKeysInternal()
        }
    }
    
    /**
     * 强制刷新 WBI 密钥
     */
    suspend fun refreshKeys(): Result<Pair<String, String>> {
        return refreshMutex.withLock {
            refreshKeysInternal()
        }
    }
    
    /**
     * 内部刷新逻辑
     */
    private suspend fun refreshKeysInternal(): Result<Pair<String, String>> {
        com.bbttvv.app.core.util.Logger.d(TAG, " Refreshing WBI keys from network...")
        
        return try {
            val api = NetworkModule.api
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            
            if (wbiImg != null) {
                val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
                
                cachedKeys = Pair(imgKey, subKey)
                cacheTimestamp = System.currentTimeMillis()
                
                //  自动持久化到 storage，下次启动时无需再请求网络
                try {
                    val context = NetworkModule.appContext
                    if (context != null) {
                        persistToStorage(context)
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, " Failed to persist WBI keys: ${e.message}")
                }
                
                com.bbttvv.app.core.util.Logger.d(TAG, " WBI keys refreshed successfully")
                Result.success(Pair(imgKey, subKey))
            } else {
                android.util.Log.e(TAG, " WBI keys not found in response")
                Result.failure(Exception("WBI keys not found in nav response"))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, " Failed to refresh WBI keys: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 使缓存失效
     */
    fun invalidateCache() {
        com.bbttvv.app.core.util.Logger.d(TAG, " Invalidating WBI keys cache")
        cachedKeys = null
        cacheTimestamp = 0
    }
    
    /**
     * 检查缓存是否有效
     */
    private fun isCacheValid(): Boolean {
        val age = System.currentTimeMillis() - cacheTimestamp
        return age < CACHE_DURATION_MS
    }
    
    /**
     * 检查是否需要预刷新（剩余时间 < 1 小时）
     */
    fun shouldPrefresh(): Boolean {
        val remaining = (cacheTimestamp + CACHE_DURATION_MS) - System.currentTimeMillis()
        return remaining < PREFRESH_THRESHOLD_MS
    }
    
    /**
     * 持久化到本地存储
     */
    fun persistToStorage(context: Context) {
        val keys = cachedKeys ?: return
        
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
            .putString(SP_KEY_IMG, keys.first)
            .putString(SP_KEY_SUB, keys.second)
            .putLong(SP_KEY_TIMESTAMP, cacheTimestamp)
            .apply()
        
        com.bbttvv.app.core.util.Logger.d(TAG, " WBI keys persisted to storage")
    }
    
    /**
     * 从本地存储恢复
     */
    fun restoreFromStorage(context: Context): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val imgKey = sp.getString(SP_KEY_IMG, null)
        val subKey = sp.getString(SP_KEY_SUB, null)
        val timestamp = sp.getLong(SP_KEY_TIMESTAMP, 0)
        
        if (imgKey != null && subKey != null && timestamp > 0) {
            cachedKeys = Pair(imgKey, subKey)
            cacheTimestamp = timestamp
            
            if (isCacheValid()) {
                com.bbttvv.app.core.util.Logger.d(TAG, " WBI keys restored from storage")
                return true
            } else {
                com.bbttvv.app.core.util.Logger.d(TAG, "⏰ Restored WBI keys are expired")
                invalidateCache()
            }
        } else {
            com.bbttvv.app.core.util.Logger.d(TAG, " No WBI keys found in storage")
        }
        
        return false
    }
    
    /**
     * 获取缓存统计信息（调试用）
     */
    fun getStats(): String {
        val hasKeys = cachedKeys != null
        val age = if (cacheTimestamp > 0) {
            (System.currentTimeMillis() - cacheTimestamp) / 1000 / 60
        } else 0
        return "WbiKeyManager: hasKeys=$hasKeys, ageMinutes=$age, valid=${isCacheValid()}"
    }
}

