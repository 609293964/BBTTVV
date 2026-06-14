package com.bbttvv.app.ui.home

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.bbttvv.app.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvDpadHomeRegressionTest {
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val targetPackage: String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
    private val robot = TvDpadRobot(device, targetPackage)

    @Test
    fun coldLaunchFocusesHomeTopBarWithoutBackRecovery() = withHome {
        assertTrue("Cold launch should focus a home top tab", robot.waitUntilFocusedOnTopTab())
    }

    @Test
    fun topTabDownToContentAndUpReturnsTopBarFocus() = withHome {
        robot.ensureHomeTopBar()
        assumeTrue("推荐 tab should be focusable before D-Pad regression", robot.focusTopTab("推荐"))

        robot.press(KeyEvent.KEYCODE_DPAD_DOWN)
        robot.settle()
        assumeFalse("No focusable home content available on this device/data set", robot.isFocusedOnTopTab())

        robot.press(KeyEvent.KEYCODE_DPAD_UP)
        robot.waitUntilFocusedOnTopTab()

        assertTrue(robot.isFocusedOnTopTab())
    }

    @Test
    fun recommendDetailBackRestoresContentFocus() = withHome {
        robot.ensureHomeTopBar()
        assumeTrue(robot.focusTopTab("推荐"))
        assumeTrue("Recommend card is required for detail focus restore regression", robot.focusHomeContent())

        val before = robot.focusedLabel()
        robot.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assumeTrue("Detail page did not open from focused recommend card", robot.waitForAnyText("播放", "继续播放", "从头播放"))

        robot.press(KeyEvent.KEYCODE_BACK)
        robot.waitForHomeTopBar()

        val after = robot.focusedLabel()
        assertTrue(
            "Expected focus to return to the previous card or stay in home content; before=$before after=$after",
            after == before || !robot.isFocusedOnTopTab(),
        )
    }

    @Test
    fun dynamicPageDpadUpDownKeepsFocusInsideHome() = withHome {
        robot.ensureHomeTopBar()
        assumeTrue("动态 tab should be available", robot.focusTopTab("动态"))

        robot.press(KeyEvent.KEYCODE_DPAD_DOWN)
        robot.settle()
        assumeFalse("Dynamic content is not focusable on this device/account state", robot.isFocusedOnTopTab())

        robot.press(KeyEvent.KEYCODE_DPAD_DOWN)
        robot.settle()
        robot.press(KeyEvent.KEYCODE_DPAD_UP)
        robot.settle()

        assertTrue("Focus should remain inside BBTTVV after dynamic D-Pad movement", robot.hasFocusedObjectInApp())
    }

    @Test
    fun profileRapidMenuMoveAndImmediateRightEntersCurrentContent() = withHome {
        robot.ensureHomeTopBar()
        assumeTrue("Profile tab should be available", robot.focusTopTab("我的"))

        robot.press(KeyEvent.KEYCODE_DPAD_DOWN)
        robot.settle()
        assumeTrue(
            "Logged-in Profile sidebar is required for focus generation regression",
            robot.hasAnyText("历史记录", "我的收藏"),
        )

        robot.press(KeyEvent.KEYCODE_DPAD_DOWN)
        robot.press(KeyEvent.KEYCODE_DPAD_DOWN)
        robot.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        robot.settle(WaitMedium)

        assertTrue("Profile rapid movement must keep focus inside BBTTVV", robot.hasFocusedObjectInApp())
        assertFalse("Right should leave the Profile sidebar", robot.isFocusedOnProfileMenu())
    }

    @Test
    fun playerBackReturnsHome() = withHome {
        robot.ensureHomeTopBar()
        assumeTrue(robot.focusTopTab("推荐"))
        assumeTrue("Recommend card is required for player return regression", robot.focusHomeContent())

        robot.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assumeTrue("Detail page did not open from focused recommend card", robot.waitForAnyText("播放", "继续播放", "从头播放"))
        assumeTrue("Playable detail action was not available", robot.clickAnyText("播放", "继续播放", "从头播放"))

        robot.settle(WaitLong)
        robot.press(KeyEvent.KEYCODE_BACK)
        robot.settle(WaitMedium)
        robot.press(KeyEvent.KEYCODE_BACK)
        robot.waitForHomeTopBar()

        assertTrue(robot.hasHomeTopBar())
        assertTrue(robot.hasFocusedObjectInApp())
    }

    @Test
    fun cardMenuDismissRestoresCardFocus() = withHome {
        robot.ensureHomeTopBar()
        assumeTrue(robot.focusTopTab("推荐"))
        assumeTrue("Recommend card is required for context menu focus regression", robot.focusHomeContent())

        val before = robot.focusedLabel()
        assumeTrue("Focused card did not expose a long-click menu", robot.longClickFocusedObject())
        assumeTrue("Context menu did not open", robot.waitForAnyText("视频操作", "稍后再看", "不感兴趣"))

        robot.press(KeyEvent.KEYCODE_BACK)
        robot.waitUntilTextGone("视频操作")

        val after = robot.focusedLabel()
        assertTrue(
            "Expected context menu dismissal to restore card focus; before=$before after=$after",
            after == before || !robot.isFocusedOnTopTab(),
        )
    }

    private inline fun withHome(block: () -> Unit) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            robot.waitForApp()
            block()
        } finally {
            scenario.close()
        }
    }

    private companion object {
        private const val WaitMedium = 3_000L
        private const val WaitLong = 8_000L
    }
}

