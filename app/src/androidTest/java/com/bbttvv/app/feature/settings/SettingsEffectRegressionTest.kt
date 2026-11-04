package com.bbttvv.app.feature.settings

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bbttvv.app.core.player.BasePlayerViewModel
import com.bbttvv.app.core.player.PlayerDanmakuFilterContext
import com.bbttvv.app.core.player.PlayerDanmakuPipeline
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import com.bbttvv.app.data.repository.DanmakuUserFilter
import com.bbttvv.app.data.repository.DanmakuWebSetting
import com.bbttvv.app.feature.video.danmaku.ParsedDanmaku
import com.bbttvv.app.feature.video.danmaku.WeightedTextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsEffectRegressionTest {
    @Test
    fun filtersRebuildExistingSourceAndCanRestoreHiddenItems() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = DanmakuSettingsStore.getSettings(context).first()
        val store = ViewModelStore()
        lateinit var model: FilterPlayer
        try {
            DanmakuSettingsStore.updateSettings(context) {
                DanmakuSettings(followBiliShield = false, allowScroll = true)
            }
            withContext(Dispatchers.Main) {
                model = FilterPlayer()
                store.put("filter-test", model)
                model.publish(source())
            }
            awaitCount(model, 1)
            DanmakuSettingsStore.updateSettings(context) { it.copy(allowScroll = false) }
            awaitCount(model, 0)
            DanmakuSettingsStore.updateSettings(context) { it.copy(allowScroll = true) }
            awaitCount(model, 1)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            DanmakuSettingsStore.updateSettings(context) { original }
        }
    }

    @Test
    fun disablingAccountSyncDiscardsCachedAccountRestrictions() {
        val cloud = PlayerDanmakuFilterContext(
            followBiliShield = true,
            serverSetting = DanmakuWebSetting(true, false, true, true, true, true, true, 10),
            userFilter = DanmakuUserFilter(listOf("测试"), emptyList(), emptySet()),
        )
        fun count(follow: Boolean) = PlayerDanmakuPipeline.buildRenderPayload(
            source(), "test", cloud, DanmakuSettings(followBiliShield = follow),
        ).totalCount
        assertEquals(0, count(true))
        assertEquals(1, count(false))
    }

    @Test
    fun startingSpeedChoicePersistsBothFieldsAndReadsLegacyPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = PlayerSettingsStore.getSettingsSnapshot(context).first()
        try {
            PlayerSettingsStore.setStartingPlaybackSpeed(context, null)
            assertEquals(true, PlayerSettingsStore.getSettingsSnapshot(context).first().rememberLastPlaybackSpeed)
            PlayerSettingsStore.setStartingPlaybackSpeed(context, 1.5f)
            val selected = PlayerSettingsStore.getSettingsSnapshot(context).first()
            assertEquals(false, selected.rememberLastPlaybackSpeed)
            assertEquals(1.5f, selected.defaultPlaybackSpeed)
            assertEquals(1.5f, PlayerSettingsStore.getPreferredPlaybackSpeedSync(context))
        } finally {
            PlayerSettingsStore.setDefaultPlaybackSpeed(context, original.defaultPlaybackSpeed)
            PlayerSettingsStore.setRememberLastPlaybackSpeed(context, original.rememberLastPlaybackSpeed)
        }
    }

    private suspend fun awaitCount(model: FilterPlayer, count: Int) {
        withTimeout(5000) { model.danmakuPayload.first { it?.totalCount == count } }
    }

    private fun source() = ParsedDanmaku(
        standardList = listOf(WeightedTextData().apply {
            text = "测试弹幕"
            layerType = LAYER_TYPE_SCROLL
            weight = 5
            textColor = android.graphics.Color.WHITE
        }),
        advancedList = emptyList(),
    )

    private class FilterPlayer : BasePlayerViewModel() {
        suspend fun publish(source: ParsedDanmaku) = publishDanmakuPayload(source, "test")
    }
}
