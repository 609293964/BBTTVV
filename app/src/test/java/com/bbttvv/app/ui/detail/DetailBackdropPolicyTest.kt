package com.bbttvv.app.ui.detail

import org.junit.Assert.assertTrue
import org.junit.Test

class DetailBackdropPolicyTest {
    @Test
    fun `text side of cover backdrop has strong contrast protection`() {
        assertTrue(DetailBackdropGradientColors.first().alpha >= 0.90f)
        assertTrue(DetailLightBackdropGradientColors.first().alpha >= 0.90f)
    }

    @Test
    fun `bottom content area fades close to the page surface`() {
        assertTrue(DetailBackdropBottomGradientColors.last().alpha >= 0.90f)
        assertTrue(DetailLightBackdropBottomGradientColors.last().alpha >= 0.90f)
    }
}
