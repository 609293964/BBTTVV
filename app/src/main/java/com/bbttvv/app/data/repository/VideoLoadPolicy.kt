package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.Page

internal enum class PlayUrlSource {
    APP,
    DASH,
    HTML5,
    LEGACY,
    GUEST
}

internal enum class PlayUrlRequestKind {
    INITIAL,
    EXPLICIT
}

internal data class VideoInfoLookupInput(
    val bvid: String,
    val aid: Long
)

internal fun resolveVideoInfoLookupInput(rawBvid: String, aid: Long): VideoInfoLookupInput? {
    val normalizedBvid = rawBvid.trim()
    if (normalizedBvid.startsWith("BV", ignoreCase = true)) {
        return VideoInfoLookupInput(bvid = normalizedBvid, aid = 0L)
    }

    if (aid > 0L) {
        return VideoInfoLookupInput(bvid = "", aid = aid)
    }

    val normalizedAv = normalizedBvid.lowercase()
    if (normalizedAv.startsWith("av")) {
        val parsedAid = normalizedAv.removePrefix("av").toLongOrNull()
        if (parsedAid != null && parsedAid > 0L) {
            return VideoInfoLookupInput(bvid = "", aid = parsedAid)
        }
    }

    return null
}

internal fun resolveRequestedVideoCid(
    requestCid: Long,
    infoCid: Long,
    pages: List<Page>
): Long {
    val normalizedRequestCid = requestCid.takeIf { it > 0L }
    val normalizedInfoCid = infoCid.takeIf { it > 0L }

    if (normalizedRequestCid != null) {
        if (pages.isEmpty() || pages.any { it.cid == normalizedRequestCid }) {
            return normalizedRequestCid
        }
    }

    return normalizedInfoCid ?: normalizedRequestCid ?: 0L
}

internal fun resolveInitialStartQuality(
    targetQuality: Int?,
    isAutoHighestQuality: Boolean,
    isLogin: Boolean,
    isVip: Boolean,
    auto1080pEnabled: Boolean
): Int {
    return when {
        isAutoHighestQuality && isVip -> 120
        isAutoHighestQuality && isLogin -> 80
        isAutoHighestQuality -> 64
        targetQuality != null -> targetQuality
        isVip -> 116
        isLogin && auto1080pEnabled -> 80
        isLogin -> 64
        else -> 32
    }
}

internal fun resolveVideoPlaybackAuthState(
    hasSessionCookie: Boolean,
    hasAccessToken: Boolean
): Boolean {
    return hasSessionCookie || hasAccessToken
}

internal fun shouldSkipPlayUrlCache(
    isAutoHighestQuality: Boolean,
    isVip: Boolean,
    audioLang: String?
): Boolean {
    return audioLang != null || (isAutoHighestQuality && isVip)
}

internal fun buildDashAttemptQualities(targetQn: Int): List<Int> {
    if (targetQn <= 80) return listOf(targetQn)

    val premiumFallbacks = listOf(120, 116, 112)
        .filter { quality -> quality <= targetQn }

    return (premiumFallbacks + 80).distinct()
}

internal fun resolveDashRetryDelays(targetQn: Int): List<Long> {
    // 标准画质（80/64 等）偶发返回空流时，给一次短重试窗口，避免误降级到游客 720。
    return if (targetQn <= 80) listOf(0L, 450L) else listOf(0L)
}

internal fun shouldRetryDashTrackRecovery(
    targetQn: Int,
    returnedQuality: Int,
    acceptQualities: List<Int>,
    dashVideoIds: List<Int>
): Boolean {
    if (targetQn <= 0 || targetQn > 80) return false
    if (returnedQuality >= targetQn) return false
    if (targetQn !in acceptQualities) return false
    return targetQn !in dashVideoIds
}

internal fun buildStartQualityDecisionSummary(
    bvid: String,
    cid: Long,
    userSettingQuality: Int?,
    startQuality: Int,
    isAutoHighestQuality: Boolean,
    isLoggedIn: Boolean,
    isVip: Boolean,
    auto1080pEnabled: Boolean,
    audioLang: String?
): String {
    return "PLAY_DIAG start_quality bvid=$bvid cid=$cid userSetting=${userSettingQuality ?: "null"} " +
        "start=$startQuality autoHighest=$isAutoHighestQuality " +
        "isLoggedIn=$isLoggedIn isVip=$isVip auto1080p=$auto1080pEnabled " +
        "audioLang=${audioLang ?: "default"}"
}

