package com.bbttvv.app.ui.home

import android.view.KeyEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DpadGridControllerInstrumentationTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun rapidVerticalKeysKeepOnlyTheLatestDirectionalTarget() {
        val scenario = ActivityScenario.launch(DpadGridTestActivity::class.java)
        try {
            waitForFocusedPosition(scenario, expectedPosition = 0)

            repeat(6) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN) }
            waitForFocusedPosition(scenario, expectedPosition = 24)

            repeat(3) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP) }
            waitForFocusedPosition(scenario, expectedPosition = 12)
        } finally {
            scenario.close()
        }
    }

    private fun waitForFocusedPosition(
        scenario: ActivityScenario<DpadGridTestActivity>,
        expectedPosition: Int,
    ) {
        val deadline = System.currentTimeMillis() + FocusTimeoutMs
        var actualPosition = RecyclerView.NO_POSITION
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                actualPosition = activity.focusedAdapterPosition()
            }
            if (actualPosition == expectedPosition) return
            Thread.sleep(FocusPollMs)
        }
        assertEquals(expectedPosition, actualPosition)
    }

    private companion object {
        private const val FocusTimeoutMs = 5_000L
        private const val FocusPollMs = 50L
    }
}
