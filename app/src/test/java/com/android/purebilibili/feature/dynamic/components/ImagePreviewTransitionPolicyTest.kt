package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePreviewTransitionPolicyTest {

    @Test
    fun resolveImagePreviewTransitionFrame_clampsVisualProgressButKeepsLayoutOvershoot() {
        val frame = resolveImagePreviewTransitionFrame(
            rawProgress = -0.2f,
            hasSourceRect = true,
            sourceCornerRadiusDp = 12f
        )

        assertEquals(-0.08f, frame.layoutProgress)
        assertEquals(0f, frame.visualProgress)
        assertEquals(12f, frame.cornerRadiusDp)
    }

    @Test
    fun resolveImagePreviewTransitionFrame_keepsCornerRadiusConstantDuringTransition() {
        val frame = resolveImagePreviewTransitionFrame(
            rawProgress = 0.5f,
            hasSourceRect = true,
            sourceCornerRadiusDp = 12f
        )

        assertEquals(12f, frame.cornerRadiusDp)
    }

    @Test
    fun resolveImagePreviewTransitionFrame_usesZeroCornerWhenNoSourceRect() {
        val frame = resolveImagePreviewTransitionFrame(
            rawProgress = 0.5f,
            hasSourceRect = false,
            sourceCornerRadiusDp = 12f
        )

        assertEquals(0f, frame.cornerRadiusDp)
    }

    @Test
    fun imagePreviewDismissMotion_returnsOvershootThenSettleTargets() {
        val motion = imagePreviewDismissMotion()

        assertEquals(-0.06f, motion.overshootTarget)
        assertEquals(0f, motion.settleTarget)
    }

    @Test
    fun resolvePredictiveBackAnimationProgress_isInverseOfGestureProgress() {
        assertEquals(1f, resolvePredictiveBackAnimationProgress(0f))
        assertEquals(0.5f, resolvePredictiveBackAnimationProgress(0.5f))
        assertEquals(0f, resolvePredictiveBackAnimationProgress(1f))
    }

    @Test
    fun resolvePredictiveBackAnimationProgress_clampsOutOfRangeInput() {
        assertEquals(1f, resolvePredictiveBackAnimationProgress(-0.3f))
        assertEquals(0f, resolvePredictiveBackAnimationProgress(1.6f))
    }

    @Test
    fun resolveImagePreviewDismissTransform_returnsIdentityWithoutSourceRect() {
        val transform = resolveImagePreviewDismissTransform(
            transitionProgress = 0.3f,
            sourceRect = null,
            displayedImageRect = null
        )

        assertEquals(1f, transform.scale)
        assertEquals(0f, transform.translationXPx)
        assertEquals(0f, transform.translationYPx)
    }

    @Test
    fun resolveImagePreviewDismissTransform_usesUniformScaleAndCenterTranslation() {
        val source = Rect(
            left = 100f,
            top = 400f,
            right = 300f,
            bottom = 500f
        )
        val start = resolveImagePreviewDismissTransform(
            transitionProgress = 1f,
            sourceRect = source,
            displayedImageRect = Rect(
                left = 0f,
                top = 660f,
                right = 1080f,
                bottom = 1260f
            )
        )
        val middle = resolveImagePreviewDismissTransform(
            transitionProgress = 0.5f,
            sourceRect = source,
            displayedImageRect = Rect(
                left = 0f,
                top = 660f,
                right = 1080f,
                bottom = 1260f
            )
        )
        val end = resolveImagePreviewDismissTransform(
            transitionProgress = 0f,
            sourceRect = source,
            displayedImageRect = Rect(
                left = 0f,
                top = 660f,
                right = 1080f,
                bottom = 1260f
            )
        )
        val overshoot = resolveImagePreviewDismissTransform(
            transitionProgress = -0.06f,
            sourceRect = source,
            displayedImageRect = Rect(
                left = 0f,
                top = 660f,
                right = 1080f,
                bottom = 1260f
            )
        )

        assertEquals(1f, start.scale, 0.0001f)
        assertEquals(0f, start.translationXPx, 0.0001f)
        assertEquals(0f, start.translationYPx, 0.0001f)

        assertTrue(middle.scale in 0.7f..0.85f)
        assertTrue(middle.translationXPx < 0f && middle.translationXPx > -340f)
        assertTrue(middle.translationYPx < 0f && middle.translationYPx > -535f)

        assertEquals(0.16666669f, end.scale, 0.0001f)
        assertEquals(-340f, end.translationXPx, 0.0001f)
        assertEquals(-510f, end.translationYPx, 0.0001f)

        assertTrue(overshoot.translationXPx < end.translationXPx)
        assertTrue(overshoot.translationYPx < end.translationYPx)
        assertTrue(overshoot.scale in 0.01f..end.scale)
    }

    @Test
    fun resolveImagePreviewDismissRectFrame_startsFromDisplayedRect() {
        val displayed = Rect(
            left = 180f,
            top = 220f,
            right = 900f,
            bottom = 1500f
        )
        val source = Rect(
            left = 40f,
            top = 80f,
            right = 280f,
            bottom = 440f
        )

        val frame = resolveImagePreviewDismissRectFrame(
            transitionProgress = 1f,
            sourceRect = source,
            displayedImageRect = displayed
        ) ?: error("Expected start frame")

        assertEquals(displayed.left, frame.rect.left, 0.0001f)
        assertEquals(displayed.top, frame.rect.top, 0.0001f)
        assertEquals(displayed.right, frame.rect.right, 0.0001f)
        assertEquals(displayed.bottom, frame.rect.bottom, 0.0001f)
    }

    @Test
    fun resolveImagePreviewDismissRectFrame_endsAtSourceRect() {
        val displayed = Rect(
            left = 180f,
            top = 220f,
            right = 900f,
            bottom = 1500f
        )
        val source = Rect(
            left = 40f,
            top = 80f,
            right = 280f,
            bottom = 440f
        )

        val frame = resolveImagePreviewDismissRectFrame(
            transitionProgress = 0f,
            sourceRect = source,
            displayedImageRect = displayed
        ) ?: error("Expected end frame")

        assertEquals(source.left, frame.rect.left, 0.0001f)
        assertEquals(source.top, frame.rect.top, 0.0001f)
        assertEquals(source.right, frame.rect.right, 0.0001f)
        assertEquals(source.bottom, frame.rect.bottom, 0.0001f)
    }

    @Test
    fun resolveImagePreviewDismissRectFrame_interpolatesWidthHeightAndCenter() {
        val displayed = Rect(
            left = 180f,
            top = 220f,
            right = 900f,
            bottom = 1500f
        )
        val source = Rect(
            left = 40f,
            top = 80f,
            right = 280f,
            bottom = 440f
        )

        val frame = resolveImagePreviewDismissRectFrame(
            transitionProgress = 0.5f,
            sourceRect = source,
            displayedImageRect = displayed
        )

        val rect = frame?.rect ?: error("Expected rect frame")
        assertTrue(rect.width < displayed.width && rect.width > source.width)
        assertTrue(rect.height < displayed.height && rect.height > source.height)
        val displayedCenterX = (displayed.left + displayed.right) / 2f
        val sourceCenterX = (source.left + source.right) / 2f
        val currentCenterX = (rect.left + rect.right) / 2f
        assertTrue(currentCenterX < displayedCenterX && currentCenterX > sourceCenterX)
    }

    @Test
    fun resolveImagePreviewDismissStartRect_prefersPreviewSurfaceForChromeDismiss() {
        val previewSurface = Rect(
            left = 0f,
            top = 0f,
            right = 1080f,
            bottom = 2400f
        )
        val displayed = Rect(
            left = 120f,
            top = 320f,
            right = 960f,
            bottom = 1760f
        )

        val startRect = resolveImagePreviewDismissStartRect(
            previewSurfaceRect = previewSurface,
            displayedImageRect = displayed,
            preferPreviewSurface = true
        )

        assertEquals(previewSurface, startRect)
    }

    @Test
    fun resolveImagePreviewDismissStartRect_fallsBackToDisplayedImageWhenPreviewSurfaceMissing() {
        val displayed = Rect(
            left = 120f,
            top = 320f,
            right = 960f,
            bottom = 1760f
        )

        val startRect = resolveImagePreviewDismissStartRect(
            previewSurfaceRect = null,
            displayedImageRect = displayed,
            preferPreviewSurface = true
        )

        assertEquals(displayed, startRect)
    }

    @Test
    fun resolveImagePreviewDraggedDisplayRect_appliesTranslationAndScaleAroundCenter() {
        val displayed = Rect(
            left = 180f,
            top = 220f,
            right = 900f,
            bottom = 1500f
        )

        val dragged = resolveImagePreviewDraggedDisplayRect(
            displayedImageRect = displayed,
            translationYPx = 240f,
            scale = 0.9f
        ) ?: error("Expected dragged rect")

        assertEquals(displayed.width * 0.9f, dragged.width, 0.0001f)
        assertEquals(displayed.height * 0.9f, dragged.height, 0.0001f)
        val draggedCenterY = (dragged.top + dragged.bottom) / 2f
        val displayedCenterY = (displayed.top + displayed.bottom) / 2f
        assertEquals(displayedCenterY + 240f, draggedCenterY, 0.0001f)
    }

    @Test
    fun resolveImagePreviewDismissBackdropAlpha_keepsBackdropLongerThenFades() {
        val start = resolveImagePreviewDismissBackdropAlpha(1f)
        val middle = resolveImagePreviewDismissBackdropAlpha(0.5f)
        val end = resolveImagePreviewDismissBackdropAlpha(0f)

        assertEquals(1f, start, 0.0001f)
        assertEquals(0.7320428f, middle, 0.0001f)
        assertEquals(0f, end, 0.0001f)
    }

    @Test
    fun resolveImagePreviewText_usesPerImageCaptionWhenAvailable() {
        val resolved = resolveImagePreviewText(
            textContent = ImagePreviewTextContent(
                headline = "作者A",
                body = "这是正文",
                perImageCaptions = listOf("图1文案", "图2文案")
            ),
            currentPage = 1,
            totalPages = 3
        )

        assertEquals("作者A", resolved?.headline)
        assertEquals("图2文案", resolved?.body)
        assertEquals("2 / 3", resolved?.pageIndicator)
    }

    @Test
    fun resolveImagePreviewText_returnsNullWhenEverythingIsBlank() {
        val resolved = resolveImagePreviewText(
            textContent = ImagePreviewTextContent(),
            currentPage = 0,
            totalPages = 1
        )

        assertNull(resolved)
    }

    @Test
    fun resolveImagePreviewTextTransform_adds3dFeelDuringPagerSwipe() {
        val centered = resolveImagePreviewTextTransform(0f)
        val swiping = resolveImagePreviewTextTransform(0.55f)

        assertEquals(0f, centered.rotationX, 0.0001f)
        assertEquals(1f, centered.alpha, 0.0001f)
        assertTrue(swiping.rotationX > 10f)
        assertTrue(swiping.alpha < 1f)
        assertTrue(swiping.translateYDp > 0f)
    }
}
