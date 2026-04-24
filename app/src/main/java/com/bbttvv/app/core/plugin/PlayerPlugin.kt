// 文件路径: core/plugin/PlayerPlugin.kt
package com.bbttvv.app.core.plugin

/**
 *  播放器增强插件接口
 * 
 * 用于实现视频播放相关的增强功能，如：
 * - 自动跳过片段 (SponsorBlock)
 * - 自动跳过片头片尾
 * - 播放统计
 */
interface PlayerPlugin : Plugin {
    
    /**
     * 视频加载时回调
     * 用于加载该视频的相关数据（如跳过片段信息）
     * 
     * @param bvid 视频 BV 号
     * @param cid 视频 cid
     */
    suspend fun onVideoLoad(bvid: String, cid: Long)
    
    /**
     * 播放位置更新回调
     * 定期调用（约每 500ms），用于检测是否需要跳过
     * 
     * @param positionMs 当前播放位置（毫秒）
     * @return 跳过动作，返回 null 或 SkipAction.None 表示不跳过
     */
    suspend fun onPositionUpdate(positionMs: Long): SkipAction?

    /**
     * 用户显式 seek 时回调
     * 用于让插件在拖动进度条、点击章节等场景下同步内部状态
     */
    fun onUserSeek(positionMs: Long) {}
    
    /**
     * 视频播放结束时回调
     * 用于清理状态
     */
    fun onVideoEnd() {}
}

/**
 * 跳过动作定义
 */
sealed class SkipAction {
    /** 不执行跳过 */
    object None : SkipAction()
    
    /** 跳转到指定位置（自动跳过） */
    data class SkipTo(
        val positionMs: Long,
        val reason: String
    ) : SkipAction()
    
    /** 显示跳过按钮（手动跳过模式） */
    data class ShowButton(
        val skipToMs: Long,
        val label: String,           // 按钮文字，如"跳过广告"
        val segmentId: String        // 片段标识，用于防止重复显示
    ) : SkipAction()
}

