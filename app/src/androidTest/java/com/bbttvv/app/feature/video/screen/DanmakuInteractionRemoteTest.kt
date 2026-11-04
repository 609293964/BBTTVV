package com.bbttvv.app.feature.video.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bbttvv.app.MainActivity
import com.bbttvv.app.feature.video.danmaku.DanmakuProto
import com.bbttvv.app.feature.video.viewmodel.DanmakuVoteController
import com.bbttvv.app.feature.video.viewmodel.DanmakuVoteUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DanmakuInteractionRemoteTest {
    @Test
    fun timedCommandsShowAtAuthorPositionAndAcceptRemoteInput() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val coordinator = PlayerFocusCoordinator()
        var handler: DanmakuVoteKeyHandler? = null
        lateinit var controller: DanmakuVoteController
        val submissions = mutableListOf<Int>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                controller = DanmakuVoteController(activity.lifecycleScope, submitVote = {
                    submissions += it.option.id
                    Result.success(Unit)
                })
                activity.setContent {
                    val state = controller.uiState.collectAsState().value
                    BackHandler(enabled = state !is DanmakuVoteUiState.Hidden, onBack = controller::hide)
                    Box(Modifier.fillMaxSize().onPreviewKeyEvent { handler?.invoke(it.nativeKeyEvent) ?: false }) {
                        DanmakuVoteOverlay(state, 16f / 9f, coordinator, controller::selectOption,
                            controller::retry, controller::hide, { handler = it })
                    }
                }
                controller.initialize(1, 2, listOf(
                    DanmakuProto.CommandDm(id = 1, command = "#GRADE#", progress = 1000,
                        extra = """{"grade_id":1,"msg":"给这段打几星？","posX_2":10,"posY_2":20,"duration":30000}"""),
                    DanmakuProto.CommandDm(id = 2, command = "#VOTE#", progress = 40000,
                        extra = """{"vote_id":2,"question":"选哪一个？","options":["甲","乙"],"posX_2":90,"posY_2":80,"duration":30000}"""),
                    DanmakuProto.CommandDm(id = 3, command = "#GRADE#", progress = 80000,
                        extra = """{"grade_id":3,"msg":"限时打分","duration":3000}"""),
                ))
                controller.syncPlaybackPosition(1000)
            }
            val grade = device.wait(Until.findObject(By.text("给这段打几星？")), 5000)
            assertTrue(grade.visibleCenter.x < device.displayWidth / 2)
            device.pressDPadDown()
            device.pressDPadCenter()
            scenario.onActivity { assertEquals(listOf(2), submissions) }
            assertTrue(device.wait(Until.gone(By.text("给这段打几星？")), 5000))
            scenario.onActivity { controller.syncPlaybackPosition(40000) }
            val vote = device.wait(Until.findObject(By.text("选哪一个？")), 5000)
            assertTrue(vote.visibleCenter.x > device.displayWidth / 2)
            device.pressDPadDown()
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.text("选哪一个？")), 5000))
            scenario.onActivity {
                assertEquals(DanmakuVoteUiState.Hidden, controller.uiState.value)
                controller.syncPlaybackPosition(80000)
            }
            assertTrue(device.wait(Until.hasObject(By.text("限时打分")), 2000))
            assertTrue(device.wait(Until.gone(By.text("限时打分")), 5000))
            scenario.onActivity {
                assertEquals(DanmakuVoteUiState.Hidden, controller.uiState.value)
                assertEquals(listOf(2), submissions)
            }
        }
    }
}
