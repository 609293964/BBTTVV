package com.bbttvv.app.core.store.player

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bbttvv.app.core.store.settingsDataStore
import com.bbttvv.app.feature.video.danmaku.DanmakuConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

enum class DanmakuFontWeightPreset {
    Normal,
    Bold,
}

enum class DanmakuLaneDensityPreset {
    Sparse,
    Standard,
    Dense,
}

data class DanmakuSettings(
    val enabled: Boolean = true,
    val opacity: Float = 0.9f,
    val textSizeSp: Int = 20,
    val fontWeight: DanmakuFontWeightPreset = DanmakuFontWeightPreset.Normal,
    val strokeWidthPx: Int = 2,
    val areaRatio: Float = 0.5f,
    val laneDensity: DanmakuLaneDensityPreset = DanmakuLaneDensityPreset.Standard,
    val speedLevel: Int = 4,
    val followBiliShield: Boolean = true,
    val aiShieldEnabled: Boolean = false,
    val aiShieldLevel: Int = 3,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColor: Boolean = true,
    val allowSpecial: Boolean = true,
)

val DANMAKU_OPACITY_VALUES: List<Float> = (1..20).map { it * 0.05f }
val DANMAKU_TEXT_SIZE_VALUES: List<Int> = (10..60 step 2).toList()
val DANMAKU_STROKE_WIDTH_VALUES: List<Int> = listOf(0, 2, 4, 6)
val DANMAKU_AREA_RATIO_VALUES: List<Float> = listOf(
    1f / 6f,
    1f / 5f,
    1f / 4f,
    1f / 3f,
    2f / 5f,
    1f / 2f,
    3f / 5f,
    2f / 3f,
    3f / 4f,
    4f / 5f,
    1f,
)
val DANMAKU_SPEED_FACTORS: List<Float> = listOf(1.45f, 1.32f, 1.18f, 1.0f, 0.9f, 0.82f, 0.74f, 0.68f, 0.63f, 0.6f)

fun normalizeDanmakuOpacity(value: Float): Float = findNearestOption(
    value = value.takeIf { it.isFinite() } ?: DanmakuSettings().opacity,
    options = DANMAKU_OPACITY_VALUES
)

fun normalizeDanmakuTextSizeSp(value: Int): Int = findNearestOption(
    value = value,
    options = DANMAKU_TEXT_SIZE_VALUES
)

fun normalizeDanmakuStrokeWidthPx(value: Int): Int = findNearestOption(
    value = value,
    options = DANMAKU_STROKE_WIDTH_VALUES
)

fun normalizeDanmakuAreaRatio(value: Float): Float = findNearestOption(
    value = value.takeIf { it.isFinite() } ?: DanmakuSettings().areaRatio,
    options = DANMAKU_AREA_RATIO_VALUES
)

fun normalizeDanmakuSpeedLevel(value: Int): Int = value.coerceIn(1, 10)

fun normalizeDanmakuAiShieldLevel(value: Int): Int = value.coerceIn(1, 10)

fun resolveDanmakuFontWeightPreset(value: String?): DanmakuFontWeightPreset {
    return DanmakuFontWeightPreset.entries.firstOrNull { it.name == value }
        ?: DanmakuFontWeightPreset.Normal
}

fun resolveDanmakuLaneDensityPreset(value: String?): DanmakuLaneDensityPreset {
    return DanmakuLaneDensityPreset.entries.firstOrNull { it.name == value }
        ?: DanmakuLaneDensityPreset.Standard
}

fun normalizeDanmakuSettings(settings: DanmakuSettings): DanmakuSettings {
    return settings.copy(
        opacity = normalizeDanmakuOpacity(settings.opacity),
        textSizeSp = normalizeDanmakuTextSizeSp(settings.textSizeSp),
        strokeWidthPx = normalizeDanmakuStrokeWidthPx(settings.strokeWidthPx),
        areaRatio = normalizeDanmakuAreaRatio(settings.areaRatio),
        speedLevel = normalizeDanmakuSpeedLevel(settings.speedLevel),
        aiShieldLevel = normalizeDanmakuAiShieldLevel(settings.aiShieldLevel),
    )
}

