# AGENTS.md

本文件为在本仓库中工作的 AI Agent / Codex / 自动化改码工具提供项目级约束。执行任务时优先遵守本文件；若子目录内存在更近层级的 `AGENTS.md`，以更近层级为准。

## 项目定位

BBTTVV 是面向 Android TV 的第三方客户端，核心目标是：

- 电视遥控器 D-Pad 操作稳定、可预期；
- 首页多 Tab、视频网格、播放页在低端电视设备上保持流畅；
- 播放链路以 Media3 ExoPlayer 为主，兼顾直播、点播、弹幕、字幕和调试信息；
- 保持现代 Android 架构，但允许在关键 TV 焦点场景中使用传统 View / RecyclerView 混合实现。

不要把项目当作普通手机 App 处理。所有 UI、焦点和性能修改都必须以 Android TV 遥控器体验为第一优先级。

## 项目架构

项目采用分层架构，各层职责明确：

| 层级 | 包路径 | 职责 |
|------|--------|------|
| 入口层 | `app/` | Application、启动编排 |
| UI 层 | `ui/` | Compose 页面、焦点协调、通用组件 |
| 功能层 | `feature/` | 按功能模块划分（video / live / profile / search / settings / login / plugin / publisher） |
| 导航层 | `navigation/` | Compose Navigation 路由和状态 |
| 数据层 | `data/` | Repository、API 响应模型、数据库 |
| 核心层 | `core/` | 网络、播放器引擎、插件系统、存储、分页状态机、工具类 |
| 领域层 | `domain/` | UseCase |

依赖注入采用手动 DI（`AppContainer` 单例），不使用 Hilt / Dagger / Koin。

模块结构（4 个模块）：

- `:app` — 主应用模块
- `:baselineprofile` — Baseline Profile 生成模块
- `:settings-core` — 设置核心库模块
- `:network-core` — 网络核心库模块

## 技术栈

主要技术及版本：

- Kotlin 2.3.10 / Java 21 (JVM Target)
- Android Gradle Plugin 8.13.2
- compileSdk 36 / minSdk 26 / targetSdk 35
- Jetpack Compose (BOM 2025.12.00)
- AndroidX TV Material 1.0.0
- Compose Navigation 2.9.4
- Media3 ExoPlayer 1.10.0（含 DASH / HLS / OkHttp DataSource）
- Retrofit 2.9.0 / OkHttp 4.12.0 / kotlinx-serialization-json 1.6.3
- Room 2.8.4 / DataStore 1.2.1
- Coil 2.7.0
- Protobuf 3.25.3 (lite 模式)
- DanmakuRenderEngine v0.1.0（字节跳动）
- ZXing Core 3.5.3 / Brotli 0.1.2
- 关键焦点控件使用 AndroidView + RecyclerView 混合实现
- ViewBinding 已启用

## 常用命令

在修改代码后，根据改动范围选择运行：

```bash
./gradlew tvVerification --no-daemon --stacktrace    # lintDebug + testDebugUnitTest + assembleDebug
./gradlew tvBuild --no-daemon --stacktrace            # assembleRelease
./gradlew tvReleaseVerification --no-daemon --stacktrace  # lintRelease + assembleRelease
./gradlew lintDebug --no-daemon --stacktrace
./gradlew testDebugUnitTest --no-daemon --stacktrace
```

连接 Android TV 或模拟器后，可运行：

```bash
./gradlew installDebug
./gradlew tvInstall                                     # installRelease
./gradlew tvUiRegression                                # connectedDebugAndroidTest
./gradlew tvBaselineProfile                             # 生成 Baseline Profile
```

如果本地环境不支持完整验证，至少说明未运行的命令和原因。

## 修改原则

### 1. 优先小步修改

- 优先修复具体问题，不要顺手大范围重构。
- 不要在一个提交里同时改架构、UI、播放器、网络和插件系统。
- 删除代码前必须确认没有被 Compose Preview、导航、反射、Room、序列化或 ProGuard/R8 依赖。

### 2. 不要全面迁移 UI 技术栈

禁止在没有明确任务要求时做以下改动：

- 将 Compose 页面整体改成 View/XML；
- 将 View/RecyclerView 混合控件整体改回纯 Compose；
- 引入新的大型 UI 框架；
- 为了短期修复焦点问题重写整个首页或播放器。

