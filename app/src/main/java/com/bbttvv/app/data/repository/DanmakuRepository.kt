// 文件路径: data/repository/DanmakuRepository.kt
package com.bbttvv.app.data.repository

import android.content.Context
import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.store.normalizeDanmakuDisplayArea
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.socket.LiveDanmakuClient
import com.bbttvv.app.core.network.socket.LiveDanmakuConnectionConfig
import com.bbttvv.app.core.network.socket.LiveDanmakuEndpoint
import com.bbttvv.app.core.util.runSuspendCatching
import com.bbttvv.app.data.model.response.DanmakuThumbupStatsItem
import com.bbttvv.app.data.model.response.LiveDanmuHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

internal data class DanmakuThumbupState(
    val likes: Int,
    val liked: Boolean
)

internal data class DanmakuCloudSyncSettings(
    val enabled: Boolean,
    val allowScroll: Boolean,
    val allowTop: Boolean,
    val allowBottom: Boolean,
    val allowColorful: Boolean,
    val allowSpecial: Boolean,
    val opacity: Float,
    val displayAreaRatio: Float,
    val speed: Float,
    val fontScale: Float
)

internal data class DanmakuCloudConfigPayload(
    val dmSwitch: String,
    val blockScroll: String,
    val blockTop: String,
    val blockBottom: String,
    val blockColor: String,
    val blockSpecial: String,
    val opacity: Float,
    val dmArea: Int,
    val speedPlus: Float,
    val fontSize: Float
)

internal data class DanmakuWebSetting(
    val dmSwitch: Boolean,
    val allowScroll: Boolean,
    val allowTop: Boolean,
    val allowBottom: Boolean,
    val allowColor: Boolean,
    val allowSpecial: Boolean,
    val aiEnabled: Boolean,
    val aiLevel: Int,
)

internal data class DanmakuViewMetadata(
    val segmentTotal: Int,
    val segmentPageSizeMs: Long,
    val count: Long,
    val setting: DanmakuWebSetting?,
    val commandDms: List<com.bbttvv.app.feature.video.danmaku.DanmakuProto.CommandDm>,
)

internal data class DanmakuUserFilter(
    val keywords: List<String> = emptyList(),
    val regexes: List<Regex> = emptyList(),
    val blockedUserMidHashes: Set<String> = emptySet(),
) {
    fun isEmpty(): Boolean {
        return keywords.isEmpty() && regexes.isEmpty() && blockedUserMidHashes.isEmpty()
    }

    companion object {
        val EMPTY = DanmakuUserFilter()
    }
}

private data class DanmakuUserFilterCache(
    val mid: Long,
    val fetchedAtMs: Long,
    val filter: DanmakuUserFilter,
)

private fun Boolean.toCloudFlag(): String = if (this) "true" else "false"

internal fun mapDanmakuDisplayAreaRatioToCloudValue(displayAreaRatio: Float): Int {
    if (displayAreaRatio <= 0f) return 0
    return (normalizeDanmakuDisplayArea(displayAreaRatio) * 100f).toInt()
}

internal const val DANMAKU_CLOUD_FONT_SIZE_MIN_EXCLUSIVE = 0.5f
internal const val DANMAKU_CLOUD_FONT_SIZE_MIN = 0.51f
internal const val DANMAKU_CLOUD_FONT_SIZE_MAX = 1.6f

internal fun mapDanmakuFontScaleToCloudFontSize(fontScale: Float): Float {
    val clamped = fontScale.coerceIn(0.3f, DANMAKU_CLOUD_FONT_SIZE_MAX)
    return if (clamped <= DANMAKU_CLOUD_FONT_SIZE_MIN_EXCLUSIVE) {
        DANMAKU_CLOUD_FONT_SIZE_MIN
    } else {
        clamped
    }
}