fun mapDanmakuTextSizeSpToFontScale(textSizeSp: Int): Float {
    return normalizeDanmakuTextSizeSp(textSizeSp).toFloat() / 42f
}

fun mapDanmakuLaneDensityToLineHeight(value: DanmakuLaneDensityPreset): Float {
    return when (value) {
        DanmakuLaneDensityPreset.Sparse -> 1.5f
        DanmakuLaneDensityPreset.Standard -> 1.35f
        DanmakuLaneDensityPreset.Dense -> 1.18f
    }
}

fun mapDanmakuSpeedLevelToSpeedFactor(speedLevel: Int): Float {
    return DANMAKU_SPEED_FACTORS[normalizeDanmakuSpeedLevel(speedLevel) - 1]
}

fun isWhiteDanmakuColor(color: Int): Boolean = (color and 0x00FFFFFF) == 0x00FFFFFF

fun shouldAllowDanmakuType(type: Int, settings: DanmakuSettings): Boolean {
    return when (type) {
        1 -> settings.allowScroll
        4 -> settings.allowBottom
        5 -> settings.allowTop
        7 -> settings.allowSpecial
        else -> true
    }
}

fun shouldAllowDanmakuColor(color: Int, settings: DanmakuSettings): Boolean {
    return settings.allowColor || isWhiteDanmakuColor(color)
}

fun shouldAllowDanmakuWeight(weight: Int, settings: DanmakuSettings): Boolean {
    return !settings.aiShieldEnabled || weight >= normalizeDanmakuAiShieldLevel(settings.aiShieldLevel)
}

fun shouldRenderDanmakuItem(type: Int, color: Int, settings: DanmakuSettings): Boolean {
    return shouldAllowDanmakuType(type, settings) && shouldAllowDanmakuColor(color, settings)
}

fun DanmakuSettings.toEngineConfig(): DanmakuConfig {
    val normalized = normalizeDanmakuSettings(this)
    return DanmakuConfig().apply {
        opacity = normalized.opacity
        fontScale = mapDanmakuTextSizeSpToFontScale(normalized.textSizeSp)
        itemTextSize = normalized.textSizeSp.toFloat() * 1.6f
        fontWeight = when (normalized.fontWeight) {
            DanmakuFontWeightPreset.Normal -> 5
            DanmakuFontWeightPreset.Bold -> 6
        }
        strokeEnabled = normalized.strokeWidthPx > 0
        strokeWidth = normalized.strokeWidthPx.toFloat()
        displayAreaRatio = normalized.areaRatio
        lineHeight = mapDanmakuLaneDensityToLineHeight(normalized.laneDensity)
        speedFactor = mapDanmakuSpeedLevelToSpeedFactor(normalized.speedLevel)
    }
}

object DanmakuSettingsStore {
    private val keyEnabled = booleanPreferencesKey("danmaku_enabled")
    private val keyOpacity = floatPreferencesKey("danmaku_opacity")
    private val keyTextSizeSp = intPreferencesKey("danmaku_text_size_sp")
    private val keyFontWeight = stringPreferencesKey("danmaku_font_weight")
    private val keyStrokeWidthPx = intPreferencesKey("danmaku_stroke_width_px")
    private val keyAreaRatio = floatPreferencesKey("danmaku_area_ratio")
    private val keyLaneDensity = stringPreferencesKey("danmaku_lane_density")
    private val keySpeedLevel = intPreferencesKey("danmaku_speed_level")
    private val keyFollowBiliShield = booleanPreferencesKey("danmaku_follow_bili_shield")
    private val keyAiShieldEnabled = booleanPreferencesKey("danmaku_ai_shield_enabled")
    private val keyAiShieldLevel = intPreferencesKey("danmaku_ai_shield_level")
    private val keyAllowScroll = booleanPreferencesKey("danmaku_allow_scroll")
    private val keyAllowTop = booleanPreferencesKey("danmaku_allow_top")
    private val keyAllowBottom = booleanPreferencesKey("danmaku_allow_bottom")
    private val keyAllowColor = booleanPreferencesKey("danmaku_allow_color")
    private val keyAllowSpecial = booleanPreferencesKey("danmaku_allow_special")

