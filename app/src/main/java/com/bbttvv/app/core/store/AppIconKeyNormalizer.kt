package com.bbttvv.app.core.store

const val APP_ICON_COMPONENT_PACKAGE_NAME = "com.bbttvv.app"
const val APP_ICON_COMPAT_ALIAS_CLASS_NAME = "$APP_ICON_COMPONENT_PACKAGE_NAME.MainActivityAlias3D"

const val DEFAULT_APP_ICON_KEY = "icon_3d"

private val CANONICAL_APP_ICON_KEYS = setOf(
    "icon_3d",
<<<<<<< HEAD:app/src/main/java/com/bbttvv/app/core/store/AppIconKeyNormalizer.kt
    "icon_blue",
    "icon_neon",
    "icon_retro",
=======
    "icon_bilipai",
    "icon_bilipai_pink",
    "icon_bilipai_white",
    "icon_bilipai_monet",
>>>>>>> 66bf842c85f92ca468e1f91940f277d9739fd68f:app/src/main/java/com/android/purebilibili/core/store/AppIconKeyNormalizer.kt
    "icon_anime",
    "icon_flat",
    "icon_flat_material",
    "icon_telegram_blue",
    "icon_telegram_dark",
    "Yuki",
    "Headphone"
)

private val LAUNCHER_ALIAS_SUFFIX_BY_KEY = mapOf(
    "icon_3d" to "MainActivityAlias3DLauncher",
<<<<<<< HEAD:app/src/main/java/com/bbttvv/app/core/store/AppIconKeyNormalizer.kt
    "icon_blue" to "MainActivityAliasBlue",
    "icon_neon" to "MainActivityAliasNeon",
    "icon_retro" to "MainActivityAliasRetro",
=======
    "icon_bilipai" to "MainActivityAliasBiliPai",
    "icon_bilipai_pink" to "MainActivityAliasBiliPaiPink",
    "icon_bilipai_white" to "MainActivityAliasBiliPaiWhite",
    "icon_bilipai_monet" to "MainActivityAliasBiliPaiMonet",
>>>>>>> 66bf842c85f92ca468e1f91940f277d9739fd68f:app/src/main/java/com/android/purebilibili/core/store/AppIconKeyNormalizer.kt
    "icon_anime" to "MainActivityAliasAnime",
    "icon_flat" to "MainActivityAliasFlat",
    "icon_flat_material" to "MainActivityAliasFlatMaterial",
    "icon_telegram_blue" to "MainActivityAliasTelegramBlue",
    "icon_telegram_dark" to "MainActivityAliasDark",
    "Yuki" to "MainActivityAliasYuki",
    "Headphone" to "MainActivityAliasHeadphone"
)

fun normalizeAppIconKey(rawKey: String?): String {
    val key = rawKey?.trim().orEmpty()
    if (key.isEmpty()) return DEFAULT_APP_ICON_KEY

    return when (key) {
        "default", "3D" -> "icon_3d"
<<<<<<< HEAD:app/src/main/java/com/bbttvv/app/core/store/AppIconKeyNormalizer.kt
        "Anime" -> "icon_anime"
        "Blue" -> "icon_blue"
        "Retro" -> "icon_retro"
        "Flat" -> "icon_flat"
        "Flat Material", "FlatMaterial" -> "icon_flat_material"
        "Neon" -> "icon_neon"
=======
        "BiliPai", "bilipai", "Icon BiliPai" -> "icon_bilipai"
        "BiliPai Pink", "BiliPai 粉", "bilipai_pink" -> "icon_bilipai_pink"
        "BiliPai White", "BiliPai 白", "bilipai_white" -> "icon_bilipai_white"
        "BiliPai Monet", "BiliPai 莫奈", "bilipai_monet" -> "icon_bilipai_monet"
        "Anime" -> "icon_anime"
        "Flat" -> "icon_flat"
>>>>>>> 66bf842c85f92ca468e1f91940f277d9739fd68f:app/src/main/java/com/android/purebilibili/core/store/AppIconKeyNormalizer.kt
        "Telegram Blue" -> "icon_telegram_blue"
        "Dark", "Telegram Dark" -> "icon_telegram_dark"
        "icon_headphone" -> "Headphone"
        else -> if (CANONICAL_APP_ICON_KEYS.contains(key)) key else DEFAULT_APP_ICON_KEY
    }
}

fun resolveAppIconLauncherAlias(packageName: String, rawKey: String?): String {
    val normalizedKey = normalizeAppIconKey(rawKey)
    val aliasSuffix = LAUNCHER_ALIAS_SUFFIX_BY_KEY[normalizedKey]
        ?: LAUNCHER_ALIAS_SUFFIX_BY_KEY.getValue(DEFAULT_APP_ICON_KEY)
    return "$APP_ICON_COMPONENT_PACKAGE_NAME.$aliasSuffix"
}

fun allManagedAppIconLauncherAliases(packageName: String): Set<String> {
    return LAUNCHER_ALIAS_SUFFIX_BY_KEY.values
        .map { aliasSuffix -> "$APP_ICON_COMPONENT_PACKAGE_NAME.$aliasSuffix" }
        .toSet()
}