当前推荐路线是：

- 业务页面以 Compose 为主；
- 顶部栏、复杂 TV 焦点入口、必要的高风险列表控件可以使用 AndroidView + RecyclerView；
- 焦点状态通过 Coordinator / State 对象集中管理。

### 3. TV 焦点是核心约束

修改任何可聚焦 UI 时，必须考虑：

- DPAD_UP / DOWN / LEFT / RIGHT 的边界行为；
- DPAD_CENTER / ENTER 的点击行为；
- 长按确认键是否需要触发二级动作；
- 返回键是否回到上一级焦点区域，而不是直接退出；
- 数据刷新、分页 append、item 消失时焦点是否会逃逸；
- 从播放页、详情页返回首页时是否能恢复合理焦点。

不要用大量 `delay()`、多轮 `withFrameNanos {}` 或循环 `requestFocus()` 掩盖焦点模型问题。优先使用明确的 pending focus intent、注册式 focus target、稳定 key 和有限重试。

## 焦点架构约定

焦点体系采用三层架构：

1. **全局层**：`TvFocusEscapeGuard`（防焦点逃逸）+ `TvFocusReturn`（焦点返回）— 在 `MainActivity` 通过 `CompositionLocalProvider` 注入，`dispatchKeyEvent` 中拦截焦点逃逸事件；
2. **页面层**：各页面独立的 FocusCoordinator — `HomeFocusCoordinator`、`DetailFocusCoordinator`、`PlayerFocusCoordinator`、`ProfileFocusCoordinator`；
3. **组件层**：网格内焦点状态 — `HomeRecommendGridFocusState`、`HomeTabGridFocusStates`、`DpadGridController` 等。

### 全局焦点基础设施

- `TvFocusEscapeGuard`：在 `MainActivity.dispatchKeyEvent` 中拦截焦点逃逸，防止焦点跑到不可预期的 View；
- `TvFocusReturn`：通过 `CompositionLocal` 向下传递，支持跨页面的焦点返回语义；
- 两者均在 `MainActivity.onCreate` 中创建，通过 `CompositionLocalProvider` 注入 Compose 树。

### 首页焦点

首页焦点由 `HomeFocusCoordinator` 统一调度。新增首页区域时，应注册为明确的 `HomeFocusRegion`，不要在页面内部直接抢焦点。

`HomeFocusRegion` 枚举值：

- `TopBar` — 顶部导航栏
- `ContentTabs` — 内容区域二级 Tab
- `Grid` — 视频网格
- `DynamicLiveUsers` — 动态直播用户栏
- `DynamicFollowUpdates` — 动态关注更新
- `SearchInput` — 搜索输入
- `SearchCategory` — 搜索分类
- `ProfileSidebar` — 个人侧边栏
- `ProfileContent` — 个人内容区域

推荐分层：

- `HomeFocusCoordinator`：负责 TopBar、内容 Tab、Grid、动态用户栏等区域间焦点切换，通过 `HomeFocusIntent` 密封类表达焦点意图（`FocusTopBar`、`FocusTopBarTab`、`FocusSelectedContent`、`FocusRegion`、`RestoreVideoKey`）；
- Grid 内部 Focus State / Controller：负责 item 索引、稳定 key、分页后恢复、刷新期间 focus parking；
- 单个 Card：只处理自身视觉状态、点击和长按，不应决定全局焦点流向。

新增页面接入首页时，至少提供：

- 内容区域首个可聚焦目标（通过 `HomeFocusTarget` 接口注册）；
- 顶部边缘回到 TopBar 的处理；
- 从 TopBar 向下进入内容的处理；
- 可选的稳定 key 恢复能力。

### 顶部栏

顶部栏当前使用 `AndroidView + RecyclerView` 是有意设计，用于降低 Compose 重组和 TV 焦点不稳定。不要轻易改回纯 Compose。

修改顶部栏时注意：

- 保持 item stable id（`setHasStableIds(true)`，`getItemId` 返回 `tab.ordinal.toLong()`）；
- 禁用不必要的 change animation（`supportsChangeAnimations = false`）；
- 左右边界应消费或保持当前焦点，避免焦点逃逸（`focusSearch` 中 `FOCUS_LEFT` / `FOCUS_RIGHT` 返回自身）；
- DPAD_DOWN 应委托给首页焦点协调器；
- 顶部栏不应自己知道具体内容页面实现。

