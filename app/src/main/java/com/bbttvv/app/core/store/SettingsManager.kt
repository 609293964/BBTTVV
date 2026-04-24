package com.bbttvv.app.core.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbttvv.app.core.util.SubtitleAutoPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

internal const val DEFAULT_APP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

fun normalizeDanmakuDisplayArea(ratio: Float): Float {
    return ratio.coerceIn(0.1f, 1.0f)
}

data class AppNavigationSettings(
    val orderedVisibleTabIds: List<String> = DEFAULT_TOP_LEVEL_TABS,
    val startDestination: String = DEFAULT_TOP_LEVEL_TABS.first()
)

object SettingsManager {
    enum class FeedApiType(
        val value: String,
        val label: String
    ) {
        NORMAL("normal", "标准"),
        MOBILE("mobile", "移动端"),
        WEB("web", "网页端");

        companion object {
            fun fromValue(value: String?): FeedApiType {
                return entries.find { it.value == value } ?: WEB
            }
        }
    }

    enum class PlayerCdnPreference(
        val value: String,
        val label: String
    ) {
        BILIVIDEO("bilivideo", "bilivideo"),
        MCDN("mcdn", "mcdn");

        companion object {
            fun fromValue(value: String?): PlayerCdnPreference {
                return entries.find { it.value == value } ?: BILIVIDEO
            }
        }
    }

    private const val SYNC_PREFS_NAME = "settings_sync_cache"
    private const val CACHE_PRIVACY_MODE = "privacy_mode"
    private const val CACHE_SPONSOR_BLOCK_ENABLED = "sponsor_block_enabled"
    private const val CACHE_SPONSOR_BLOCK_AUTO_SKIP = "sponsor_block_auto_skip"
    private const val CACHE_AUTO_1080P = "exp_auto_1080p"
    private const val CACHE_PLAYER_CDN_PREFERENCE = "player_cdn_preference"
    private const val CACHE_HOME_REFRESH_COUNT = "home_refresh_count"
    private const val CACHE_FEED_API_TYPE = "feed_api_type"
    private const val CACHE_STOP_PLAYBACK_ON_EXIT = "stop_playback_on_exit"
    private const val CACHE_TOP_LEVEL_TABS = "top_level_tabs"
    private const val CACHE_CLICK_TO_PLAY = "click_to_play"
    private const val CACHE_BACKGROUND_PLAYBACK_ENABLED = "background_playback_enabled"
    private const val CACHE_AUDIO_FOCUS_ENABLED = "audio_focus_enabled"
    private const val CACHE_RESUME_PLAYBACK_PROMPT_ENABLED = "resume_playback_prompt_enabled"
    private const val CACHE_AUTO_ROTATE_ENABLED = "auto_rotate_enabled"
    private const val CACHE_SHOW_ONLINE_COUNT = "show_online_count"
    private const val CACHE_SUBTITLE_AUTO_PREFERENCE = "subtitle_auto_preference"
    private const val CACHE_AUTO_PLAY = "auto_play"
    private const val CACHE_USER_AGENT = "user_agent"
    private const val CACHE_IPV4_ONLY_ENABLED = "ipv4_only_enabled"
    private const val CACHE_PLAYER_AUTO_RESUME_ENABLED = "player_auto_resume_enabled"
    private const val CACHE_VIDEO_DETAIL_COMMENTS_ENABLED = "video_detail_comments_enabled"
    private const val CACHE_UPDATE_CONTENT_ON_TAB_FOCUS_ENABLED = "update_content_on_tab_focus_enabled"
    private const val CACHE_KEEP_ALIVE_TRANSIENT_TABS = "keep_alive_transient_tabs"
    private const val CACHE_WATCH_LATER_IN_TOP_TABS = "watch_later_in_top_tabs"

