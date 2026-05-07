package com.bbttvv.app.core.store

private const val SPLASH_WALLPAPER_HISTORY_DELIMITER = "\n"
private const val SPLASH_WALLPAPER_HISTORY_MAX_COUNT = 12

internal fun decodeSplashWallpaperHistory(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return normalizeSplashWallpaperHistory(
        raw.split(SPLASH_WALLPAPER_HISTORY_DELIMITER),
        maxCount = SPLASH_WALLPAPER_HISTORY_MAX_COUNT
    )
}

internal fun encodeSplashWallpaperHistory(history: List<String>): String {
    return normalizeSplashWallpaperHistory(
        history,
        maxCount = SPLASH_WALLPAPER_HISTORY_MAX_COUNT
    ).joinToString(SPLASH_WALLPAPER_HISTORY_DELIMITER)
}

internal fun appendSplashWallpaperHistory(
    existing: List<String>,
    newUri: String,
    maxCount: Int = SPLASH_WALLPAPER_HISTORY_MAX_COUNT
): List<String> {
    val normalizedExisting = normalizeSplashWallpaperHistory(existing, maxCount)
    val normalizedNew = newUri.trim()
    if (normalizedNew.isEmpty()) return normalizedExisting
    return listOf(normalizedNew)
        .plus(normalizedExisting.filter { it != normalizedNew })
        .take(maxCount)
}

private fun normalizeSplashWallpaperHistory(
    items: List<String>,
    maxCount: Int
): List<String> {
    return items
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(maxCount)
        .toList()
}