internal fun buildDanmakuCloudConfigPayload(settings: DanmakuCloudSyncSettings): DanmakuCloudConfigPayload {
    return DanmakuCloudConfigPayload(
        dmSwitch = settings.enabled.toCloudFlag(),
        blockScroll = settings.allowScroll.toCloudFlag(),
        blockTop = settings.allowTop.toCloudFlag(),
        blockBottom = settings.allowBottom.toCloudFlag(),
        blockColor = settings.allowColorful.toCloudFlag(),
        blockSpecial = settings.allowSpecial.toCloudFlag(),
        opacity = settings.opacity.coerceIn(0f, 1f),
        dmArea = mapDanmakuDisplayAreaRatioToCloudValue(settings.displayAreaRatio),
        speedPlus = settings.speed.coerceIn(0.4f, 1.6f),
        fontSize = mapDanmakuFontScaleToCloudFontSize(settings.fontScale)
    )
}

internal fun isDanmakuCloudSyncSuccessful(code: Int): Boolean = code == 0 || code == 23004

internal const val DANMAKU_SEGMENT_DURATION_MS = 360000L
internal const val DANMAKU_SEGMENT_SAFE_FALLBACK_COUNT = 3
private const val DM_FILTER_USER_CACHE_TTL_MS = 10L * 60 * 1000
private val MID_HASH_REGEX = Regex("^[0-9a-fA-F]{1,8}$")
private val MID_REGEX = Regex("^\\d{1,20}$")

internal fun estimateDanmakuCacheBytes(
    rawCacheBytes: Long,
    segmentCacheBytes: Long
): Long {
    return rawCacheBytes.coerceAtLeast(0L) + segmentCacheBytes.coerceAtLeast(0L)
}

data class DanmakuCacheStats(
    val rawEntryCount: Int,
    val segmentEntryCount: Int,
    val totalBytes: Long,
    val rawBytes: Long = 0L,
    val segmentBytes: Long = 0L,
    val rawMaxBytes: Long = 0L,
    val segmentMaxBytes: Long = 0L,
)

internal fun resolveDanmakuThumbupState(
    dmid: Long,
    data: Map<String, DanmakuThumbupStatsItem>
): DanmakuThumbupState? {
    val matched = data[dmid.toString()] ?: return null
    return DanmakuThumbupState(
        likes = matched.likes.coerceAtLeast(0),
        liked = matched.userLike == 1
    )
}

internal fun mapSendDanmakuErrorMessage(code: Int, fallbackMessage: String): String {
    return when (code) {
        -101 -> "请先登录"
        -102 -> "账号被封禁"
        -111 -> "鉴权失败，请重新登录"
        -400 -> "请求参数错误"
        -509 -> "请求过于频繁，请稍后再试"
        36700 -> "系统升级中，请稍后再试"
        36701 -> "弹幕包含被禁止的内容"
        36702 -> "弹幕长度超出限制"
        36703 -> "发送频率过快，请稍后再试"
        36704 -> "当前视频暂不允许发送弹幕"
        36705 -> "当前账号等级不足，无法发送该弹幕"
        36706 -> "当前账号等级不足，无法发送顶端弹幕"
        36707 -> "当前账号等级不足，无法发送底端弹幕"
        36708 -> "当前账号暂无彩色弹幕权限"
        36709 -> "当前账号等级不足，无法发送高级弹幕"
        36710 -> "当前账号暂无该弹幕样式权限"
        36711 -> "该视频禁止发送弹幕"
        36712 -> "当前账号等级限制，弹幕长度上限更低"
        36718 -> "当前账号不是大会员，无法发送渐变彩色弹幕"
        else -> fallbackMessage.ifEmpty { "发送弹幕失败 ($code)" }
    }
}

internal fun resolveDanmakuSegmentCount(
    durationMs: Long,
    metadataSegmentCount: Int?
): Int {
    val fromDuration = if (durationMs > 0) {
        ((durationMs + DANMAKU_SEGMENT_DURATION_MS - 1) / DANMAKU_SEGMENT_DURATION_MS).toInt()
    } else {
        0
    }
    if (fromDuration > 0) return fromDuration

    val fromMetadata = metadataSegmentCount?.coerceAtLeast(0) ?: 0
    if (fromMetadata > 0) return fromMetadata
    return DANMAKU_SEGMENT_SAFE_FALLBACK_COUNT
}

object DanmakuRepository {
    private val api = NetworkModule.api
    private val guestApi = NetworkModule.guestApi