    private val keyPrivacyMode = booleanPreferencesKey(CACHE_PRIVACY_MODE)
    private val keySponsorBlockEnabled = booleanPreferencesKey(CACHE_SPONSOR_BLOCK_ENABLED)
    private val keySponsorBlockAutoSkip = booleanPreferencesKey(CACHE_SPONSOR_BLOCK_AUTO_SKIP)
    private val keyAuto1080p = booleanPreferencesKey(CACHE_AUTO_1080P)
    private val keyPlayerCdnPreference = stringPreferencesKey(CACHE_PLAYER_CDN_PREFERENCE)
    private val keyHomeRefreshCount = androidx.datastore.preferences.core.intPreferencesKey(CACHE_HOME_REFRESH_COUNT)
    private val keyFeedApiType = stringPreferencesKey(CACHE_FEED_API_TYPE)
    private val keyStopPlaybackOnExit = booleanPreferencesKey(CACHE_STOP_PLAYBACK_ON_EXIT)
    private val keyTopLevelTabs = stringPreferencesKey(CACHE_TOP_LEVEL_TABS)
    private val keyClickToPlay = booleanPreferencesKey(CACHE_CLICK_TO_PLAY)
    private val keyBackgroundPlaybackEnabled = booleanPreferencesKey(CACHE_BACKGROUND_PLAYBACK_ENABLED)
    private val keyAudioFocusEnabled = booleanPreferencesKey(CACHE_AUDIO_FOCUS_ENABLED)
    private val keyResumePlaybackPromptEnabled = booleanPreferencesKey(CACHE_RESUME_PLAYBACK_PROMPT_ENABLED)
    private val keyAutoRotateEnabled = booleanPreferencesKey(CACHE_AUTO_ROTATE_ENABLED)
    private val keyShowOnlineCount = booleanPreferencesKey(CACHE_SHOW_ONLINE_COUNT)
    private val keySubtitleAutoPreference = stringPreferencesKey(CACHE_SUBTITLE_AUTO_PREFERENCE)
    private val keyAutoPlay = booleanPreferencesKey(CACHE_AUTO_PLAY)
    private val keyUserAgent = stringPreferencesKey(CACHE_USER_AGENT)
    private val keyIpv4OnlyEnabled = booleanPreferencesKey(CACHE_IPV4_ONLY_ENABLED)
    private val keyPlayerAutoResumeEnabled = booleanPreferencesKey(CACHE_PLAYER_AUTO_RESUME_ENABLED)
    private val keyVideoDetailCommentsEnabled = booleanPreferencesKey(CACHE_VIDEO_DETAIL_COMMENTS_ENABLED)
    private val keyUpdateContentOnTabFocusEnabled = booleanPreferencesKey(CACHE_UPDATE_CONTENT_ON_TAB_FOCUS_ENABLED)
    private val keyKeepAliveTransientTabs = booleanPreferencesKey(CACHE_KEEP_ALIVE_TRANSIENT_TABS)
    private val keyWatchLaterInTopTabs = booleanPreferencesKey(CACHE_WATCH_LATER_IN_TOP_TABS)

    fun getWatchLaterInTopTabsEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyWatchLaterInTopTabs] ?: false
        }
    }

    suspend fun setWatchLaterInTopTabsEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyWatchLaterInTopTabs] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_WATCH_LATER_IN_TOP_TABS, enabled) }
    }

    fun getKeepAliveTransientTabsEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyKeepAliveTransientTabs] ?: false
        }
    }

    suspend fun setKeepAliveTransientTabsEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyKeepAliveTransientTabs] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_KEEP_ALIVE_TRANSIENT_TABS, enabled) }
    }

    fun observeAppNavigationSettings(context: Context): Flow<AppNavigationSettings> {
        return context.settingsDataStore.data.map(::mapNavigationSettingsFromPreferences)
    }

    fun getAuto1080p(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyAuto1080p] ?: true
        }
    }

    suspend fun setAuto1080p(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyAuto1080p] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_AUTO_1080P, enabled) }
    }

    fun getPlayerCdnPreference(context: Context): Flow<PlayerCdnPreference> {
        return context.settingsDataStore.data.map { preferences ->
            PlayerCdnPreference.fromValue(preferences[keyPlayerCdnPreference])
        }
    }

    suspend fun setPlayerCdnPreference(
        context: Context,
        preference: PlayerCdnPreference
    ) {
        updatePreference(context) { preferences ->
            preferences[keyPlayerCdnPreference] = preference.value
        }
        updateSyncCache(context) { putString(CACHE_PLAYER_CDN_PREFERENCE, preference.value) }
    }

    suspend fun consumeLegacySponsorBlockEnabled(context: Context, defaultValue: Boolean = true): Boolean {
        val legacyValue = context.settingsDataStore.data.map { preferences ->
            preferences[keySponsorBlockEnabled]
        }.first()
        clearLegacySponsorBlockEnabled(context)
        return legacyValue ?: defaultValue
    }

    suspend fun consumeLegacySponsorBlockAutoSkip(context: Context, defaultValue: Boolean = false): Boolean {
        val legacyValue = context.settingsDataStore.data.map { preferences ->
            preferences[keySponsorBlockAutoSkip]
        }.first()
        clearLegacySponsorBlockAutoSkip(context)
        return legacyValue ?: defaultValue
    }

    fun getPrivacyModeEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyPrivacyMode] ?: false
        }
    }

    suspend fun setPrivacyModeEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyPrivacyMode] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_PRIVACY_MODE, enabled) }
    }

    fun getClickToPlay(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyClickToPlay] ?: true
        }
    }

    suspend fun setClickToPlay(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyClickToPlay] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_CLICK_TO_PLAY, enabled) }
    }

    fun getResumePlaybackPromptEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyResumePlaybackPromptEnabled] ?: true
        }
    }

    suspend fun setResumePlaybackPromptEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyResumePlaybackPromptEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_RESUME_PLAYBACK_PROMPT_ENABLED, enabled) }
    }

    fun getAutoPlay(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyAutoPlay] ?: false
        }
    }

    suspend fun setAutoPlay(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyAutoPlay] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_AUTO_PLAY, enabled) }
    }

    fun getUserAgent(context: Context): Flow<String> {
        return context.settingsDataStore.data.map { preferences ->
            normalizeUserAgent(preferences[keyUserAgent])
        }
    }

    suspend fun setUserAgent(context: Context, userAgent: String) {
        val normalized = normalizeUserAgent(userAgent)
        updatePreference(context) { preferences ->
            preferences[keyUserAgent] = normalized
        }
        updateSyncCache(context) { putString(CACHE_USER_AGENT, normalized) }
    }

    fun getIpv4OnlyEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyIpv4OnlyEnabled] ?: false
        }
    }

    suspend fun setIpv4OnlyEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyIpv4OnlyEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_IPV4_ONLY_ENABLED, enabled) }
    }

    fun getPlayerAutoResumeEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyPlayerAutoResumeEnabled] ?: true
        }
    }

    suspend fun setPlayerAutoResumeEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyPlayerAutoResumeEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_PLAYER_AUTO_RESUME_ENABLED, enabled) }
    }

    fun getVideoDetailCommentsEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyVideoDetailCommentsEnabled] ?: false
        }
    }

    suspend fun setVideoDetailCommentsEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyVideoDetailCommentsEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_VIDEO_DETAIL_COMMENTS_ENABLED, enabled) }
    }

    fun getBackgroundPlaybackEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyBackgroundPlaybackEnabled] ?: false
        }
    }

    suspend fun setBackgroundPlaybackEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyBackgroundPlaybackEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_BACKGROUND_PLAYBACK_ENABLED, enabled) }
    }

    fun getAudioFocusEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyAudioFocusEnabled] ?: true
        }
    }

    suspend fun setAudioFocusEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyAudioFocusEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_AUDIO_FOCUS_ENABLED, enabled) }
    }

    fun getAutoRotateEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyAutoRotateEnabled] ?: false
        }
    }

    suspend fun setAutoRotateEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyAutoRotateEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_AUTO_ROTATE_ENABLED, enabled) }
    }

    fun getShowOnlineCount(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyShowOnlineCount] ?: true
        }
    }

    suspend fun setShowOnlineCount(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyShowOnlineCount] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_SHOW_ONLINE_COUNT, enabled) }
    }

    fun getUpdateContentOnTabFocusEnabled(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyUpdateContentOnTabFocusEnabled] ?: true
        }
    }

    suspend fun setUpdateContentOnTabFocusEnabled(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyUpdateContentOnTabFocusEnabled] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_UPDATE_CONTENT_ON_TAB_FOCUS_ENABLED, enabled) }
    }

    fun getSubtitleAutoPreference(context: Context): Flow<SubtitleAutoPreference> {
        return context.settingsDataStore.data.map { preferences ->
            resolveSubtitleAutoPreference(preferences[keySubtitleAutoPreference])
        }
    }

    suspend fun setSubtitleAutoPreference(
        context: Context,
        preference: SubtitleAutoPreference
    ) {
        updatePreference(context) { preferences ->
            preferences[keySubtitleAutoPreference] = preference.name
        }
        updateSyncCache(context) { putString(CACHE_SUBTITLE_AUTO_PREFERENCE, preference.name) }
    }

    fun getFeedApiType(context: Context): Flow<FeedApiType> {
        return context.settingsDataStore.data.map { preferences ->
            FeedApiType.fromValue(preferences[keyFeedApiType])
        }
    }

    suspend fun setFeedApiType(context: Context, feedApiType: FeedApiType) {
        updatePreference(context) { preferences ->
            preferences[keyFeedApiType] = feedApiType.value
        }
        updateSyncCache(context) { putString(CACHE_FEED_API_TYPE, feedApiType.value) }
    }

    fun getHomeRefreshCount(context: Context): Flow<Int> {
        return context.settingsDataStore.data.map { preferences ->
            normalizeHomeRefreshCount(preferences[keyHomeRefreshCount] ?: DEFAULT_HOME_REFRESH_COUNT)
        }
    }

    suspend fun setHomeRefreshCount(context: Context, count: Int) {
        val normalized = normalizeHomeRefreshCount(count)
        updatePreference(context) { preferences ->
            preferences[keyHomeRefreshCount] = normalized
        }
        updateSyncCache(context) { putInt(CACHE_HOME_REFRESH_COUNT, normalized) }
    }

    fun getStopPlaybackOnExit(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { preferences ->
            preferences[keyStopPlaybackOnExit] ?: false
        }
    }

    suspend fun setStopPlaybackOnExit(context: Context, enabled: Boolean) {
        updatePreference(context) { preferences ->
            preferences[keyStopPlaybackOnExit] = enabled
        }
        updateSyncCache(context) { putBoolean(CACHE_STOP_PLAYBACK_ON_EXIT, enabled) }
    }

    fun getTopLevelTabs(context: Context): Flow<List<String>> {
        return context.settingsDataStore.data.map { preferences ->
            (preferences[keyTopLevelTabs] ?: DEFAULT_TOP_LEVEL_TABS.joinToString(","))
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { DEFAULT_TOP_LEVEL_TABS }
        }
    }

    suspend fun setTopLevelTabs(context: Context, tabIds: List<String>) {
        val normalized = tabIds
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { DEFAULT_TOP_LEVEL_TABS }
        val serialized = normalized.joinToString(",")
        updatePreference(context) { preferences ->
            preferences[keyTopLevelTabs] = serialized
        }
        updateSyncCache(context) { putString(CACHE_TOP_LEVEL_TABS, serialized) }
    }

    fun isPrivacyModeEnabledSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_PRIVACY_MODE, false)
    }

    fun getAutoHighestQualitySync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_AUTO_1080P, true)
    }

    fun getPlayerCdnPreferenceSync(context: Context): PlayerCdnPreference {
        return PlayerCdnPreference.fromValue(
            context.syncPrefs().getString(CACHE_PLAYER_CDN_PREFERENCE, PlayerCdnPreference.BILIVIDEO.value)
        )
    }

    fun getFeedApiTypeSync(context: Context): FeedApiType {
        return FeedApiType.fromValue(
            context.syncPrefs().getString(CACHE_FEED_API_TYPE, FeedApiType.WEB.value)
        )
    }

    fun getHomeRefreshCountSync(context: Context): Int {
        return normalizeHomeRefreshCount(
            context.syncPrefs().getInt(CACHE_HOME_REFRESH_COUNT, DEFAULT_HOME_REFRESH_COUNT)
        )
    }

    fun getStopPlaybackOnExitSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_STOP_PLAYBACK_ON_EXIT, false)
    }

    fun getUserAgentSync(context: Context): String {
        return normalizeUserAgent(
            context.syncPrefs().getString(CACHE_USER_AGENT, DEFAULT_APP_USER_AGENT)
        )
    }

    fun getIpv4OnlyEnabledSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_IPV4_ONLY_ENABLED, false)
    }

    fun getPlayerAutoResumeEnabledSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_PLAYER_AUTO_RESUME_ENABLED, true)
    }

    fun getVideoDetailCommentsEnabledSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_VIDEO_DETAIL_COMMENTS_ENABLED, false)
    }

    fun getKeepAliveTransientTabsEnabledSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_KEEP_ALIVE_TRANSIENT_TABS, false)
    }

    fun getWatchLaterInTopTabsEnabledSync(context: Context): Boolean {
        return context.syncPrefs().getBoolean(CACHE_WATCH_LATER_IN_TOP_TABS, false)
    }

    internal fun mapNavigationSettingsFromPreferences(preferences: Preferences): AppNavigationSettings {
        val tabs = (preferences[keyTopLevelTabs] ?: DEFAULT_TOP_LEVEL_TABS.joinToString(","))
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { DEFAULT_TOP_LEVEL_TABS }
        return AppNavigationSettings(
            orderedVisibleTabIds = tabs,
            startDestination = tabs.first()
        )
    }

    private suspend fun updatePreference(
        context: Context,
        block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ) {
        context.settingsDataStore.edit { preferences ->
            block(preferences)
        }
    }

    private suspend fun clearLegacySponsorBlockEnabled(context: Context) {
        updatePreference(context) { preferences ->
            preferences.remove(keySponsorBlockEnabled)
        }
        updateSyncCache(context) {
            remove(CACHE_SPONSOR_BLOCK_ENABLED)
        }
    }

    private suspend fun clearLegacySponsorBlockAutoSkip(context: Context) {
        updatePreference(context) { preferences ->
            preferences.remove(keySponsorBlockAutoSkip)
        }
        updateSyncCache(context) {
            remove(CACHE_SPONSOR_BLOCK_AUTO_SKIP)
        }
    }

    private fun updateSyncCache(
        context: Context,
        block: android.content.SharedPreferences.Editor.() -> Unit
    ) {
        context.syncPrefs().edit().apply(block).apply()
    }

    private fun Context.syncPrefs() =
        getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
}

internal const val DEFAULT_HOME_REFRESH_COUNT = 20
internal val DEFAULT_TOP_LEVEL_TABS = listOf("HOME", "SEARCH", "PROFILE", "SETTINGS")

internal fun normalizeHomeRefreshCount(count: Int): Int {
    return count.coerceIn(10, 40)
}

internal fun resolveSubtitleAutoPreference(rawValue: String?): SubtitleAutoPreference {
    return SubtitleAutoPreference.entries.find { it.name == rawValue } ?: SubtitleAutoPreference.ON
}

internal fun normalizeUserAgent(value: String?): String {
    return value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_APP_USER_AGENT
}
