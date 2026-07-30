package com.bbttvv.app.ui.components

import android.view.KeyEvent
import com.bbttvv.app.data.model.response.ReplyPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentPicturePolicyTest {
    @Test
    fun `picture mapping drops blank urls and normalizes protocol and ratio`() {
        val pictures = listOf(
            ReplyPicture(imgSrc = "  "),
            ReplyPicture(imgSrc = "//i0.hdslb.com/a.jpg", imgWidth = 800, imgHeight = 400),
            ReplyPicture(imgSrc = "http://i0.hdslb.com/b.jpg", imgWidth = 0, imgHeight = 0),
        ).toCommentPictureUiModels()

        assertEquals(2, pictures.size)
        assertEquals("https://i0.hdslb.com/a.jpg", pictures[0].url)
        assertEquals(2f, pictures[0].aspectRatio)
        assertEquals("https://i0.hdslb.com/b.jpg", pictures[1].url)
        assertEquals(16f / 9f, pictures[1].aspectRatio)
    }

    @Test
    fun `thumbnail and viewer requests preserve ratio within limits`() {
        assertEquals(
            CommentPictureRequestSize(widthPx = 480, heightPx = 240),
            resolveCommentPictureThumbnailSize(aspectRatio = 2f),
        )
        assertEquals(
            CommentPictureRequestSize(widthPx = 540, heightPx = 1080),
            resolveCommentPictureViewerSize(
                aspectRatio = 0.5f,
                viewportWidthPx = 3840,
                viewportHeightPx = 2160,
            ),
        )
        assertEquals(
            CommentPictureRequestSize(widthPx = 1280, heightPx = 720),
            resolveCommentPictureViewerSize(
                aspectRatio = 16f / 9f,
                viewportWidthPx = 1280,
                viewportHeightPx = 720,
            ),
        )
    }

    @Test
    fun `long press opens once and suppresses following key up`() {
        val initial = CommentPictureConfirmState()
        val firstRepeat = resolveCommentPictureConfirmEvent(
            state = initial,
            isKeyDown = true,
            repeatCount = 1,
            hasPictures = true,
        )
        val secondRepeat = resolveCommentPictureConfirmEvent(
            state = firstRepeat.state,
            isKeyDown = true,
            repeatCount = 2,
            hasPictures = true,
        )
        val keyUp = resolveCommentPictureConfirmEvent(
            state = secondRepeat.state,
            isKeyDown = false,
            repeatCount = 0,
            hasPictures = true,
        )

        assertTrue(firstRepeat.openPictures)
        assertTrue(firstRepeat.consumed)
        assertFalse(secondRepeat.openPictures)
        assertTrue(secondRepeat.consumed)
        assertFalse(keyUp.openPictures)
        assertTrue(keyUp.consumed)
        assertFalse(keyUp.state.longPressHandled)
    }

    @Test
    fun `short confirm remains available to card click`() {
        val down = resolveCommentPictureConfirmEvent(
            state = CommentPictureConfirmState(),
            isKeyDown = true,
            repeatCount = 0,
            hasPictures = true,
        )
        val up = resolveCommentPictureConfirmEvent(
            state = down.state,
            isKeyDown = false,
            repeatCount = 0,
            hasPictures = true,
        )

        assertFalse(down.consumed)
        assertFalse(up.consumed)
    }

    @Test
    fun `short confirm prefers replies while long press remains reserved for pictures`() {
        assertEquals(
            CommentCardShortConfirmAction.OpenReplies,
            resolveCommentCardShortConfirmAction(
                hasReplies = true,
                hasPictures = true,
                replyNavigationEnabled = true,
            ),
        )
        assertEquals(
            CommentCardShortConfirmAction.OpenPictures,
            resolveCommentCardShortConfirmAction(
                hasReplies = false,
                hasPictures = true,
                replyNavigationEnabled = true,
            ),
        )
        assertEquals(
            CommentCardShortConfirmAction.OpenReplies,
            resolveCommentCardShortConfirmAction(
                hasReplies = true,
                hasPictures = false,
                replyNavigationEnabled = true,
            ),
        )
    }

    @Test
    fun `viewer navigation stops at boundaries and preserves source key`() {
        val state = createCommentImageViewerState(
            pictures = listOf(
                CommentPictureUiModel("https://example.com/1.jpg", 1f),
                CommentPictureUiModel("https://example.com/2.jpg", 1f),
            ),
            sourceKey = "comment:42",
            suppressInitialConfirmKeyUp = true,
        )

        assertNotNull(state)
        assertEquals(0, state!!.move(-1).currentIndex)
        assertEquals(1, state.move(1).move(1).currentIndex)
        assertEquals("comment:42", state.move(1).sourceKey)
        assertTrue(state.suppressInitialConfirmKeyUp)
    }

    @Test
    fun `viewer consumes boundaries and confirm closes after opening key up is suppressed`() {
        val left = resolveCommentImageViewerKeyEvent(
            keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            isKeyDown = true,
            suppressInitialConfirmKeyUp = true,
        )
        val suppressedConfirmUp = resolveCommentImageViewerKeyEvent(
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            isKeyDown = false,
            suppressInitialConfirmKeyUp = left.suppressInitialConfirmKeyUp,
        )
        val closingConfirmUp = resolveCommentImageViewerKeyEvent(
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            isKeyDown = false,
            suppressInitialConfirmKeyUp = suppressedConfirmUp.suppressInitialConfirmKeyUp,
        )

        assertEquals(CommentImageViewerKeyAction.Previous, left.action)
        assertTrue(left.consumed)
        assertEquals(CommentImageViewerKeyAction.None, suppressedConfirmUp.action)
        assertFalse(suppressedConfirmUp.suppressInitialConfirmKeyUp)
        assertEquals(CommentImageViewerKeyAction.Dismiss, closingConfirmUp.action)
        assertTrue(closingConfirmUp.consumed)
    }
}