    private const val danmakuCachePrefs = "danmaku_settings_cache"
    private const val cacheKeyEnabled = "enabled"
    private const val cacheKeyOpacity = "opacity"
    private const val cacheKeyTextSizeSp = "text_size_sp"
    private const val cacheKeyFontWeight = "font_weight"
    private const val cacheKeyStrokeWidthPx = "stroke_width_px"
    private const val cacheKeyAreaRatio = "area_ratio"
    private const val cacheKeyLaneDensity = "lane_density"
    private const val cacheKeySpeedLevel = "speed_level"
    private const val cacheKeyFollowBiliShield = "follow_bili_shield"
    private const val cacheKeyAiShieldEnabled = "ai_shield_enabled"
    private const val cacheKeyAiShieldLevel = "ai_shield_level"
    private const val cacheKeyAllowScroll = "allow_scroll"
    private const val cacheKeyAllowTop = "allow_top"
    private const val cacheKeyAllowBottom = "allow_bottom"
    private const val cacheKeyAllowColor = "allow_color"
    private const val cacheKeyAllowSpecial = "allow_special"

    fun getSettings(context: Context): Flow<DanmakuSettings> = context.settingsDataStore.data
        .map { preferences -> preferences.toDanmakuSettings() }

    fun getSettingsSync(context: Context): DanmakuSettings {
        val prefs = context.getSharedPreferences(danmakuCachePrefs, Context.MODE_PRIVATE)
        return normalizeDanmakuSettings(
            DanmakuSettings(
                enabled = prefs.getBoolean(cacheKeyEnabled, DanmakuSettings().enabled),
                opacity = prefs.getFloat(cacheKeyOpacity, DanmakuSettings().opacity),
                textSizeSp = prefs.getInt(cacheKeyTextSizeSp, DanmakuSettings().textSizeSp),
                fontWeight = resolveDanmakuFontWeightPreset(
                    prefs.getString(cacheKeyFontWeight, DanmakuSettings().fontWeight.name)
                ),
                strokeWidthPx = prefs.getInt(cacheKeyStrokeWidthPx, DanmakuSettings().strokeWidthPx),
                areaRatio = prefs.getFloat(cacheKeyAreaRatio, DanmakuSettings().areaRatio),
                laneDensity = resolveDanmakuLaneDensityPreset(
                    prefs.getString(cacheKeyLaneDensity, DanmakuSettings().laneDensity.name)
                ),
                speedLevel = prefs.getInt(cacheKeySpeedLevel, DanmakuSettings().speedLevel),
                followBiliShield = prefs.getBoolean(
                    cacheKeyFollowBiliShield,
                    DanmakuSettings().followBiliShield
                ),
                aiShieldEnabled = prefs.getBoolean(
                    cacheKeyAiShieldEnabled,
                    DanmakuSettings().aiShieldEnabled
                ),
                aiShieldLevel = prefs.getInt(cacheKeyAiShieldLevel, DanmakuSettings().aiShieldLevel),
                allowScroll = prefs.getBoolean(cacheKeyAllowScroll, DanmakuSettings().allowScroll),
                allowTop = prefs.getBoolean(cacheKeyAllowTop, DanmakuSettings().allowTop),
                allowBottom = prefs.getBoolean(cacheKeyAllowBottom, DanmakuSettings().allowBottom),
                allowColor = prefs.getBoolean(cacheKeyAllowColor, DanmakuSettings().allowColor),
                allowSpecial = prefs.getBoolean(cacheKeyAllowSpecial, DanmakuSettings().allowSpecial),
            )
        )
    }

    suspend fun updateSettings(context: Context, transform: (DanmakuSettings) -> DanmakuSettings) {
        var normalized = DanmakuSettings()
        context.settingsDataStore.edit { preferences ->
            val current = preferences.toDanmakuSettings()
            normalized = normalizeDanmakuSettings(transform(current))
            preferences.applyDanmakuSettings(normalized)
        }
        cacheDanmakuSettings(context, normalized)
    }

