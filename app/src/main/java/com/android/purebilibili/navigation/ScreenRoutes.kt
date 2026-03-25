package com.android.purebilibili.navigation

sealed class ScreenRoutes(val route: String) {
    object Home : ScreenRoutes("home")
    object Search : ScreenRoutes("search")
    object Settings : ScreenRoutes("settings")
    object Login : ScreenRoutes("login")
    object Profile : ScreenRoutes("profile")

    //  新增路由：历史记录和收藏
    object History : ScreenRoutes("history")
    object Favorite : ScreenRoutes("favorite")
    object WatchLater : ScreenRoutes("watch_later")  //  [新增] 稍后再看
    object LiveList : ScreenRoutes("live_list")  //  [新增] 直播列表
    
    //  关注列表页面
    object Following : ScreenRoutes("following/{mid}") {
        fun createRoute(mid: Long): String {
            return "following/$mid"
        }
    }
    
    //  离线缓存列表
    object DownloadList : ScreenRoutes("download_list")
    
    // 🔧 [新增] 离线视频播放
    object OfflineVideoPlayer : ScreenRoutes("offline_video/{taskId}") {
        fun createRoute(taskId: String): String {
            return "offline_video/${android.net.Uri.encode(taskId)}"
        }
    }
    
    //  动态页面
    object Dynamic : ScreenRoutes("dynamic")

    //  动态详情页面
    object DynamicDetail : ScreenRoutes("dynamic_detail/{dynamicId}") {
        fun createRoute(dynamicId: String): String {
            return "dynamic_detail/${android.net.Uri.encode(dynamicId)}"
        }
    }

    object ArticleDetail : ScreenRoutes("article/{articleId}?title={title}") {
        fun createRoute(articleId: Long, title: String? = null): String {
            val encodedTitle = title?.let(android.net.Uri::encode).orEmpty()
            return "article/$articleId?title=$encodedTitle"
        }
    }
    
    //  [新增] 竖屏短视频 (故事模式)
    object Story : ScreenRoutes("story")

    //  开源许可证页面
    object OpenSourceLicenses : ScreenRoutes("open_source_licenses")
    
    //  二级设置页面
    object AppearanceSettings : ScreenRoutes("appearance_settings")
    object PlaybackSettings : ScreenRoutes("playback_settings")
    object PermissionSettings : ScreenRoutes("permission_settings")  //  权限管理
    object PluginsSettings : ScreenRoutes("plugins_settings?importUrl={importUrl}") {  //  插件中心
        fun createRoute(importUrl: String? = null): String {
            if (importUrl.isNullOrBlank()) return "plugins_settings"
            return "plugins_settings?importUrl=${android.net.Uri.encode(importUrl)}"
        }
    }
    object BottomBarSettings : ScreenRoutes("bottom_bar_settings")  //  底栏管理
    object SettingsShare : ScreenRoutes("settings_share")
    object WebDavBackup : ScreenRoutes("webdav_backup") // WebDAV 备份中心
    object TipsSettings : ScreenRoutes("tips_settings") // [Feature] 小贴士 & 隐藏操作
    //  [新增] 更多外观设置子页面

    object IconSettings : ScreenRoutes("icon_settings")  // 图标设置
    object AnimationSettings : ScreenRoutes("animation_settings")  // 动画设置

    // [修复] 添加 aid 参数支持，用于移动端推荐流（可能只返回 aid）
    object VideoPlayer : ScreenRoutes("video_player/{bvid}?cid={cid}&aid={aid}") {
        fun createRoute(bvid: String, cid: Long = 0, aid: Long = 0): String {
            return "video_player/$bvid?cid=$cid&aid=$aid"
        }
    }
    
    //  [新增] UP主空间页面
    object Space : ScreenRoutes("space/{mid}") {
        fun createRoute(mid: Long): String {
            return "space/$mid"
        }
    }

    //  [新增] 合集/系列详情页面
    object SeasonSeriesDetail : ScreenRoutes("season_series_detail/{type}/{id}?mid={mid}&title={title}") {
        fun createRoute(type: String, id: Long, mid: Long, title: String): String {
            // Encode title to handle special characters
            val encodedTitle = android.net.Uri.encode(title)
            return "season_series_detail/$type/$id?mid=$mid&title=$encodedTitle"
        }
    }
    
    //  [新增] 直播播放页面
    object Live : ScreenRoutes("live/{roomId}?title={title}&uname={uname}") {
        fun createRoute(roomId: Long, title: String, uname: String): String {
            val encodedTitle = android.net.Uri.encode(title).orEmpty()
            val encodedUname = android.net.Uri.encode(uname).orEmpty()
            return "live/$roomId?title=$encodedTitle&uname=$encodedUname"
        }
    }
    
    //  [新增] 音频模式页面
    object AudioMode : ScreenRoutes("audio_mode")
    
    //  [新增] 番剧/影视页面 - 支持初始类型参数
    object Bangumi : ScreenRoutes("bangumi?type={type}") {
        fun createRoute(initialType: Int = 1): String {
            return "bangumi?type=$initialType"
        }
    }
    
    object BangumiDetail : ScreenRoutes("bangumi/{seasonId}?epId={epId}") {
        fun createRoute(seasonId: Long, epId: Long = 0): String {
            return "bangumi/$seasonId?epId=$epId"
        }
    }
    
    //  [新增] 番剧播放页面
    object BangumiPlayer : ScreenRoutes("bangumi/play/{seasonId}/{epId}") {
        fun createRoute(seasonId: Long, epId: Long): String {
            return "bangumi/play/$seasonId/$epId"
        }
    }
    
    //  分区页面
    object Partition : ScreenRoutes("partition")
    
    //  分类详情页面
    object Category : ScreenRoutes("category/{tid}?name={name}") {
        fun createRoute(tid: Int, name: String): String {
            return "category/$tid?name=${android.net.Uri.encode(name)}"
        }
    }

    // [新增] 新手引导页面
    object Onboarding : ScreenRoutes("onboarding")
    
    // [新增] 私信相关页面
    object Inbox : ScreenRoutes("inbox")  // 收件箱
    object Chat : ScreenRoutes("chat/{talkerId}/{sessionType}?name={name}") {
        fun createRoute(talkerId: Long, sessionType: Int, userName: String): String {
            return "chat/$talkerId/$sessionType?name=${android.net.Uri.encode(userName)}"
        }
    }
    
    // [新增] In-app Browser
    object Web : ScreenRoutes("web?url={url}&title={title}") {
        fun createRoute(url: String, title: String? = null): String {
            val encodedUrl = android.net.Uri.encode(url)
            val encodedTitle = title?.let { android.net.Uri.encode(it) } ?: ""
            return "web?url=$encodedUrl&title=$encodedTitle"
        }
    }
    
    // [新增] Audio Player
    object MusicDetail : ScreenRoutes("music/{sid}") {
        fun createRoute(sid: Long): String {
            return "music/$sid"
        }
    }
    
    // [新增] Native Music - 用于 MA 格式的原生音乐播放 (从视频 DASH 流提取音频)
    object NativeMusic : ScreenRoutes("native_music?title={title}&bvid={bvid}&cid={cid}") {
        fun createRoute(title: String, bvid: String, cid: Long): String {
            return "native_music?title=${android.net.Uri.encode(title)}&bvid=${android.net.Uri.encode(bvid)}&cid=$cid"
        }
    }
}