    @Volatile
    private var danmakuUserFilterCache: DanmakuUserFilterCache? = null

    private val danmakuCacheManager = DanmakuCacheManager()
    private val rawXmlInFlight = ConcurrentHashMap<Long, Deferred<ByteArray?>>()
    private val segmentInFlight = ConcurrentHashMap<DanmakuSegmentCacheKey, Deferred<ByteArray?>>()
    private const val MAX_SEGMENT_PARALLELISM = 3

    fun configureDanmakuCache(context: Context) {
        danmakuCacheManager.configure(DanmakuCacheProfile.fromContext(context))
    }

    fun clearDanmakuCache() {
        rawXmlInFlight.values.forEach { it.cancel() }
        segmentInFlight.values.forEach { it.cancel() }
        rawXmlInFlight.clear()
        segmentInFlight.clear()
        danmakuCacheManager.clear()
        com.bbttvv.app.core.util.Logger.d("DanmakuRepo", " Danmaku cache cleared")
    }

    fun trimDanmakuCache(targetBytes: Long) {
        danmakuCacheManager.trimDanmakuCache(targetBytes)
    }

    fun trimDanmakuCache(maxRawBytes: Long, maxSegmentBytes: Long) {
        danmakuCacheManager.trimDanmakuCache(maxRawBytes, maxSegmentBytes)
    }

    fun trimDanmakuCacheToHalf() {
        danmakuCacheManager.trimToHalf()
    }

    fun trimDanmakuCacheToSmall() {
        danmakuCacheManager.trimToSmall()
    }

    fun getDanmakuCacheStats(): DanmakuCacheStats = danmakuCacheManager.stats()

    suspend fun warmUpDanmaku(
        cid: Long,
        durationMs: Long = 0L
    ) {
        if (cid <= 0L) return
        val segmentBytes = runSuspendCatching {
            getDanmakuSegments(
                cid = cid,
                durationMs = durationMs.coerceAtLeast(0L)
            )
        }.getOrDefault(emptyList())
        if (segmentBytes.isNotEmpty()) return
        currentCoroutineContext().ensureActive()
        getDanmakuRawData(cid)
    }

    suspend fun getDanmakuRawData(cid: Long): ByteArray? = withContext(Dispatchers.IO) {
        if (cid <= 0L) return@withContext null
        danmakuCacheManager.getRawXml(cid)?.let { return@withContext it }

        val request = rawXmlInFlight.computeIfAbsent(cid) {
            AppScope.ioScope.async { fetchDanmakuRawData(cid) }
        }
        try {
            request.await()
        } finally {
            if (request.isCompleted) rawXmlInFlight.remove(cid, request)
        }
    }

    private suspend fun fetchDanmakuRawData(cid: Long): ByteArray? {
        return try {
            val bytes = requestBytesWithGuestFallback(
                requestName = "getDanmakuXml cid=$cid",
                primary = { api.getDanmakuXml(cid).bytes() },
                fallback = { guestApi.getDanmakuXml(cid).bytes() }
            )
            if (bytes.isEmpty()) return null
            val result = decodeRawXmlBytes(bytes)
            if (result.isNotEmpty()) danmakuCacheManager.putRawXml(cid, result)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.bbttvv.app.core.util.Logger.e("DanmakuRepo", "getDanmakuRawData failed: cid=$cid", e)
            null
        }
    }

