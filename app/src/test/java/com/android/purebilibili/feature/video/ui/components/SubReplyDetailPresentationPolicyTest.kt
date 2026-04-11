package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubReplyDetailPresentationPolicyTest {

    @Test
    fun `section title should include current reply count`() {
        assertEquals("相关回复共14条", resolveSubReplyDetailSectionTitle(replyCount = 14))
    }

    @Test
    fun `conversation section title should include filtered reply count`() {
        assertEquals("对话共2条", resolveSubReplyConversationSectionTitle(replyCount = 2))
    }

    @Test
    fun `conversation action should only show for directed reply text`() {
        assertTrue(
            shouldShowSubReplyConversationAction(
                buildReply(message = "回复 @前进四放映室：没错")
            )
        )
        assertFalse(
            shouldShowSubReplyConversationAction(
                buildReply(message = "又又又又更新？？？？")
            )
        )
    }

    @Test
    fun `conversation action should not reuse reply composer when handler is missing`() {
        val directedReply = buildReply(message = "回复 @前进四放映室：没错")

        assertFalse(
            shouldRenderSubReplyConversationAction(
                item = directedReply,
                hasConversationHandler = false
            )
        )
        assertTrue(
            shouldRenderSubReplyConversationAction(
                item = directedReply,
                hasConversationHandler = true
            )
        )
    }

    @Test
    fun `conversation items should filter by dialog id`() {
        val first = buildReply(
            rpid = 10,
            message = "回复 @甲：第一条",
            dialog = 100
        )
        val second = buildReply(
            rpid = 11,
            message = "回复 @乙：第二条",
            dialog = 100
        )
        val other = buildReply(
            rpid = 12,
            message = "回复 @丙：无关",
            dialog = 200
        )

        assertEquals(
            listOf(10L, 11L),
            resolveSubReplyConversationItems(
                anchorReply = first,
                subReplies = listOf(first, second, other)
            ).map { it.rpid }
        )
    }

    @Test
    fun `conversation items should fallback to clicked reply when dialog is unavailable`() {
        val clicked = buildReply(
            rpid = 10,
            message = "回复 @甲：第一条"
        )

        assertEquals(
            listOf(10L),
            resolveSubReplyConversationItems(
                anchorReply = clicked,
                subReplies = emptyList()
            ).map { it.rpid }
        )
    }

    @Test
    fun `auxiliary label should prefer garb card number when available`() {
        assertEquals(
            "NO.013992",
            resolveSubReplyAuxiliaryLabel(
                item = buildReply(
                    message = "test",
                    garbCardNumber = "13992"
                )
            )
        )
    }

    @Test
    fun `auxiliary label should stay hidden when no garb card number exists`() {
        assertEquals(
            null,
            resolveSubReplyAuxiliaryLabel(
                item = buildReply(message = "test")
            )
        )
    }

    @Test
    fun `light theme detail appearance should follow theme surface instead of dark palette`() {
        val appearance = resolveSubReplyDetailAppearance(
            surfaceColor = Color(0xFFFFFFFF),
            surfaceVariantColor = Color(0xFFF1F2F4),
            surfaceContainerHighColor = Color(0xFFE8EAF0),
            outlineVariantColor = Color(0xFFD9DCE3),
            onSurfaceColor = Color(0xFF1B1C1F),
            onSurfaceVariantColor = Color(0xFF6A6F76),
            primaryColor = Color(0xFFFB7299),
            onPrimaryColor = Color(0xFFFFFFFF)
        )

        assertEquals(Color(0xFFFFFFFF), appearance.panelColor)
        assertEquals(Color(0xFF1B1C1F), appearance.primaryTextColor)
        assertEquals(Color(0xFF6A6F76), appearance.secondaryTextColor)
        assertEquals(Color(0xFFD9DCE3), appearance.dividerColor)
        assertEquals(Color(0xFFE8EAF0), appearance.sectionDividerColor)
        assertEquals(Color(0xFFFB7299), appearance.accentColor)
    }

    @Test
    fun `dark theme detail appearance should follow active theme colors`() {
        val appearance = resolveSubReplyDetailAppearance(
            surfaceColor = Color(0xFF141414),
            surfaceVariantColor = Color(0xFF242424),
            surfaceContainerHighColor = Color(0xFF1E1E1E),
            outlineVariantColor = Color(0xFF333333),
            onSurfaceColor = Color(0xFFF5F5F5),
            onSurfaceVariantColor = Color(0xFFD0D0D0),
            primaryColor = Color(0xFFFB7299),
            onPrimaryColor = Color(0xFF101010)
        )

        assertEquals(Color(0xFF141414), appearance.panelColor)
        assertEquals(Color(0xFFF5F5F5), appearance.primaryTextColor)
        assertEquals(Color(0xFFD0D0D0), appearance.secondaryTextColor)
        assertEquals(Color(0xFF333333), appearance.dividerColor)
        assertEquals(Color(0xFF242424), appearance.placeholderColor)
    }

    private fun buildReply(
        rpid: Long = 200L,
        message: String,
        garbCardNumber: String = "",
        parent: Long = 0L,
        dialog: Long = 0L
    ): ReplyItem {
        return ReplyItem(
            rpid = rpid,
            parent = parent,
            dialog = dialog,
            member = ReplyMember(
                mid = "12",
                uname = "ReplyUser",
                garbCardNumber = garbCardNumber
            ),
            content = ReplyContent(message = message)
        )
    }
}