### 详情页焦点

详情页焦点通过 `DetailFocusCoordinator` 管理。支持：

- `DetailFocusIntent`：`FocusPlayButton`、`RestoreComment`；
- 按 `rpid` 索引的评论焦点恢复；
- `recoverFocusAfterEscape()` 处理焦点逃逸后的恢复。

### 播放器焦点

播放器焦点通过 `PlayerFocusCoordinator` 管理。支持：

- `PlayerFocusIntent`：`FocusPlayerSurface`、`FocusProgress`、`FocusCommentsPanel`、`FocusAction(index)`、`FocusPanelOption(index)`；
- 同文件包含 `PlayerCommentFocusCoordinator`，管理播放器评论面板中按 key 索引的评论焦点。

播放器内不要直接散落：

```kotlin
focusRequester.requestFocus()
playerView.requestFocus()
```

应改为表达明确意图，例如：

- 聚焦播放 Surface（`FocusPlayerSurface`）；
- 聚焦进度条（`FocusProgress`）；
- 聚焦控制按钮（`FocusAction(index)`）；
- 聚焦设置面板项（`FocusPanelOption(index)`）；
- 聚焦评论面板（`FocusCommentsPanel`）。

### 个人页焦点

个人页焦点通过 `ProfileFocusCoordinator` 管理。支持：

- 侧边栏菜单焦点，每个 `ProfileMenu` 对应独立的 `FocusRequester`；
- 内容区域通过 `ProfileContentFocusTargetState` 注册为 `HomeFocusRegion.ProfileContent` 目标。

## 导航架构

使用 Compose Navigation（`NavHost` + `composable`），路由定义在 `ScreenRoutes`：

- `Home` → `HomeScreen`
- `Settings` → `SettingsScreen`
- `Publisher`（参数：`mid`）→ `PublisherScreen`
- `VideoDetail`（参数：`bvid`）→ `DetailScreen`（通过 `videoDetailRoutes()` 扩展）
- `CommentReplies`（参数：`bvid`, `aid`, `rootRpid`）→ `CommentRepliesScreen`
- `VideoPlayer`（参数：`bvid`, `cid`, `aid`, `startPositionMs`）→ `PlayerScreen`
- `LivePlayer`（参数：`roomId`）→ `LivePlayerScreen`

`AppNavigationState` 管理从详情页 / 播放器返回首页时的视频焦点恢复（`restoreVideoFocusKey` / `restoreVideoFocusTab` / `hasPendingVideoFocusRestore`）。

## 列表与分页约定

Feed 类页面遵循以下模型：

- 原始数据列表：`sourceItems`（`PagedFeedGridState` 内部 `MutableList<SourceItem>`）；
- 可见数据列表：`visibleItems`（`PagedFeedGridState` 内部 `MutableList<VisibleItem>`）；
- 分页状态：`PagedGridStateMachine<K>`（泛型 K 为分页键类型），包含 `nextKey`、`isLoading`、`endReached`、`generation`；
- 焦点状态：按 Tab 或页面独立保存；
- 筛选与去重：不要放在 Composable 热路径内。

`PagedGridStateMachine` 的 `LoadResult` 密封接口：

- `Applied` — 成功应用
- `Skipped(AlreadyLoading / EndReached)` — 跳过
- `Aborted` — 中止
- `IgnoredStale` — 忽略过期结果（通过 generation 判定）

新增或修改分页时：

- refresh 才允许清空当前页；
- load more 只处理新增页，不要每次对全量列表重新过滤；
- 使用 generation / token 防止旧请求覆盖新状态；
- 插件筛选、关键词过滤、去重等 CPU 工作放到 `Dispatchers.Default`；
- 网络、数据库、文件 IO 放到 `Dispatchers.IO`；
- UI State 更新应尽量一次性提交，避免连续触发多次重组。

Compose 列表必须使用稳定 key。视频类 key 优先级建议：

1. bvid；
2. aid；
3. roomId / epId / seasonId；
4. 明确构造的 fallback key。

不要使用不稳定 index 作为长期焦点恢复 key。

## 性能要求

面向 Android TV 时，以下改动需要特别谨慎：

