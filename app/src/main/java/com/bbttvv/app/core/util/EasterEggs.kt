//  BiliPai 彩蛋系统
// 增添应用趣味性的小惊喜
package com.bbttvv.app.core.util

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/**
 *  彩蛋管理器
 * 提供各种有趣的互动彩蛋
 */
object EasterEggs {
    const val VERSION_EASTER_EGG_THRESHOLD = 7
    
    // ═══════════════════════════════════════════════════
    // 🎉 下拉刷新趣味提示语
    // ═══════════════════════════════════════════════════
    private val refreshMessages = listOf(
        "刷到好内容了～ 🎉",
        "新鲜出炉！热乎的～ ",
        "加载完成，请笑纳 ✨",
        "嗖～新内容已就位！",
        "你的手气不错哦～ 🍀",
        "内容已更新，快去看看吧！",
        "刷新成功，冲冲冲！💪",
        "叮～新视频到货啦！",
        "已刷新，今天也要开心！😊",
        "内容加载完毕，请享用～"
    )
    
    //  深夜彩蛋 (23:00 - 05:00)
    private val lateNightMessages = listOf(
        "夜深了，注意休息哦～ ",
        "熬夜冠军就是你！🏆",
        "月亮都睡了，你还在刷？",
        "深夜食堂营业中... 🍜",
        "你很努力，但也要早点睡觉",
        "黑眼圈在向你招手了～ 👀"
    )
    
    // 🌅 清晨彩蛋 (05:00 - 08:00)
    private val earlyMorningMessages = listOf(
        "早起的鸟儿有虫吃！🐦",
        "清晨好！今天也元气满满！☀️",
        "起得真早！你超棒的！",
        "新的一天，新的开始～ 🌄"
    )
    
    // 🎂 特殊日期彩蛋
    private val specialDayMessages = mapOf(
        "01-01" to "新年快乐！🎊 愿新的一年万事如意！",
        "02-14" to "情人节快乐！💕",
        "05-01" to "劳动节快乐！💪 今天不劳动～",
        "05-04" to "青年节快乐！年轻无极限！",
        "06-01" to "儿童节快乐！🎈 谁还不是个宝宝呢？",
        "10-01" to "国庆节快乐！🇨🇳",
        "12-25" to "圣诞快乐！🎄🎅",
        "12-31" to "最后一天了，辛苦你啦！"
    )
    
    /**
     * 获取刷新后的趣味提示
     * 根据时间、日期等返回不同的提示语
     */
    fun getRefreshMessage(): String {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val monthDay = String.format(Locale.US, "%02d-%02d",
            now.get(Calendar.MONTH) + 1, 
            now.get(Calendar.DAY_OF_MONTH)
        )
        
        // 优先检查特殊日期
        specialDayMessages[monthDay]?.let { return it }
        
        // 根据时间段返回不同提示
        return when (hour) {
            in 23..23, in 0..4 -> lateNightMessages.random()
            in 5..7 -> earlyMorningMessages.random()
            else -> refreshMessages.random()
        }
    }
    
    // ═══════════════════════════════════════════════════
    // ✨ 版本号点击彩蛋
    // ═══════════════════════════════════════════════════
    private val versionClickMessages = listOf(
        "再点 %d 次有惊喜！",
        "你在找什么？🤔",
        "别点啦～",
        "我痒痒～别戳了！😆",
        "你发现我了！",
        "点点点，你可真会点！",
        "恭喜解锁隐藏成就！🏆"
    )
    
    /**
     * 获取版本号点击提示
     * @param clickCount 当前点击次数
     * @param threshold 触发彩蛋的阈值
     */
    fun getVersionClickMessage(
        clickCount: Int,
        threshold: Int = VERSION_EASTER_EGG_THRESHOLD
    ): String {
        val remaining = threshold - clickCount
        return when {
            remaining > 3 -> "再点 $remaining 次有惊喜！"
            remaining == 3 -> "就快了！还差 3 次..."
            remaining == 2 -> "还差 2 次！加油！"
            remaining == 1 -> "最后一次！"
            remaining == 0 -> "🎉 恭喜发现开发者彩蛋！"
            else -> versionClickMessages.random()
        }
    }
    
    /**
     * 判断是否触发了版本彩蛋
     */
    fun isVersionEasterEggTriggered(
        clickCount: Int,
        threshold: Int = VERSION_EASTER_EGG_THRESHOLD
    ): Boolean {
        return clickCount >= threshold
    }
    
    // ═══════════════════════════════════════════════════
    // 💰 投币趣味提示
    // ═══════════════════════════════════════════════════
    private val coinMessages = listOf(
        "感谢土豪！💰",
        "你的硬币闪闪发光～ ✨",
        "UP主会很开心的！",
        "大手笔！豪气！",
        "投币成功！你很有眼光～"
    )
    
    fun getCoinMessage(): String = coinMessages.random()
    