    private fun decodeRawXmlBytes(bytes: ByteArray): ByteArray {
        if (bytes[0] == 0x3C.toByte()) return bytes
        return try {
            val inflater = java.util.zip.Inflater(true)
            inflater.setInput(bytes)
            val outputStream = java.io.ByteArrayOutputStream(bytes.size * 3)
            val tempBuffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(tempBuffer)
                if (count == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) break
                }
                outputStream.write(tempBuffer, 0, count)
            }
            inflater.end()
            outputStream.toByteArray()
        } catch (e: Exception) {
            com.bbttvv.app.core.util.Logger.e("DanmakuRepo", "Deflate failed, using raw danmaku bytes", e)
            bytes
        }
    }

    internal suspend fun getDanmakuView(cid: Long, aid: Long): DanmakuViewMetadata? = withContext(Dispatchers.IO) {
        try {
            val bytes = requestBytesWithGuestFallback(
                requestName = "getDanmakuView cid=$cid aid=$aid",
                primary = { api.getDanmakuView(oid = cid, pid = aid).bytes() },
                fallback = { guestApi.getDanmakuView(oid = cid, pid = aid).bytes() }
            )
            if (bytes.isEmpty()) return@withContext null
            val reply = com.bbttvv.app.feature.video.danmaku.DanmakuProto.parseWebViewReply(bytes)
            val serverSetting = reply.dmSetting?.let { setting ->
                DanmakuWebSetting(
                    dmSwitch = setting.dmSwitch,
                    allowScroll = setting.allowScroll,
                    allowTop = setting.allowTop,
                    allowBottom = setting.allowBottom,
                    allowColor = setting.allowColor,
                    allowSpecial = setting.allowSpecial,
                    aiEnabled = setting.aiSwitch,
                    aiLevel = if (setting.aiLevel == 0) 3 else setting.aiLevel.coerceIn(0, 10)
                )
            }
            DanmakuViewMetadata(
                segmentTotal = reply.dmSge?.total?.coerceAtLeast(0)?.toInt() ?: 0,
                segmentPageSizeMs = reply.dmSge?.pageSize?.coerceAtLeast(0L) ?: 0L,
                count = reply.count,
                setting = serverSetting,
                commandDms = reply.commandDms,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", " getDanmakuView failed: ${e.message}")
            null
        }
    }

    internal suspend fun submitDanmakuVote(
        aid: Long,
        cid: Long,
        progressMs: Long,
        voteId: Long,
        voteType: Int,
        commandId: String,
        optionId: Int,
        hasSelfDefined: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val csrf = TokenManager.csrfCache.orEmpty()
        if (csrf.isBlank() || TokenManager.sessDataCache.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("请先登录后投票"))
        }
        runSuspendCatching {
            val response = api.submitDanmakuVote(
                aid = aid,
                cid = cid,
                progress = progressMs.coerceAtLeast(0L),
                voteId = voteId,
                voteType = voteType,
                commandId = commandId,
                optionId = optionId,
                hasSelfDefined = hasSelfDefined,
                polarisAppId = com.bbttvv.app.core.network.DANMAKU_VOTE_POLARIS_APP_ID,
                polarisPlatform = com.bbttvv.app.core.network.DANMAKU_VOTE_POLARIS_PLATFORM,
                csrf = csrf,
            )
            if (response.code != 0) {
                throw IllegalStateException(response.message.ifBlank { "投票失败(${response.code})" })
            }
        }
    }

    internal suspend fun getDanmakuUserFilter(forceRefresh: Boolean = false): DanmakuUserFilter = withContext(Dispatchers.IO) {
        val mid = TokenManager.midCache?.takeIf { it > 0L } ?: return@withContext DanmakuUserFilter.EMPTY
        if (TokenManager.sessDataCache.isNullOrBlank()) return@withContext DanmakuUserFilter.EMPTY

        val now = System.currentTimeMillis()
        val cached = danmakuUserFilterCache
        if (!forceRefresh && cached != null && cached.mid == mid && now - cached.fetchedAtMs < DM_FILTER_USER_CACHE_TTL_MS) {
            return@withContext cached.filter
        }

        try {
            val body = api.getDanmakuFilterUser().string()
            val json = JSONObject(body)
            if (json.optInt("code", 0) != 0) {
                return@withContext cached?.takeIf { it.mid == mid }?.filter ?: DanmakuUserFilter.EMPTY
            }

            val rules = json.optJSONObject("data")?.optJSONArray("rule")
            val keywords = mutableListOf<String>()
            val regexes = mutableListOf<Regex>()
            val blockedMidHashes = linkedSetOf<String>()
            for (index in 0 until (rules?.length() ?: 0)) {
                val item = rules?.optJSONObject(index) ?: continue
                val type = item.optInt("type", -1)
                val raw = item.optString("filter", item.optString("filter_content", item.optString("content", ""))).trim()
                if (raw.isBlank()) continue
                when (type) {
                    0 -> keywords.add(raw)
                    1 -> normalizeRegexRule(raw)?.let(regexes::add)
                    2 -> normalizeMidHashRule(raw)?.let(blockedMidHashes::add)
                }
            }
            DanmakuUserFilter(
                keywords = keywords.distinct(),
                regexes = regexes.distinctBy { it.pattern },
                blockedUserMidHashes = blockedMidHashes
            ).also { fetched ->
                danmakuUserFilterCache = DanmakuUserFilterCache(mid = mid, fetchedAtMs = now, filter = fetched)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cached?.takeIf { it.mid == mid }?.filter ?: DanmakuUserFilter.EMPTY
        }
    }

    suspend fun getDanmakuSegment(cid: Long, segmentIndex: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (segmentIndex <= 0) return@withContext null
        val cacheKey = DanmakuSegmentCacheKey(cid = cid, segmentIndex = segmentIndex)
        danmakuCacheManager.getSegment(cacheKey)?.let { return@withContext it }
        val request = segmentInFlight.computeIfAbsent(cacheKey) {
            AppScope.ioScope.async { fetchDanmakuSegment(cacheKey) }
        }
        try {
            request.await()
        } finally {
            if (request.isCompleted) segmentInFlight.remove(cacheKey, request)
        }
    }

    private suspend fun fetchDanmakuSegment(cacheKey: DanmakuSegmentCacheKey): ByteArray? {
        return try {
            val bytes = requestBytesWithGuestFallback(
                requestName = "getDanmakuSeg cid=${cacheKey.cid} segment=${cacheKey.segmentIndex}",
                primary = { api.getDanmakuSeg(oid = cacheKey.cid, segmentIndex = cacheKey.segmentIndex).bytes() },
                fallback = { guestApi.getDanmakuSeg(oid = cacheKey.cid, segmentIndex = cacheKey.segmentIndex).bytes() }
            )
            if (bytes.isNotEmpty()) {
                danmakuCacheManager.putSegment(cacheKey, bytes)
                bytes
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DanmakuRepo", " Segment ${cacheKey.segmentIndex} failed: ${e.message}")
            null
        }
    }

    suspend fun getDanmakuSegments(
        cid: Long,
        durationMs: Long,
        metadataSegmentCount: Int? = null
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val segmentCount = resolveDanmakuSegmentCount(durationMs, metadataSegmentCount)
        data class SegmentResult(val index: Int, val bytes: ByteArray)
        val segmentResults = coroutineScope {
            val semaphore = Semaphore(MAX_SEGMENT_PARALLELISM)
            (1..segmentCount).map { index ->
                async {
                    semaphore.withPermit {
                        getDanmakuSegment(cid = cid, segmentIndex = index)
                            ?.let { bytes -> SegmentResult(index, bytes) }
                    }
                }
            }.awaitAll()
        }
        segmentResults.filterNotNull().sortedBy { it.index }.map { it.bytes }
    }

    private suspend fun requestBytesWithGuestFallback(
        requestName: String,
        primary: suspend () -> ByteArray,
        fallback: suspend () -> ByteArray
    ): ByteArray {
        val primaryResult = runSuspendCatching { primary() }
        primaryResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val primaryError = primaryResult.exceptionOrNull()
        if (primaryError != null) {
            com.bbttvv.app.core.util.Logger.w(
                "DanmakuRepo",
                "$requestName primary failed, retrying without cookies: ${primaryError.message}"
            )
        }
        return runSuspendCatching { fallback() }
            .onFailure { error -> primaryError?.let { error.addSuppressed(it) } }
            .getOrThrow()
    }

    suspend fun sendDanmaku(
        aid: Long,
        cid: Long,
        message: String,
        progress: Long,
        color: Int = 16777215,
        fontSize: Int = 25,
        mode: Int = 1
    ): Result<com.bbttvv.app.data.model.response.SendDanmakuData> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
            if (message.isBlank()) return@withContext Result.failure(Exception("弹幕内容不能为空"))
            if (message.length > 100) return@withContext Result.failure(Exception("弹幕内容过长，最多 100 字"))
            val response = api.sendDanmaku(
                oid = cid,
                aid = aid,
                msg = message,
                progress = progress,
                color = color,
                fontsize = fontSize,
                mode = mode,
                csrf = csrf
            )
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(mapSendDanmakuErrorMessage(response.code, response.message)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "sendDanmaku exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun recallDanmaku(cid: Long, dmid: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val response = api.recallDanmaku(cid = cid, dmid = dmid, csrf = csrf)
            if (response.code == 0) {
                Result.success(response.message)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -111 -> "鉴权失败，请重新登录"
                    -400 -> "请求参数错误"
                    36301 -> "撤回次数已用完"
                    36302 -> "弹幕发送超过2分钟，无法撤回"
                    36303 -> "该弹幕无法撤回"
                    else -> response.message.ifEmpty { "撤回失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal suspend fun getDanmakuThumbupState(cid: Long, dmid: Long): Result<DanmakuThumbupState> = withContext(Dispatchers.IO) {
        try {
            if (dmid <= 0L) return@withContext Result.failure(IllegalArgumentException("弹幕ID无效"))
            val response = api.getDanmakuThumbupStats(oid = cid, ids = dmid.toString())
            if (response.code != 0) {
                return@withContext Result.failure(Exception(response.message.ifEmpty { "查询弹幕投票状态失败 (${response.code})" }))
            }
            val state = resolveDanmakuThumbupState(dmid = dmid, data = response.data)
                ?: return@withContext Result.failure(Exception("未找到该弹幕投票信息"))
            Result.success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun likeDanmaku(cid: Long, dmid: Long, like: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val response = api.likeDanmaku(oid = cid, dmid = dmid, op = if (like) 1 else 2, csrf = csrf)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -111 -> "鉴权失败，请重新登录"
                    -400 -> "请求参数错误"
                    65004 -> "已经点过赞了"
                    65005 -> "已经取消点赞了"
                    else -> response.message.ifEmpty { "操作失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportDanmaku(
        cid: Long,
        dmid: Long,
        reason: Int,
        content: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val response = api.reportDanmaku(cid = cid, dmid = dmid, reason = reason, content = content, csrf = csrf)
            if (response.code == 0) Result.success(Unit)
            else Result.failure(Exception(response.message.ifEmpty { "举报失败 (${response.code})" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal suspend fun syncDanmakuCloudConfig(settings: DanmakuCloudSyncSettings): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val payload = buildDanmakuCloudConfigPayload(settings)
            val response = api.updateDanmakuWebConfig(
                dmSwitch = payload.dmSwitch,
                blockScroll = payload.blockScroll,
                blockTop = payload.blockTop,
                blockBottom = payload.blockBottom,
                blockColor = payload.blockColor,
                blockSpecial = payload.blockSpecial,
                opacity = payload.opacity,
                dmArea = payload.dmArea,
                speedPlus = payload.speedPlus,
                fontSize = payload.fontSize,
                csrf = csrf
            )
            if (isDanmakuCloudSyncSuccessful(response.code)) Result.success(Unit)
            else Result.failure(Exception(response.message.ifEmpty { "弹幕云同步失败 (${response.code})" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveLiveDanmakuConnectionConfig(roomId: Long): Result<LiveDanmakuConnectionConfig> {
        return withContext(Dispatchers.IO) {
            try {
                SubtitleAndAuxRepository.ensureBuvid3()
                val realRoomId = runSuspendCatching { api.getLiveRoomInit(roomId) }
                    .getOrNull()
                    ?.data
                    ?.roomId
                    ?.takeIf { it > 0L }
                    ?: roomId

                val initialWbiKeys = com.bbttvv.app.core.network.WbiKeyManager.getWbiKeys().getOrNull()
                    ?: com.bbttvv.app.core.network.WbiKeyManager.refreshKeys().getOrNull()
                    ?: return@withContext Result.failure(Exception("获取 WBI 密钥失败，无法连接直播弹幕"))

                fun buildSignedParams(keys: Pair<String, String>): Map<String, String> {
                    return com.bbttvv.app.core.network.WbiUtils.sign(
                        mapOf("id" to realRoomId.toString(), "type" to "0", "web_location" to "444.8"),
                        keys.first,
                        keys.second
                    )
                }

                var response = api.getDanmuInfoWbi(buildSignedParams(initialWbiKeys))
                if (response.code != 0) {
                    com.bbttvv.app.core.network.WbiKeyManager.invalidateCache()
                    com.bbttvv.app.core.network.WbiKeyManager.refreshKeys().getOrNull()?.let { refreshedKeys ->
                        response = api.getDanmuInfoWbi(buildSignedParams(refreshedKeys))
                    }
                }
                if (response.code != 0) {
                    return@withContext Result.failure(Exception("获取弹幕服务信息失败: ${response.code} (msg=${response.message})"))
                }
                val info = response.data
                    ?: return@withContext Result.failure(Exception("获取弹幕服务信息失败: empty data"))
                val endpoints = buildLiveDanmakuEndpoints(info.host_list)
                if (endpoints.isEmpty()) return@withContext Result.failure(Exception("无可用弹幕服务器"))
                val hasSess = !TokenManager.sessDataCache.isNullOrEmpty()
                val uid = if (hasSess) (TokenManager.midCache ?: 0L) else 0L
                Result.success(
                    LiveDanmakuConnectionConfig(
                        endpoints = endpoints,
                        token = info.token,
                        realRoomId = realRoomId,
                        uid = uid,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DanmakuRepo", "Start live danmaku failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun buildLiveDanmakuEndpoints(hosts: List<LiveDanmuHost>): List<LiveDanmakuEndpoint> {
        return hosts
            .flatMap { host ->
                val hostName = host.host.trim()
                if (hostName.isBlank()) emptyList()
                else buildList {
                    if (host.wss_port > 0) add(LiveDanmakuEndpoint("wss://$hostName:${host.wss_port}/sub"))
                    if (host.ws_port > 0) add(LiveDanmakuEndpoint("ws://$hostName:${host.ws_port}/sub"))
                    if (host.port > 0 && host.port != host.wss_port && host.port != host.ws_port) {
                        add(LiveDanmakuEndpoint("ws://$hostName:${host.port}/sub"))
                    }
                }
            }
            .distinctBy { it.url }
            .sortedBy { endpoint -> liveDanmakuEndpointPriority(endpoint.url) }
    }

    private fun liveDanmakuEndpointPriority(url: String): Int {
        return when {
            url.startsWith("wss://") && url.contains(":443/") -> 0
            url.startsWith("wss://") -> 1
            else -> 2
        }
    }

    suspend fun startLiveDanmaku(
        scope: kotlinx.coroutines.CoroutineScope,
        roomId: Long
    ): Result<LiveDanmakuClient> {
        val config = resolveLiveDanmakuConnectionConfig(roomId)
            .getOrElse { return Result.failure(it) }
        currentCoroutineContext().ensureActive()
        val client = LiveDanmakuClient(
            scope = scope,
            reconnectConfigProvider = { resolveLiveDanmakuConnectionConfig(roomId).getOrNull() },
        )
        return try {
            client.connect(config)
            currentCoroutineContext().ensureActive()
            Result.success(client)
        } catch (e: CancellationException) {
            client.release()
            throw e
        } catch (e: Exception) {
            client.release()
            Result.failure(e)
        }
    }
}

private fun normalizeMidHashRule(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (MID_REGEX.matches(trimmed)) {
        val mid = trimmed.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return if (trimmed.length > 8) midHashOfMid(mid) else trimmed.lowercase(Locale.US).padStart(8, '0')
    }
    if (MID_HASH_REGEX.matches(trimmed)) return trimmed.lowercase(Locale.US).padStart(8, '0')
    return null
}

private fun normalizeRegexRule(raw: String): Regex? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching { Regex(trimmed) }.getOrNull()
}

private fun midHashOfMid(mid: Long): String {
    val crc = CRC32()
    crc.update(mid.toString().toByteArray(Charsets.UTF_8))
    return java.lang.Long.toHexString(crc.value).padStart(8, '0')
}