- 在 Composable 中执行过滤、排序、JSON 解析、正则匹配；
- 在焦点变化时立即触发重网络请求；
- 在每个 item 内创建重对象；
- 在列表 item 中无约束地启动协程；
- 切 Tab 时销毁并重建大型 ViewModel / 列表状态；
- 在 release 热路径中保留大量日志。

推荐做法：

- `remember` 只缓存轻量 UI 派生值；
- 重计算放入 ViewModel；
- 首屏、Tab 切换、详情返回首页、播放首帧要保留性能埋点（`AppPerformanceTracker`）；
- 对焦点预取做 debounce，避免快速移动焦点触发大量预取；
- `BbtvApplication` 实现 `ComponentCallbacks2`，按优先级清理图片缓存、PlayUrl 缓存、弹幕缓存。

## 播放器约定

播放器以 Media3 ExoPlayer 为主链路（`PlayerEngineKind.EXO_PLAYER` 为唯一引擎类型）。除非任务明确要求，不要引入新的播放器内核或大规模替换播放架构。

播放器核心组件：

- `createConfiguredPlayer()`（顶层函数）：创建配置好的 ExoPlayer（OkHttp DataSource、WiFi / 移动网络不同缓冲策略、`AppRenderersFactory` 解码器 fallback、`SeekParameters.CLOSEST_SYNC`、`VolumeBalanceAudioProcessor`）；
- `BasePlayerViewModel`：ExoPlayer 绑定 / 解绑、DASH / 分段 / 普通 / 流媒体播放、弹幕数据加载与分段预取，所有方法强制主线程调用；
- `PlayerMediaSourceCoordinator`：构建 MediaSource，支持 CDN 候选 URL fallback（`CdnFailoverDataSourceFactory`）、DASH MPD、多段视频、直播流自动识别 HLS / 渐进式；
- `ExoPlayerLifecycle`：`ExoPlayerReleaseGuard` 安全释放 ExoPlayer（finish session → detach owner → remove listeners → detach PlayerView → release）；
- `SponsorBlockController`：SponsorBlock 空降助手逻辑。

修改播放器时必须注意：

- 保持点播、直播、弹幕、字幕、进度上报的状态隔离；
- 播放退出不要阻塞返回页面首帧；
- 历史记录、心跳、调试日志应异步处理；
- 解码器 fallback、清晰度切换、SurfaceView / TextureView 切换要保留可观测日志；
- Debug Overlay（`PlayerDebugOverlay`）应仅在调试场景启用，不应影响 release 热路径性能；
- CDN failover 切换要保留可观测日志。

### 弹幕管线

弹幕渲染使用字节跳动 `DanmakuRenderEngine`（`DanmakuView`），通过 `AndroidView` 嵌入。

弹幕数据获取（`DanmakuRepository`）：

- Protobuf 分段弹幕：`getDanmakuSegment(cid, segmentIndex)` / `getDanmakuSegments(cid, durationMs)`，分段时长 6 分钟；
- XML fallback：`getDanmakuRawData(cid)`，支持 deflate 解压；
- 游客 fallback：`requestBytesWithGuestFallback()` 先尝试带 Cookie 请求，失败后无 Cookie 重试；
- 弹幕元数据：`getDanmakuView(cid, aid)` 获取分段配置和服务器屏蔽设置；
- 用户过滤规则：关键词 / 正则 / 用户哈希屏蔽；
- 弹幕缓存：`DanmakuCacheManager` 管理 raw XML 和 segment 缓存；
- 直播弹幕：`startLiveDanmaku()` 通过 WebSocket 连接，支持 WBI 签名。

弹幕渲染管线（`PlayerDanmakuPipeline`）：

- `loadSegmentSource()`：加载分段，优先 Protobuf，fallback XML；
- `buildRenderPayload()`：全局过滤 → 插件过滤 → 构建 `DanmakuRenderPayload`；
- 插件管线：先 `DanmakuPlugin.filterDanmaku()`，再 `JsonPluginManager.shouldShowDanmaku()`；
- 样式管线：`DanmakuPlugin.styleDanmaku()` + `JsonPluginManager.getDanmakuStyle()`。

弹幕分段会话控制（`PlayerDanmakuSessionController`）：

