package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class PortraitProgressBarLayoutPolicyTest {

    @Test
    fun compactPhone_usesDensePortraitSeekLayout() {
        val policy = resolvePortraitProgressBarLayoutPolicy(
            widthDp = 393
        )

        assertEquals(48, policy.touchAreaHeightDp)
        assertEquals(4, policy.idleTrackHeightDp)
        assertEquals(11, policy.draggingTrackHeightDp)
        assertEquals(12, policy.draggingThumbSizeDp)
        assertEquals(4, policy.trackCornerRadiusDp)
        assertEquals(18, policy.bubbleFontSp)
    }

    @Test
    fun mediumTablet_expandsSeekAreaForRemoteAndTouch() {
        val policy = resolvePortraitProgressBarLayoutPolicy(
            widthDp = 720
        )

        assertEquals(52, policy.touchAreaHeightDp)
        assertEquals(5, policy.idleTrackHeightDp)
        assertEquals(12, policy.draggingTrackHeightDp)
        assertEquals(13, policy.draggingThumbSizeDp)
        assertEquals(5, policy.trackCornerRadiusDp)
        assertEquals(19, policy.bubbleFontSp)
    }

    @Test
    fun tablet_expandsSeekHitAreaAndBubbleReadability() {
        val policy = resolvePortraitProgressBarLayoutPolicy(
            widthDp = 1024
        )

        assertEquals(56, policy.touchAreaHeightDp)
        assertEquals(5, policy.idleTrackHeightDp)
        assertEquals(13, policy.draggingTrackHeightDp)
        assertEquals(14, policy.draggingThumbSizeDp)
        assertEquals(5, policy.trackCornerRadiusDp)
        assertEquals(20, policy.bubbleFontSp)
    }

    @Test
    fun ultraWide_forcesLargestPortraitSeekScale() {
        val policy = resolvePortraitProgressBarLayoutPolicy(
            widthDp = 1920
        )

        assertEquals(64, policy.touchAreaHeightDp)
        assertEquals(6, policy.idleTrackHeightDp)
        assertEquals(15, policy.draggingTrackHeightDp)
        assertEquals(16, policy.draggingThumbSizeDp)
        assertEquals(6, policy.trackCornerRadiusDp)
        assertEquals(22, policy.bubbleFontSp)
    }
}