private class TvDpadRobot(
    private val device: UiDevice,
    private val targetPackage: String,
) {
    fun waitForApp() {
        device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), WaitLong)
        settle()
    }

    fun ensureHomeTopBar() {
        if (!hasHomeTopBar()) {
            press(KeyEvent.KEYCODE_BACK)
            waitForHomeTopBar()
        }
        assertTrue("Home top bar should be visible", hasHomeTopBar())
    }

    fun waitForHomeTopBar(): Boolean {
        device.wait(Until.hasObject(By.text("推荐")), WaitLong)
        settle()
        return hasHomeTopBar()
    }

    fun hasHomeTopBar(): Boolean {
        return hasText("推荐") && hasText("热门") && hasText("直播")
    }

    fun focusTopTab(label: String): Boolean {
        clickText(label)
        settle()
        if (focusedLabel() == label) return true

        repeat(4) {
            press(KeyEvent.KEYCODE_DPAD_UP)
            settle(WaitTiny)
            if (isFocusedOnTopTab()) return true
        }
        clickText(label)
        settle()
        return focusedLabel() == label || isFocusedOnTopTab()
    }

    fun focusHomeContent(): Boolean {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        settle(WaitMedium)
        return hasFocusedObjectInApp() && !isFocusedOnTopTab()
    }

    fun waitUntilFocusedOnTopTab(): Boolean {
        val deadline = System.currentTimeMillis() + WaitLong
        while (System.currentTimeMillis() < deadline) {
            if (isFocusedOnTopTab()) return true
            settle(WaitTiny)
        }
        return false
    }

    fun hasFocusedObjectInApp(): Boolean {
        val focused = focusedObject() ?: return false
        return focused.packageName == targetPackage
    }

    fun isFocusedOnTopTab(): Boolean {
        return focusedLabel() in TopTabLabels
    }

    fun focusedLabel(): String? {
        val focused = focusedObject() ?: return null
        return focused.text.takeIf { it.isNotBlank() }
            ?: focused.contentDescription.takeIf { it.isNotBlank() }
    }

    fun longClickFocusedObject(): Boolean {
        val focused = focusedObject() ?: return false
        return runCatching { focused.longClick() }.getOrDefault(false)
    }

    fun clickAnyText(vararg labels: String): Boolean {
        return labels.any(::clickText)
    }

    fun waitForAnyText(vararg labels: String): Boolean {
        val deadline = System.currentTimeMillis() + WaitLong
        while (System.currentTimeMillis() < deadline) {
            if (labels.any(::hasText)) return true
            settle(WaitTiny)
        }
        return false
    }

    fun hasAnyText(vararg labels: String): Boolean = labels.any(::hasText)

    fun isFocusedOnProfileMenu(): Boolean = focusedLabel() in ProfileMenuLabels

    fun waitUntilTextGone(label: String): Boolean {
        val deadline = System.currentTimeMillis() + WaitLong
        while (System.currentTimeMillis() < deadline) {
            if (!hasText(label)) return true
            settle(WaitTiny)
        }
        return false
    }

    fun press(keyCode: Int) {
        device.pressKeyCode(keyCode)
    }

    fun settle(timeoutMs: Long = WaitShort) {
        device.waitForIdle()
        Thread.sleep(timeoutMs)
    }

    private fun focusedObject(): UiObject? {
        return device.findObject(UiSelector().focused(true))
            .takeIf { focused -> focused.exists() }
    }

    private fun clickText(label: String): Boolean {
        val exact = device.findObject(UiSelector().text(label))
        if (exact.exists()) return exact.click()
        val partial = device.findObject(UiSelector().textContains(label))
        return partial.exists() && partial.click()
    }

    private fun hasText(label: String): Boolean {
        return device.hasObject(By.text(label)) || device.hasObject(By.textContains(label))
    }

    private companion object {
        private val TopTabLabels = setOf("搜索", "推荐", "推荐单", "热门", "直播", "动态", "稍后再看", "我的")
        private val ProfileMenuLabels = setOf(
            "历史记录",
            "我的收藏",
            "我的追番",
            "稍后再看",
            "切换账号",
            "更换图标",
            "设置",
            "弹幕设置",
            "插件中心",
            "操作说明",
            "登出",
        )
        private const val WaitTiny = 250L
        private const val WaitShort = 1_000L
        private const val WaitMedium = 3_000L
        private const val WaitLong = 8_000L
    }
}
