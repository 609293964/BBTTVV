package com.bbttvv.app.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bbttvv.app.MainActivity
import com.bbttvv.app.ui.components.AppTopLevelTab.*
import com.bbttvv.app.ui.components.HomeTabLayout
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.theme.AppTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeTabEditorRemoteTest {
    @Test
    fun dynamicTabCanMoveHideAndShowWithoutLosingItsPlace() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val saved = AtomicReference<HomeTabLayout>()
        val original = HomeTabLayout(
            listOf(DYNAMIC, TODAY_WATCH, PROFILE, POPULAR, SEARCH, LIVE, RECOMMEND, WATCH_LATER),
            setOf(DYNAMIC, TODAY_WATCH, PROFILE, POPULAR, SEARCH, RECOMMEND),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun open(layout: HomeTabLayout) {
                saved.set(null)
                scenario.onActivity { activity ->
                    activity.setContent {
                        key(layout) {
                            AppTheme {
                                HomeTabEditor(layout, onDismiss = {}, onSave = { saved.set(it) })
                            }
                        }
                    }
                }
                assertTrue(device.wait(Until.hasObject(By.text("编辑导航栏")), 5000))
                device.waitForIdle()
            }
            fun focusedText(text: String): Boolean {
                val target = device.findObject(By.focused(true)) ?: return false
                return try {
                    target.text == text || target.hasObject(By.text(text))
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    false
                }
            }
            fun save(downPresses: Int) {
                repeat(downPresses) {
                    device.pressDPadDown()
                    device.waitForIdle()
                }
                device.pressDPadCenter()
                device.waitForIdle()
                assertNotNull(saved.get())
            }

            open(original)
            device.pressDPadDown()
            assertTrue(focusedText("推荐单（需启用插件） · 显示"))
            device.pressDPadLeft()
            device.waitForIdle()
            assertTrue(focusedText("推荐单（需启用插件） · 显示"))
            device.pressDPadCenter()
            save(8)
            val hidden = saved.get()
            assertEquals(TODAY_WATCH, hidden.order.first())
            assertFalse(TODAY_WATCH in hidden.visible)
            assertEquals(original.order.toSet(), hidden.order.toSet())
            assertEquals(false, TODAY_WATCH in AppTopLevelTab.resolveVisibleTabs(true, savedLayout = hidden.encode()))

            // Reopen a fresh editor from persisted data, including unavailable plugin tabs.
            open(HomeTabLayout.restore(hidden.encode(), false))
            assertTrue(device.wait(Until.hasObject(By.focused(true).hasDescendant(By.text("推荐单（需启用插件） · 隐藏"))), 5000))
            device.pressDPadCenter()
            device.pressDPadRight()
            device.waitForIdle()
            save(7)
            val shown = saved.get()
            assertEquals(listOf(DYNAMIC, TODAY_WATCH), shown.order.take(2))
            assertTrue(TODAY_WATCH in shown.visible)
            assertFalse(TODAY_WATCH in AppTopLevelTab.resolveVisibleTabs(false, savedLayout = shown.encode()))
            assertEquals(TODAY_WATCH,
                AppTopLevelTab.resolveVisibleTabs(true, savedLayout = shown.encode())[1])
        }
    }
}
