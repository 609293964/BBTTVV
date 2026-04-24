package com.bbttvv.app.core.theme

import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect

/**
 * 统一物理动画参数
 * 旨在模拟 iOS 系统级别的灵动交互手感
 */
object AnimationSpecs {

    /**
     * iOS 风格的空间弹簧参数 (Spatial Spring)
     * 用于共享元素过渡、卡片展开/收起等空间变换
     *
     * 特点：
     * - 高刚度 (Stiffness 380f): 响应迅速，跟手感强
     * - 阻尼 (Damping 0.82f): 带有微小的过冲 (Overshoot)，富有弹性但不过分晃动
     * - 基于真正的物理弹簧模型，而非固定时长 tween
     */
    val BiliPaiSpringSpec = spring<Rect>(
        dampingRatio = 0.82f,
        stiffness = 380f
    )
}