    private fun Preferences.toDanmakuSettings(): DanmakuSettings {
        return normalizeDanmakuSettings(
            DanmakuSettings(
                enabled = this[keyEnabled] ?: DanmakuSettings().enabled,
                opacity = this[keyOpacity] ?: DanmakuSettings().opacity,
                textSizeSp = this[keyTextSizeSp] ?: DanmakuSettings().textSizeSp,
                fontWeight = resolveDanmakuFontWeightPreset(this[keyFontWeight]),
                strokeWidthPx = this[keyStrokeWidthPx] ?: DanmakuSettings().strokeWidthPx,
                areaRatio = this[keyAreaRatio] ?: DanmakuSettings().areaRatio,
                laneDensity = resolveDanmakuLaneDensityPreset(this[keyLaneDensity]),
                speedLevel = this[keySpeedLevel] ?: DanmakuSettings().speedLevel,
                followBiliShield = this[keyFollowBiliShield] ?: DanmakuSettings().followBiliShield,
                aiShieldEnabled = this[keyAiShieldEnabled] ?: DanmakuSettings().aiShieldEnabled,
                aiShieldLevel = this[keyAiShieldLevel] ?: DanmakuSettings().aiShieldLevel,
                allowScroll = this[keyAllowScroll] ?: DanmakuSettings().allowScroll,
                allowTop = this[keyAllowTop] ?: DanmakuSettings().allowTop,
                allowBottom = this[keyAllowBottom] ?: DanmakuSettings().allowBottom,
                allowColor = this[keyAllowColor] ?: DanmakuSettings().allowColor,
                allowSpecial = this[keyAllowSpecial] ?: DanmakuSettings().allowSpecial,
            )
        )
    }

    private fun MutablePreferences.applyDanmakuSettings(settings: DanmakuSettings) {
        this[keyEnabled] = settings.enabled
        this[keyOpacity] = settings.opacity
        this[keyTextSizeSp] = settings.textSizeSp
        this[keyFontWeight] = settings.fontWeight.name
        this[keyStrokeWidthPx] = settings.strokeWidthPx
        this[keyAreaRatio] = settings.areaRatio
        this[keyLaneDensity] = settings.laneDensity.name
        this[keySpeedLevel] = settings.speedLevel
        this[keyFollowBiliShield] = settings.followBiliShield
        this[keyAiShieldEnabled] = settings.aiShieldEnabled
        this[keyAiShieldLevel] = settings.aiShieldLevel
        this[keyAllowScroll] = settings.allowScroll
        this[keyAllowTop] = settings.allowTop
        this[keyAllowBottom] = settings.allowBottom
        this[keyAllowColor] = settings.allowColor
        this[keyAllowSpecial] = settings.allowSpecial
    }

    private fun cacheDanmakuSettings(context: Context, settings: DanmakuSettings) {
        context.getSharedPreferences(danmakuCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyEnabled, settings.enabled)
            .putFloat(cacheKeyOpacity, settings.opacity)
            .putInt(cacheKeyTextSizeSp, settings.textSizeSp)
            .putString(cacheKeyFontWeight, settings.fontWeight.name)
            .putInt(cacheKeyStrokeWidthPx, settings.strokeWidthPx)
            .putFloat(cacheKeyAreaRatio, settings.areaRatio)
            .putString(cacheKeyLaneDensity, settings.laneDensity.name)
            .putInt(cacheKeySpeedLevel, settings.speedLevel)
            .putBoolean(cacheKeyFollowBiliShield, settings.followBiliShield)
            .putBoolean(cacheKeyAiShieldEnabled, settings.aiShieldEnabled)
            .putInt(cacheKeyAiShieldLevel, settings.aiShieldLevel)
            .putBoolean(cacheKeyAllowScroll, settings.allowScroll)
            .putBoolean(cacheKeyAllowTop, settings.allowTop)
            .putBoolean(cacheKeyAllowBottom, settings.allowBottom)
            .putBoolean(cacheKeyAllowColor, settings.allowColor)
            .putBoolean(cacheKeyAllowSpecial, settings.allowSpecial)
            .apply()
    }
}

private fun findNearestOption(value: Float, options: List<Float>): Float {
    return options.minByOrNull { abs(it - value) } ?: options.first()
}

private fun findNearestOption(value: Int, options: List<Int>): Int {
    return options.minByOrNull { abs(it - value) } ?: options.first()
}
