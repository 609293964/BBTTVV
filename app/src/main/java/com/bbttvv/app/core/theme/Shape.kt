// 文件路径: core/theme/Shape.kt
package com.bbttvv.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 *  iOS 风格圆角规范
 * 
 * 基于 Apple Human Interface Guidelines:
 * - iOS 使用连续曲率圆角 (continuous corners)
 * - 不同组件有不同的圆角半径规范
 * 
 * 参考值：
 * - 小按钮/标签: 4-6dp
 * - 输入框/小卡片: 8-10dp  
 * - 普通卡片: 12dp
 * - 模态弹窗: 14dp
 * - 大卡片: 16-20dp
 * - 悬浮元素: 24-28dp
 * - 胶囊按钮: 完全圆角
 */
object iOSCornerRadius {
    /** 微小圆角 - 标签、小徽章 */
    val Tiny = 4.dp
    
    /** 超小圆角 - 小按钮、Chip */
    val ExtraSmall = 6.dp
    
    /** 小圆角 - 输入框、小卡片 */
    val Small = 10.dp
    
    /** 中等圆角 - 普通卡片、按钮 */
    val Medium = 12.dp
    
    /** 大圆角 - 模态弹窗、ActionSheet */
    val Large = 14.dp
    
    /** 超大圆角 - 大卡片 */
    val ExtraLarge = 20.dp
    
    /** 悬浮圆角 - 悬浮底栏、浮动按钮 */
    val Floating = 28.dp
    
    /** 胶囊圆角 - 胶囊按钮、Pill */
    val Full = 100.dp
}
    




/**
 *  CompositionLocal 提供圆角缩放比例
 */
val LocalCornerRadiusScale = staticCompositionLocalOf { 1f }



/**
 *  iOS 风格预设 Shape
 * 
 * 使用默认 iOS 圆角值
 */
val iOSShapes = Shapes(
    extraSmall = RoundedCornerShape(iOSCornerRadius.Tiny),
    small = RoundedCornerShape(iOSCornerRadius.Small),
    medium = RoundedCornerShape(iOSCornerRadius.Medium),
    large = RoundedCornerShape(iOSCornerRadius.Large),
    extraLarge = RoundedCornerShape(iOSCornerRadius.ExtraLarge)
)

/**
 *  常用圆角形状快捷访问
 */
object iOSShapeTokens {
    /** 小标签形状 */
    val TagShape = RoundedCornerShape(iOSCornerRadius.Tiny)
    
    /** 小按钮/Chip 形状 */
    val ChipShape = RoundedCornerShape(iOSCornerRadius.ExtraSmall)
    
    /** 视频卡片封面形状 */
    val CardCoverShape = RoundedCornerShape(iOSCornerRadius.Small)
    
    /** 普通卡片形状 */
    val CardShape = RoundedCornerShape(iOSCornerRadius.Medium)
    
    /** 对话框形状 */
    val DialogShape = RoundedCornerShape(iOSCornerRadius.Large)
    
    /** 悬浮底栏形状 */
    val FloatingBarShape = RoundedCornerShape(iOSCornerRadius.Floating)
    
    /** 胶囊按钮形状 */
    val PillShape = RoundedCornerShape(iOSCornerRadius.Full)
    
    /** 搜索框形状 */
    val SearchBarShape = RoundedCornerShape(iOSCornerRadius.Small)
    
    /** 头像形状 (圆形) */
    val AvatarShape = RoundedCornerShape(iOSCornerRadius.Full)
}