- 跟踪已加载 / 加载中 / 失败的分段；
- `prefetchWindow()`：基于当前播放位置计算预取窗口（默认半径 1）；
- 预取不要对 XML fallback 无限请求后续分段。

弹幕同步（`DanmakuOverlay`）：

- 支持 hardSync（全量同步）和 softSync（播放状态同步）；
- 同步触发原因：`PayloadChanged`、`ConfigChanged`、`ViewAttached`、`ViewportReady`、`ViewportChanged`、`PositionDiscontinuity`、`PlayStateChanged`；
- 位置不连续阈值：2500ms。

## 插件与过滤系统

插件系统包含 6 种插件类型：

| 类型 | 接口 | 核心方法 |
|------|------|----------|
| 基础插件 | `Plugin` | `onEnable()` / `onDisable()` / `SettingsContent()` |
| 信息流插件 | `FeedPlugin` | `shouldShowItem(item: VideoItem): Boolean` |
| 播放器插件 | `PlayerPlugin` | `onVideoLoad()` / `onPositionUpdate()` → `SkipAction` / `onUserSeek()` / `onVideoEnd()` |
| 弹幕插件 | `DanmakuPlugin` | `filterDanmaku()` → `DanmakuItem?` / `styleDanmaku()` → `DanmakuStyle?` |
| CDN 插件 | `PlaybackCdnPlugin` | `rewritePlaybackCandidates()` → `PlaybackCdnRewriteResult` |
| 推荐插件 | `RecommendationPluginApi` | `buildRecommendations()` → `RecommendationResult` |

`PlaybackCdnPlugin` 定义在 `feature.plugin` 包，继承自 `Plugin`，用于重写播放 CDN 候选 URL。
`RecommendationPluginApi` 定义在 `core.plugin` 包，继承自 `Plugin`，用于构建个性化推荐队列。

`PluginCapability` 枚举声明插件能力：

`PLAYER_STATE`、`PLAYER_CONTROL`、`DANMAKU_STREAM`、`DANMAKU_MUTATION`、`PLAYBACK_CDN`、`RECOMMENDATION_CANDIDATES`、`LOCAL_HISTORY_READ`、`LOCAL_FEEDBACK_READ`、`NETWORK`、`PLUGIN_STORAGE`

包含授权验证：`resolvePluginCapabilityGrants()`、`validateRecommendationPluginAccess()`。

`PluginManager`（单例）核心能力：

- `register(plugin)` 注册插件，从 `PluginStore` 读取启用状态；
- `setEnabled(pluginId, enabled)` 切换启用 / 禁用，触发 `onEnable()` / `onDisable()`；
- `danmakuPluginUpdateToken` / `feedPluginUpdateToken` 热更新信号；
- `pendingEnabledOverrides` 处理注册前就收到启用请求的情况。

内置插件（分布在 `BuiltInPlugins.kt` 和独立文件中）：

- `SponsorBlockPlugin`（BuiltInPlugins.kt）— 赞助商片段跳过
- `AdFilterPlugin`（BuiltInPlugins.kt）— 广告过滤
- `DanmakuEnhancePlugin`（BuiltInPlugins.kt）— 弹幕增强
- `TodayWatchPlugin`（独立文件）— 今日观看
- `CdnRegionPlugin`（独立文件，实现 `PlaybackCdnPlugin`）— CDN 区域选择

`JsonRulePlugin` 支持通过 JSON 配置规则进行 Feed 过滤和弹幕过滤，由 `JsonPluginManager` + `RuleEngine` 驱动。

插件系统可能影响首页、热门、今日观看、动态等 Feed。修改插件或过滤逻辑时：

- 不要在 UI 线程执行重过滤；
- 插件更新可以触发全量重滤（`reapplyPluginFilters` 从 `sourceSnapshot()` 重新过滤，`replaceVisible()` 替换可见列表），但普通分页只应过滤新增页；
- 统计落盘应 debounce；
- 过滤结果要保留 source 与 visible 两层状态，方便插件更新后重算；
- 不要让单个插件异常导致整个 Feed 崩溃。

## 启动流程

`BbtvApplication` 通过 `AppStartupOrchestrator` 按顺序执行启动任务：

