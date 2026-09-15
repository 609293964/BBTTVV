package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.feature.video.danmaku.DanmakuProto
import com.bbttvv.app.proto.dmview.CommandDm
import com.bbttvv.app.proto.dmview.DmWebViewReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuVoteStateTest {
    @Test
    fun `web view protobuf keeps command danmaku field nine`() {
        val bytes = DmWebViewReply.newBuilder()
            .addCommandDms(
                CommandDm.newBuilder()
                    .setId(2161695068414006784L)
                    .setOid(40198080299L)
                    .setCommand("#VOTE#")
                    .setContent("投票弹幕")
                    .setExtra(targetVoteExtra)
                    .setIdStr("2161695068414006784")
            )
            .build()
            .toByteArray()

        val parsed = DanmakuProto.parseWebViewReply(bytes)

        assertEquals(1, parsed.commandDms.size)
        assertEquals("#VOTE#", parsed.commandDms.single().command)
        assertEquals(targetVoteExtra, parsed.commandDms.single().extra)
    }

    @Test
    fun `target video vote payload becomes an immediately visible tv prompt`() {
        val prompts = parseDanmakuVotePrompts(
            listOf(
                DanmakuProto.CommandDm(
                    id = 2161695068414006784L,
                    oid = 40198080299L,
                    command = "#VOTE#",
                    progress = 0,
                    extra = targetVoteExtra,
                    idStr = "2161695068414006784",
                )
            )
        )

        val prompt = prompts.single()
        assertEquals(20511027L, prompt.voteId)
        assertEquals("测试：小梦捷德雷欧投1", prompt.question)
        assertEquals(0L, prompt.triggerPositionMs)
        assertEquals(7_000L, prompt.durationMs)
        assertEquals(listOf(1, 2), prompt.options.map { it.id })
        assertEquals(listOf("赛罗前后", "6-13名非赛罗"), prompt.options.map { it.text })
        assertFalse(prompt.options.first().hasSelfDefined)
    }

    @Test
    fun `non vote and malformed commands are ignored without affecting playback`() {
        val prompts = parseDanmakuVotePrompts(
            listOf(
                DanmakuProto.CommandDm(command = "#ATTENTION#", extra = targetVoteExtra),
                DanmakuProto.CommandDm(command = "#VOTE#", extra = "{bad-json"),
            )
        )

        assertTrue(prompts.isEmpty())
    }

    @Test
    fun `grade command uses author title and five star levels`() {
        val prompts = parseDanmakuVotePrompts(
            listOf(
                DanmakuProto.CommandDm(
                    id = 88L,
                    command = "#GRADE#",
                    extra = """{"grade_id":99,"title":"作者自定义标题","options":[],"cnt":49}""",
                )
            )
        )

        val prompt = prompts.single()
        assertEquals(DanmakuVoteKind.Grade, prompt.kind)
        assertEquals("作者自定义标题", prompt.question)
        assertEquals(49, prompt.participantCount)
        assertEquals(listOf("1", "2", "3", "4", "5"), prompt.options.map { it.text })
        assertEquals(listOf(2, 4, 6, 8, 10), prompt.options.map { it.gradeScore })
    }

    @Test
    fun `web grade fields preserve different author messages counts and previous scores`() {
        // Anonymous fixture with the field structure returned by dm web view.
        listOf("发布者甲的问题", "发布者乙的自定义问题").forEach { title ->
            listOf("#GRADE#", "GRADE_MSG", "VIDEO_GRADE_MSG").forEach { command ->
                val prompt = parseDanmakuVotePrompts(listOf(DanmakuProto.CommandDm(
                    command = command,
                    progress = 32_000,
                    extra = """{"grade_id":99,"msg":"$title","title":"旧标题","count":49,"mid_score":8,"duration":5000,"skin":3,"avg_score":9.5}""",
                ))).single()
                assertEquals(title, prompt.question)
                assertEquals(49, prompt.participantCount)
                assertEquals(4, prompt.selectedOptionId)
                assertEquals(32_000L, prompt.triggerPositionMs)
                assertEquals(5_000L, prompt.durationMs)
                assertEquals(listOf(2, 4, 6, 8, 10), prompt.options.map { it.gradeScore })
            }
        }
    }

    @Test
    fun `invalid grade data does not create a focusable prompt`() {
        listOf(
            """{"grade_id":0,"msg":"问题"}""",
            """{"grade_id":99,"msg":" "}""",
            "{malformed",
        ).forEach { extra ->
            assertTrue(parseDanmakuVotePrompts(listOf(
                DanmakuProto.CommandDm(command = "#GRADE#", extra = extra),
            )).isEmpty())
        }
    }

    @Test
    fun `prompt passed during resume remains eligible after seeking back into its window`() {
        val controller = DanmakuVoteController(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        controller.initialize(
            aid = 116963720301064L,
            cid = 40198080299L,
            commands = listOf(targetVoteCommand()),
        )

        controller.syncPlaybackPosition(130_000L)
        assertTrue(controller.uiState.value is DanmakuVoteUiState.Hidden)

        controller.syncPlaybackPosition(2_000L)
        assertTrue(controller.uiState.value is DanmakuVoteUiState.Showing)
        controller.cancel()
    }

    private fun targetVoteCommand() = DanmakuProto.CommandDm(
        id = 2161695068414006784L,
        oid = 40198080299L,
        command = "#VOTE#",
        progress = 0,
        extra = targetVoteExtra,
        idStr = "2161695068414006784",
    )

    private companion object {
        val targetVoteExtra = """
            {
              "vote_id": 20511027,
              "vote_type": 1,
              "question": "测试：小梦捷德雷欧投1",
              "cnt": 45,
              "options": [
                {"idx": 1, "desc": "赛罗前后", "cnt": 21, "has_self_def": false},
                {"idx": 2, "desc": "6-13名非赛罗", "cnt": 24, "has_self_def": false}
              ],
              "my_vote": 0,
              "duration": 7000,
              "show_status": 0
            }
        """.trimIndent()
    }
}
