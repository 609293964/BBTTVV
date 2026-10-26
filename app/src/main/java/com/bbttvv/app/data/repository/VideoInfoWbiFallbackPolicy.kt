package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.data.model.response.VideoDetailResponse
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程内记住"旧版 view 在当前网络被风控"，后续请求直接走 wbi/view，省去一次必然的 412。
 * 只存内存：重启后重新探测；直连 wbi/view 失败时清除，下次回到旧版优先。
 */
internal class VideoInfoEndpointState {
    private val legacyBlocked = AtomicBoolean(false)

    fun shouldSkipLegacy(): Boolean = legacyBlocked.get()

    fun markLegacyBlocked() {
        legacyBlocked.set(true)
    }

    fun onDirectWbiFailed() {
        legacyBlocked.set(false)
    }
}

/**
 * 旧版 `x/web-interface/view` 在部分出口 IP（如海外网络）上会被 HTTP 412 风控，
 * 而 WBI 版本 `x/web-interface/wbi/view` 仍可用。只在明确的风控信号下降级一次。
 */
internal fun shouldFallbackToWbiVideoInfo(error: Throwable): Boolean {
    return (error as? HttpException)?.code() == 412
}

internal fun shouldFallbackToWbiVideoInfo(apiCode: Int): Boolean {
    return apiCode == -412
}

/** WBI 签名失效或 wbi 接口被风控；-404/62002 等"视频本身不可用"的码不在此列。 */
internal fun isWbiVideoInfoRiskControlled(apiCode: Int): Boolean {
    return apiCode == -352 || apiCode == -403 || apiCode == -412
}

/**
 * 旧版 view 优先；被风控时降级 wbi/view。wbi 返回风控码时刷新密钥重试一次，
 * 只有 wbi 可用才记住跳过旧版；直连 wbi 仍失败则清除标记，下次回到旧版优先。
 */
internal suspend fun resolveVideoInfoWithWbiFallback(
    state: VideoInfoEndpointState,
    legacyRequest: suspend () -> VideoDetailResponse,
    wbiRequest: suspend () -> VideoDetailResponse,
    invalidateWbiKeys: () -> Unit,
    onLegacyBlocked: () -> Unit = {},
): VideoDetailResponse {
    if (state.shouldSkipLegacy()) {
        val response = try {
            requestWbiVideoInfoWithKeyRefresh(wbiRequest, invalidateWbiKeys)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state.onDirectWbiFailed()
            throw error
        }
        if (isWbiVideoInfoRiskControlled(response.code)) {
            state.onDirectWbiFailed()
        }
        return response
    }

    val legacyResponse = try {
        legacyRequest()
    } catch (error: Exception) {
        if (!shouldFallbackToWbiVideoInfo(error)) throw error
        null
    }
    if (legacyResponse != null && !shouldFallbackToWbiVideoInfo(legacyResponse.code)) {
        return legacyResponse
    }
    onLegacyBlocked()
    val response = requestWbiVideoInfoWithKeyRefresh(wbiRequest, invalidateWbiKeys)
    if (!isWbiVideoInfoRiskControlled(response.code)) {
        state.markLegacyBlocked()
    }
    return response
}

private suspend fun requestWbiVideoInfoWithKeyRefresh(
    wbiRequest: suspend () -> VideoDetailResponse,
    invalidateWbiKeys: () -> Unit,
): VideoDetailResponse {
    val response = wbiRequest()
    if (!isWbiVideoInfoRiskControlled(response.code)) return response
    invalidateWbiKeys()
    return wbiRequest()
}

internal fun buildVideoInfoWbiParams(
    bvid: String,
    aid: Long,
    imgKey: String,
    subKey: String,
): Map<String, String> {
    val params = if (bvid.isNotEmpty()) {
        linkedMapOf("bvid" to bvid)
    } else {
        linkedMapOf("aid" to aid.toString())
    }
    return WbiUtils.sign(
        params = params,
        imgKey = imgKey,
        subKey = subKey,
    )
}
