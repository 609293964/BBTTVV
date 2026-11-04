package com.bbttvv.app.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bbttvv.app.MainActivity
import com.bbttvv.app.ui.theme.AppTheme
import com.bbttvv.app.ui.components.LocalTvNoticeHostState
import com.bbttvv.app.ui.components.rememberTvNoticeHostState
import com.bbttvv.app.ui.focus.LocalTvFocusReturn
import com.bbttvv.app.ui.focus.TvFocusReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactSettingsRemoteTest {
    @Test
    fun compactRowsKeepChoiceCancellationAndCategoryNavigationReachable() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var exited = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalTvNoticeHostState provides rememberTvNoticeHostState(),
                        LocalTvFocusReturn provides remember { TvFocusReturn() },
                    ) {
                        AppTheme { SettingsScreen(onBack = { exited = true }) }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("优先最高画质")), 5000))
            device.executeShellCommand("screencap -p /data/local/tmp/bbttvv-compact-settings.png")
            device.pressDPadRight()
            device.pressDPadDown()
            device.pressDPadCenter()
            assertTrue(device.wait(Until.hasObject(By.text("跟随普通视频")), 5000))
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.text("跟随普通视频").focused(true)), 3000))
            device.waitForIdle()
            val focused = device.findObject(By.focused(true))
            assertTrue(focused != null && focused.hasObject(By.text("番剧和影视画质")))
            device.pressDPadDown()
            device.pressDPadCenter()
            device.waitForIdle()
            // The popup starts at the saved speed; the first option may be above the viewport.
            for (step in 0 until 12) {
                if (device.hasObject(By.text("记住上次"))) break
                device.pressDPadUp()
            }
            assertTrue(device.wait(Until.hasObject(By.text("记住上次")), 5000))
            device.pressBack()
            device.waitForIdle()
            val speedRow = device.findObject(By.focused(true))
            assertTrue(speedRow != null && speedRow.hasObject(By.text("起始倍速")))
            device.pressDPadLeft()
            device.pressDPadDown()
            device.pressDPadRight()
            assertTrue(device.wait(Until.hasObject(By.text("默认开启弹幕")), 5000))
            device.pressBack()
            device.pressBack()
            scenario.onActivity { assertEquals(true, exited) }
        }
    }
}
