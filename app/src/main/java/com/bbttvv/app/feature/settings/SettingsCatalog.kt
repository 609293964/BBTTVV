package com.bbttvv.app.feature.settings

enum class SettingsCategory(val title: String) {
    PLAYBACK("播放设置"),
    DANMAKU("弹幕设置"),
    AUDIO("音频配置"),
    UI_UX("界面交互"),
    FEED("推荐数据"),
    NETWORK("网络连接"),
    SYSTEM("系统关于"),
}

internal object SettingsCatalog {
    val keysByCategory: Map<SettingsCategory, List<String>> = mapOf(
        SettingsCategory.PLAYBACK to listOf(
            "settings_playback_title",
            "settings_auto_highest_quality",
            "settings_remember_last_speed",
            "settings_default_speed",
            "settings_auto_resume",
            "settings_playback_end",
        ),
        SettingsCategory.DANMAKU to DanmakuSettingsCatalog.keys,
        SettingsCategory.AUDIO to listOf(
            "settings_audio_title",
            "settings_volume_calibration",
            "settings_volume_balance",
            "settings_audio_passthrough",
        ),
        SettingsCategory.UI_UX to listOf(
            "settings_ui_ux_title",
            "settings_show_online_count",
            "settings_video_detail_comments",
            "settings_update_content_on_tab_focus",
            "settings_home_top_tab_select_on_focus",
            "settings_watch_later_in_top_tabs",
            "settings_single_back_to_home",
            "settings_theme_mode",
        ),
        SettingsCategory.FEED to listOf(
            "settings_feed_title",
            "settings_feed_api_type",
            "settings_home_refresh_count",
            "settings_dynamic_page_display_mode",
        ),
        SettingsCategory.NETWORK to listOf(
            "settings_network_title",
            "settings_user_agent",
            "settings_ipv4_only",
        ),
        SettingsCategory.SYSTEM to listOf(
            "settings_system_title",
            "settings_privacy_mode",
            "settings_blocked_ups_count",
            "settings_clear_cache",
            "settings_version",
            "settings_build_type",
            "settings_package_name",
        ),
    )

    fun itemIndex(category: SettingsCategory, key: String): Int {
        val keys = keysByCategory.getValue(category)
        return keys.indexOf(key).takeIf { it >= 0 } ?: 1.coerceAtMost(keys.lastIndex)
    }
}

internal object DanmakuSettingsCatalog {
    val keys = listOf(
        "danmaku_basic_title",
        "danmaku_default_enabled",
        "danmaku_opacity",
        "danmaku_text_size",
        "danmaku_font_weight",
        "danmaku_stroke_width",
        "danmaku_area_ratio",
        "danmaku_lane_density",
        "danmaku_speed",
        "danmaku_cloud_title",
        "danmaku_follow_bili",
        "danmaku_ai_shield",
        "danmaku_ai_shield_level",
        "danmaku_type_title",
        "danmaku_allow_scroll",
        "danmaku_allow_top",
        "danmaku_allow_bottom",
        "danmaku_allow_color",
        "danmaku_allow_special",
    )

    fun itemIndex(key: String): Int = keys.indexOf(key).takeIf { it >= 0 } ?: 1
}
