package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

// --- 0. 基础响应 (用于只返回 code 的 API) ---
@Serializable
data class BaseResponse(
    val code: Int = 0,
    val message: String = ""
)

// --- 1. 导航信息 (基本信息、等级、钱包、VIP、Wbi签名Key) ---
@Serializable
data class NavResponse(
    val code: Int = 0,
    val data: NavData? = null
)

@Serializable
data class NavData(
    val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val mid: Long = 0,
    val level_info: LevelInfo = LevelInfo(),
    val money: Double = 0.0, // 硬币
    val wallet: Wallet = Wallet(),
    val vip: VipInfo = VipInfo(),
    //  核心修复：补回 wbi_img 字段 
    val wbi_img: WbiImg? = null,
    //  [New] 顶部背景图 (API 返回)
    val top_photo: String = ""
)

@Serializable
data class WbiImg(
    val img_url: String = "",
    val sub_url: String = ""
)

@Serializable
data class LevelInfo(
    val current_level: Int = 0
)

@Serializable
data class Wallet(
    val bcoin_balance: Double = 0.0 // B币
)

@Serializable
data class VipInfo(
    val status: Int = 0, // 1: 有效
    val type: Int = 0,   // 1: 月度大会员, 2: 年度大会员
    val label: VipLabel = VipLabel()
)

@Serializable
data class VipLabel(
    val text: String = "" // "大会员", "年度大会员"
)

// --- 2. 统计信息 (关注、粉丝、动态) ---
@Serializable
data class NavStatResponse(
    val code: Int = 0,
    val data: NavStatData? = null
)

@Serializable
data class NavStatData(
    val following: Int = 0,      // 关注
    val follower: Int = 0,       // 粉丝
    val dynamic_count: Int = 0   // 动态
)