internal fun buildPlayUrlFetchSummary(
    bvid: String,
    cid: Long,
    source: PlayUrlSource,
    requestedQuality: Int,
    returnedQuality: Int,
    acceptQualities: List<Int>,
    dashVideoIds: List<Int>,
    hasDurl: Boolean,
    isLoggedIn: Boolean,
    isVip: Boolean,
    audioLang: String?
): String {
    return "PLAY_DIAG fetch_result bvid=$bvid cid=$cid source=$source requested=$requestedQuality " +
        "returned=$returnedQuality accept=$acceptQualities dash=$dashVideoIds " +
        "hasDurl=$hasDurl isLoggedIn=$isLoggedIn isVip=$isVip " +
        "audioLang=${audioLang ?: "default"}"
}

internal fun shouldCallAccessTokenApi(
    nowMs: Long,
    cooldownUntilMs: Long,
    hasAccessToken: Boolean
): Boolean {
    return hasAccessToken && nowMs >= cooldownUntilMs
}

internal fun shouldTryAppApiForTargetQuality(
    targetQn: Int,
    hasSessionCookie: Boolean = true,
    directedTrafficMode: Boolean = false
): Boolean {
    // PiliPlus parity: playback stays on the Web/WBI playurl path instead of
    // prioritizing the APP access_token endpoint for 1080P and premium tiers.
    return false
}

internal fun buildPlayUrlWbiBaseParams(
    bvid: String,
    cid: Long,
    qn: Int,
    audioLang: String? = null,
    tryLook: Boolean = false
): MutableMap<String, String> {
    val params = linkedMapOf(
        "bvid" to bvid,
        "cid" to cid.toString(),
        "qn" to qn.toString(),
        "fnval" to "4048",
        "fnver" to "0",
        "fourk" to "1",
        "voice_balance" to "1",
        "gaia_source" to "pre-load",
        "isGaiaAvoided" to "true",
        "web_location" to "1315873"
    )
    if (tryLook) {
        params["try_look"] = "1"
    }
    if (!audioLang.isNullOrEmpty()) {
        params["cur_language"] = audioLang
    }
    return params
}

internal fun shouldRequestPlayUrlTryLook(
    isLoggedIn: Boolean,
    auto1080pEnabled: Boolean
): Boolean {
    return !isLoggedIn && auto1080pEnabled
}

internal fun buildLoggedInPlaybackFallbackOrder(): List<PlayUrlSource> {
    return listOf(
        PlayUrlSource.DASH,
        PlayUrlSource.APP,
        PlayUrlSource.LEGACY,
        PlayUrlSource.GUEST
    )
}

internal fun buildGuestPlaybackFallbackOrder(): List<PlayUrlSource> {
    return listOf(
        PlayUrlSource.DASH,
        PlayUrlSource.LEGACY
    )
}

internal fun shouldAcceptAppApiResultForTargetQuality(
    requestKind: PlayUrlRequestKind,
    targetQn: Int,
    returnedQuality: Int,
    dashVideoIds: List<Int>
): Boolean {
    if (requestKind == PlayUrlRequestKind.INITIAL) {
        // Startup should keep any playable payload to avoid a hard failure page
        // when the service temporarily downgrades or omits the requested track.
        return returnedQuality > 0 || dashVideoIds.isNotEmpty()
    }

    // Explicit quality selection must respect the requested target for both VIP
    // and non-VIP users; otherwise the UI reports a successful switch while the
    // backend silently returns a lower tier.
    if (targetQn < 80) return true
    if (dashVideoIds.distinct().contains(targetQn)) return true
    return returnedQuality >= targetQn && returnedQuality > 0
}

internal fun buildGuestFallbackQualities(): List<Int> {
    return listOf(80, 64, 32)
}

internal fun shouldCachePlayUrlResult(
    source: PlayUrlSource,
    audioLang: String?
): Boolean {
    if (audioLang != null) return false
    return source != PlayUrlSource.GUEST
}

internal fun shouldFetchCommentEmoteMapOnVideoLoad(): Boolean {
    return false
}

internal fun shouldRefreshVipStatusOnVideoLoad(): Boolean {
    return false
}

internal fun shouldFetchInteractionStatusOnVideoLoad(): Boolean {
    return false
}