1. 网络模块初始化 + WbiKey 恢复
2. Token 管理器初始化
3. 播放仓库初始化
4. 后台管理器初始化
5. 播放器设置缓存
6. Token 预热
7. 首页 Feed 预加载
8. 后台预热
9. 崩溃上报
10. 插件系统初始化

非关键任务延迟到首帧渲染后执行（`onFirstFrameRendered()`）。

## 构建与 ABI

Debug 构建包含模拟器 ABI：

- `arm64-v8a`
- `x86`
- `x86_64`

面向真实 Android TV 的 release 构建默认只包含：

- `arm64-v8a`

可通过 Gradle 属性 `bbttvv.releaseAbi` 覆盖（允许值：`arm64-v8a`、`armeabi-v7a`，逗号分隔）。

除非任务明确要求 universal APK，不要无理由扩大 release ABI 范围。

Release 应保持：

- R8 enabled（`isMinifyEnabled = true`）；
- resource shrink enabled（`isShrinkResources = true`）；
- debuggable false；
- 不保留重调试日志；
- 必要的 ProGuard / R8 keep 规则完整（当前规则覆盖 Retrofit 接口、kotlinx.serialization 模型、Protobuf 生成类）。

## 代码风格

- 使用 Kotlin idiom，保持空安全和明确的 sealed class / data class 状态建模。
- Composable 参数较多时，优先提炼 state holder 或 coordinator，而不是继续追加松散 lambda。
- 命名应表达 TV 焦点意图，例如 `requestTopBarFocus`、`handleGridTopEdge`、`restoreVideoKey`。
- 避免"万能 Manager"继续膨胀。焦点、分页、播放器、插件、设置应分域管理。
- 新增 public / internal API 时尽量补充短注释，说明 TV 焦点或生命周期约束。
- `BasePlayerViewModel` 中所有方法强制主线程调用（`ensureMainThread`），修改时保持此约束。

## 禁止事项

除非任务明确要求，不要：

- 删除现有焦点 Coordinator；
- 将顶部栏改回纯 Compose；
- 在 Composable 热路径做 Feed 过滤；
- 用无限重试或长延迟解决焦点问题；
- 在 release 构建中打开 debug overlay；
- 在主线程做网络、数据库、文件 IO；
- 把临时测试 URL、账号 Cookie、token、签名密钥提交到仓库；
- 引入 Hilt / Dagger / Koin 等依赖注入框架（项目使用手动 DI）；
- 在 `BasePlayerViewModel` 中绕过 `ensureMainThread` 约束。

## 提交前检查清单

提交前至少自查：

- 代码能编译；
- D-Pad 上下左右行为符合 TV 预期；
- 返回键行为符合当前页面层级；
- 首页返回焦点不会丢到错误区域；
- 分页、刷新、插件更新不会导致焦点逃逸；
- 没有新增主线程重计算；
- release 配置没有被误改；
- 没有提交密钥、Cookie 或本地路径；
- ProGuard / R8 keep 规则覆盖新增的序列化类和 Retrofit 接口。

## 推荐验证路径

涉及首页或焦点时，至少手动验证：

1. 冷启动进入推荐页；
2. TopBar 向下进入视频 Grid；
3. Grid 上边缘回到 TopBar；
4. Grid 快速向下触发分页；
5. 切换热门、直播、动态、今日观看；
6. 进入详情页再返回首页；
7. 进入播放器再返回首页；
8. 打开 Profile / 设置后返回；
9. 网络失败或空列表状态下焦点仍可恢复。

涉及播放器时，至少验证：

1. 点播首帧；
2. 直播首帧；
3. 暂停、恢复、seek；
4. 清晰度 / 线路切换；
5. CDN failover 切换；
6. 弹幕开关与 fallback（Protobuf → XML → 游客）；
7. 播放页返回详情或首页；
8. Debug Overlay 不影响普通播放。

涉及插件时，至少验证：

1. 插件启用 / 禁用后 Feed 列表正确更新；
2. 插件更新触发全量重滤后焦点不逃逸；
3. 单个插件异常不导致整个 Feed 崩溃；
4. JSON 规则插件的过滤和样式效果正确。

## Agent 输出要求

完成任务后，应说明：

- 改了哪些文件；
- 为什么这样改；
- 运行了哪些验证命令；
- 哪些验证未运行及原因；
- 是否存在后续风险或建议。
