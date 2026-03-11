package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.kyant.backdrop.backdrops.LayerBackdrop
import dev.chrisbanes.haze.HazeState

@Composable
internal fun HomeTopTabChrome(
    currentTabHeight: Dp,
    tabAlpha: Float,
    tabContentAlpha: Float,
    tabHorizontalPadding: Dp,
    tabVerticalPadding: Dp,
    isTabFloating: Boolean,
    effectiveTabShadowElevation: Dp,
    tabShape: Shape,
    tabChromeRenderMode: HomeTopChromeRenderMode,
    tabSurfaceColor: Color,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    liquidStyle: LiquidGlassStyle,
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    tabBorderAlpha: Float,
    tabHighlightColor: Color,
    tabContentUnderlayColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(-1f)
            .height(currentTabHeight)
            .graphicsLayer { alpha = tabAlpha * tabContentAlpha }
            .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tabHorizontalPadding, vertical = tabVerticalPadding)
                .then(
                    if (isTabFloating) {
                        Modifier.shadow(
                            elevation = effectiveTabShadowElevation,
                            shape = tabShape,
                            ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(tabShape)
                .homeTopChromeSurface(
                    renderMode = tabChromeRenderMode,
                    shape = tabShape,
                    surfaceColor = tabSurfaceColor,
                    hazeState = hazeState,
                    backdrop = backdrop,
                    liquidStyle = liquidStyle,
                    motionTier = motionTier,
                    isScrolling = isScrolling,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBlurBudget = forceLowBlurBudget
                )
                .then(
                    if (isTabFloating) {
                        Modifier.border(
                            width = 0.8.dp,
                            color = Color.White.copy(alpha = tabBorderAlpha),
                            shape = tabShape
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tabContentUnderlayColor)
            )
            if (isTabFloating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    tabHighlightColor,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            content()
        }
    }
}
