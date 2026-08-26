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
