package com.bbttvv.app.data.model

/**
 * 评论反诈检测结果状态
 * 参考: https://github.com/freedom-introvert/biliSendCommAntifraud
 */
enum class CommentFraudStatus {
    /** 评论正常显示 */
    NORMAL,
    /** 仅自己可见 (ShadowBan) */
    SHADOW_BANNED,
    /** 被系统秒删 */
    DELETED,
    /** 疑似审核中 */
    UNDER_REVIEW,
    /** 检测失败 / 未知 */
    UNKNOWN
}