    // ═══════════════════════════════════════════════════
    // ❤️ 点赞趣味提示  
    // ═══════════════════════════════════════════════════
    private val likeMessages = listOf(
        "感谢你的喜欢！❤️",
        "这个赞很有分量！",
        "赞！说明你很有品味～",
        "一个赞，传递满满正能量！",
        "被你喜欢的视频很幸福～"
    )
    
    fun getLikeMessage(): String = likeMessages.random()
    
    // ═══════════════════════════════════════════════════
    // ⭐ 收藏趣味提示
    // ═══════════════════════════════════════════════════
    private val favoriteMessages = listOf(
        "收藏夹 +1 ⭐",
        "等你有空慢慢看～",
        "好视频值得收藏！",
        "珍藏成功！🎁",
        "记得回来看哦～"
    )
    
    fun getFavoriteMessage(): String = favoriteMessages.random()
    
    // ═══════════════════════════════════════════════════
    // 🎰 幸运抽奖（低概率彩蛋）
    // ═══════════════════════════════════════════════════
    private val luckyMessages = listOf(
        " 今天你是欧皇！",
        "🍀 四叶草保佑你！",
        "🎯 恭喜触发隐藏彩蛋！",
        "✨ 你被锦鲤附体了！",
        "🎊 第 88888 位幸运用户！（并不）"
    )
    
    /**
     * 低概率触发幸运彩蛋
     * @param probability 触发概率 (0.0 - 1.0)
     */
    fun tryLuckyMessage(probability: Float = 0.02f): String? {
        return if (Random.nextFloat() < probability) {
            luckyMessages.random()
        } else null
    }
    
    // ═══════════════════════════════════════════════════
    // 🔍 搜索彩蛋关键词
    // ═══════════════════════════════════════════════════
    private val searchEasterEggs = mapOf(
        "bilipai" to "欢迎使用 BiliPai！感谢你的支持 ",
        "开发者" to "开发者正在努力搬砖中... 🧱",
        "彩蛋" to "恭喜你找到了彩蛋！",
        "感谢" to "不用谢，应该的！😊",
        "666" to "老铁双击 666！",
        "爱你" to "我也爱你！💕",
        "加油" to "你也加油！💪",
        "摸鱼" to "被你抓到了... 🐟",
        "好看" to "你更好看！✨"
    )
    
    /**
     * 检查搜索关键词是否触发彩蛋
     */
    fun checkSearchEasterEgg(keyword: String): String? {
        val lowerKeyword = keyword.lowercase().trim()
        return searchEasterEggs.entries.find { 
            lowerKeyword.contains(it.key) 
        }?.value
    }
    
    // ═══════════════════════════════════════════════════
    // 😴 空状态趣味文案
    // ═══════════════════════════════════════════════════
    private val emptyHistoryMessages = listOf(
        "这里什么都没有... 快去看视频吧！",
        "历史记录空空如也，是新用户吗？",
        "还没看过视频？来探索新世界吧！"
    )
    
    private val emptyFavoriteMessages = listOf(
        "收藏夹空空的，等你来填满～",
        "好视频值得收藏！加油！",
        "快去发现值得收藏的内容吧！"
    )
    
    private val emptySearchMessages = listOf(
        "什么都没找到... 换个关键词试试？",
        "搜索结果为空，要不试试其他的？",
        "暂无相关内容，但相信你能找到的！"
    )
    
    fun getEmptyHistoryMessage(): String = emptyHistoryMessages.random()
    fun getEmptyFavoriteMessage(): String = emptyFavoriteMessages.random()
    fun getEmptySearchMessage(): String = emptySearchMessages.random()
    
    // ═══════════════════════════════════════════════════
    // 👋 问候语（首页顶部）
    // ═══════════════════════════════════════════════════
    fun getGreeting(): String {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 0..5 -> "夜深了，注意休息 "
            in 6..8 -> "早上好！☀️"
            in 9..11 -> "上午好！"
            in 12..13 -> "中午好！该吃饭啦 🍚"
            in 14..17 -> "下午好！"
            in 18..22 -> "晚上好！🌃"
            else -> "夜深了，注意休息 "
        }
    }
    
    // ═══════════════════════════════════════════════════
    //  Konami Code 彩蛋状态
    // ═══════════════════════════════════════════════════
    private var konamiSequence = mutableListOf<String>()
    private val konamiCode = listOf("up", "up", "down", "down", "left", "right", "left", "right")
    
    /**
     * 输入 Konami 手势
     * @return 是否触发成功
     */
    fun inputKonamiGesture(direction: String): Boolean {
        konamiSequence.add(direction)
        if (konamiSequence.size > konamiCode.size) {
            konamiSequence.removeAt(0)
        }
        return konamiSequence == konamiCode
    }
    
    fun resetKonamiSequence() {
        konamiSequence.clear()
    }
}

