package com.bbttvv.app.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val videoPubDateZone: ZoneId = ZoneId.systemDefault()
private val shortVideoPubDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd", Locale.US)
private val longVideoPubDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

fun formatShortVideoPubDate(pubdateSeconds: Long): String {
    if (pubdateSeconds <= 0L) return ""
    return runCatching {
        shortVideoPubDateFormatter.format(Instant.ofEpochSecond(pubdateSeconds).atZone(videoPubDateZone))
    }.getOrDefault("")
}

fun formatLongVideoPubDate(pubdateSeconds: Long): String {
    if (pubdateSeconds <= 0L) return "--"
    return runCatching {
        longVideoPubDateFormatter.format(Instant.ofEpochSecond(pubdateSeconds).atZone(videoPubDateZone))
    }.getOrDefault("--")
}
