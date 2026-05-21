package com.bbttvv.app.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupToRecommendFirstScreen() = baselineProfileRule.collect(
        packageName = PackageName,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        waitForApp()
        settle()
    }

    @Test
    fun tvCoreJourneys() = baselineProfileRule.collect(
        packageName = PackageName,
    ) {
        pressHome()
        startActivityAndWait()
        waitForApp()
        settle()

        exerciseHomeTabs()
        exerciseHomeGridDpadScroll()
        exercisePlaybackFirstFrameAndReturn()
        exerciseSearch()
        exerciseProfileHistory()
    }

    private fun MacrobenchmarkScope.exerciseHomeTabs() {
        selectTopTab("推荐")
        listOf("热门", "直播", "动态", "搜索", "我的", "推荐").forEach { label ->
            selectTopTab(label)
            settle()
        }
    }

    private fun MacrobenchmarkScope.exerciseHomeGridDpadScroll() {
        selectTopTab("推荐")
        settle(WaitLong)
        focusContentFromTopBar()
        pressRepeated(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        pressRepeated(KeyEvent.KEYCODE_DPAD_DOWN, 6)
        pressRepeated(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        pressRepeated(KeyEvent.KEYCODE_DPAD_UP, 2)
        settle()

        selectTopTab("热门")
        settle(WaitLong)
        focusContentFromTopBar()
        pressRepeated(KeyEvent.KEYCODE_DPAD_DOWN, 4)
        pressRepeated(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        settle()

        selectTopTab("推荐")
        settle()
    }

    private fun MacrobenchmarkScope.exercisePlaybackFirstFrameAndReturn() {
        selectTopTab("推荐")
        settle(WaitLong)
        focusContentFromTopBar()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        pressCenter()
        settle(WaitLong)

        if (clickAnyText("播放", "继续播放", "从头播放")) {
            settle(WaitLong)
        } else {
            pressCenter()
            settle(WaitLong)
        }

        press(KeyEvent.KEYCODE_BACK)
        settle(WaitMedium)
        press(KeyEvent.KEYCODE_BACK)
        settle()
        ensureHomeVisible()
    }

    private fun MacrobenchmarkScope.exerciseSearch() {
        selectTopTab("搜索")
        settle()
        focusContentFromTopBar()
        device.executeShellCommand("input text BBTTVV")
        press(KeyEvent.KEYCODE_ENTER)
        settle(WaitLong)
        pressRepeated(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        settle()
        ensureHomeVisible()
    }

    private fun MacrobenchmarkScope.exerciseProfileHistory() {
        selectTopTab("我的")
        settle(WaitLong)
        clickAnyText("历史记录", "还没有历史记录", "登录后可查看")
        settle(WaitMedium)
    }

    private fun MacrobenchmarkScope.selectTopTab(label: String) {
        if (clickText(label)) return
        repeat(MaxTopTabMoves) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            settle(WaitTiny)
        }
        pressCenter()
    }

    private fun MacrobenchmarkScope.focusContentFromTopBar() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        settle(WaitTiny)
    }

    private fun MacrobenchmarkScope.ensureHomeVisible() {
        if (hasHomeTopBar()) return
        startActivityAndWait()
        waitForApp()
        settle()
    }

    private fun MacrobenchmarkScope.hasHomeTopBar(): Boolean {
        return hasText("热门") && hasText("直播") && hasText("动态")
    }

    private fun MacrobenchmarkScope.waitForApp() {
        device.wait(
            Until.hasObject(By.pkg(PackageName).depth(0)),
            WaitLong,
        )
    }

    private fun MacrobenchmarkScope.settle(timeoutMs: Long = WaitShort) {
        device.waitForIdle()
        Thread.sleep(timeoutMs)
    }

    private fun MacrobenchmarkScope.clickAnyText(vararg labels: String): Boolean {
        return labels.any { label -> clickText(label) }
    }

    private fun MacrobenchmarkScope.clickText(label: String): Boolean {
        val selector = By.text(label)
        val node = device.findObject(selector) ?: device.findObject(By.textContains(label))
        node?.let { runCatching { it.click() } }
        return node != null
    }

    private fun MacrobenchmarkScope.hasAnyText(vararg labels: String): Boolean {
        return labels.any { label -> hasText(label) }
    }

    private fun MacrobenchmarkScope.hasText(label: String): Boolean {
        return device.hasObject(By.text(label)) || device.hasObject(By.textContains(label))
    }

    private fun MacrobenchmarkScope.pressCenter() {
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun MacrobenchmarkScope.pressRepeated(
        keyCode: Int,
        count: Int,
        settleMs: Long = WaitTiny,
    ) {
        repeat(count) {
            press(keyCode)
            settle(settleMs)
        }
    }

    private fun MacrobenchmarkScope.press(keyCode: Int) {
        device.pressKeyCode(keyCode)
    }

    private companion object {
        private const val PackageName = "com.bbttvv.app"
        private const val WaitTiny = 250L
        private const val WaitShort = 1_000L
        private const val WaitMedium = 3_000L
        private const val WaitLong = 8_000L
        private const val MaxTopTabMoves = 8
    }
}
