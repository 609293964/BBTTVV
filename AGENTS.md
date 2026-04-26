# AGENTS.md

本文件为在本仓库中工作的 AI Agent / Codex / 自动化改码工具提供项目级约束。执行任务时优先遵守本文件；若子目录内存在更近层级的 `AGENTS.md`，以更近层级为准。

## 项目定位

BBTTVV 是面向 Android TV 的第三方客户端，核心目标是：

- 电视遥控器 D-Pad 操作稳定、可预期；
- 首页多 Tab、视频网格、播放页在低端电视设备上保持流畅；
- 播放链路以 Media3 ExoPlayer 为主，兼顾直播、点播、弹幕、字幕和调试信息；
- 保持现代 Android 架构，但允许在关键 TV 焦点场景中使用传统 View / RecyclerView 混合实现。

不要把项目当作普通手机 App 处理。所有 UI、焦点和性能修改都必须以 Android TV 遥控器体验为第一优先级。

## 技术栈

主要技术：

- Kotlin 2.x / Java 21
- Android Gradle Plugin 8.x
- Jetpack Compose
- AndroidX TV Material
- Compose Navigation
- Media3 ExoPlayer
- Retrofit / OkHttp
- Room / DataStore
- Coil
- Protobuf-lite
- 关键焦点控件可使用 AndroidView + RecyclerView 混合实现

## 常用命令

在修改代码后，根据改动范围选择运行：

```bash
./gradlew tvVerification --no-daemon --stacktrace
./gradlew tvBuild --no-daemon --stacktrace
./gradlew lintDebug --no-daemon --stacktrace
./gradlew testDebugUnitTest --no-daemon --stacktrace
```

连接 Android TV 或模拟器后，可运行：

```bash
./gradlew installDebug
./gradlew tvUiRegression
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

### 首页焦点

首页焦点应由 `HomeFocusCoordinator` 统一调度。新增首页区域时，应注册为明确的 `HomeFocusRegion`，不要在页面内部直接抢焦点。

推荐分层：

- `HomeFocusCoordinator`：负责 TopBar、内容 Tab、Grid、动态用户栏等区域间焦点切换；
- Grid 内部 Focus State / Controller：负责 item 索引、稳定 key、分页后恢复、刷新期间 focus parking；
- 单个 Card：只处理自身视觉状态、点击和长按，不应决定全局焦点流向。

新增页面接入首页时，至少提供：

- 内容区域首个可聚焦目标；
- 顶部边缘回到 TopBar 的处理；
- 从 TopBar 向下进入内容的处理；
- 可选的稳定 key 恢复能力。

### 顶部栏

顶部栏当前使用 `AndroidView + RecyclerView` 是有意设计，用于降低 Compose 重组和 TV 焦点不稳定。不要轻易改回纯 Compose。

修改顶部栏时注意：

- 保持 item stable id；
- 禁用不必要的 change animation；
- 左右边界应消费或保持当前焦点，避免焦点逃逸；
- DPAD_DOWN 应委托给首页焦点协调器；
- 顶部栏不应自己知道具体内容页面实现。

### 详情页与播放器焦点

详情页焦点应通过 `DetailFocusCoordinator` 一类对象管理。播放器焦点应通过 `PlayerFocusCoordinator` 一类对象管理。

播放器内不要直接散落：

```kotlin
focusRequester.requestFocus()
playerView.requestFocus()
```

应改为表达明确意图，例如：

- 聚焦播放 Surface；
- 聚焦进度条；
- 聚焦控制按钮；
- 聚焦设置面板项；
- 聚焦评论面板。

## 列表与分页约定

Feed 类页面应遵循以下模型：

- 原始数据列表：`sourceItems` / `sourceVideos`；
- 可见数据列表：`visibleItems` / `visibleVideos`；
- 分页状态：`PagedGridStateMachine` 或等价状态机；
- 焦点状态：按 Tab 或页面独立保存；
- 筛选与去重：不要放在 Composable 热路径内。

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
- 首屏、Tab 切换、详情返回首页、播放首帧要保留性能埋点；
- 对焦点预取做 debounce，避免快速移动焦点触发大量预取。

## 播放器约定

播放器以 Media3 ExoPlayer 为主链路。除非任务明确要求，不要引入新的播放器内核或大规模替换播放架构。

修改播放器时必须注意：

- 保持点播、直播、弹幕、字幕、进度上报的状态隔离；
- 播放退出不要阻塞返回页面首帧；
- 历史记录、心跳、调试日志应异步处理；
- 解码器 fallback、清晰度切换、SurfaceView / TextureView 切换要保留可观测日志；
- Debug Overlay 应仅在调试场景启用，不应影响 release 热路径性能。

弹幕相关修改要考虑：

- Protobuf 分段弹幕；
- XML fallback；
- 游客 fallback；
- 缓存命中；
- 预取不要对 XML fallback 无限请求后续分段。

## 插件与过滤系统

插件系统可能影响首页、热门、今日观看、动态等 Feed。修改插件或过滤逻辑时：

- 不要在 UI 线程执行重过滤；
- 插件更新可以触发全量重滤，但普通分页只应过滤新增页；
- 统计落盘应 debounce；
- 过滤结果要保留 source 与 visible 两层状态，方便插件更新后重算；
- 不要让单个插件异常导致整个 Feed 崩溃。

## 构建与 ABI

Debug 构建可以包含模拟器 ABI：

- `arm64-v8a`
- `x86`
- `x86_64`

面向真实 Android TV 的 release 构建应优先只包含：

- `arm64-v8a`

除非任务明确要求 universal APK，不要无理由扩大 release ABI 范围。

Release 应保持：

- R8 enabled；
- resource shrink enabled；
- debuggable false；
- 不保留重调试日志；
- 必要的 ProGuard/R8 keep 规则完整。

## 代码风格

- 使用 Kotlin idiom，保持空安全和明确的 sealed class / data class 状态建模。
- Composable 参数较多时，优先提炼 state holder 或 coordinator，而不是继续追加松散 lambda。
- 命名应表达 TV 焦点意图，例如 `requestTopBarFocus`、`handleGridTopEdge`、`restoreVideoKey`。
- 避免“万能 Manager”继续膨胀。焦点、分页、播放器、插件、设置应分域管理。
- 新增 public/internal API 时尽量补充短注释，说明 TV 焦点或生命周期约束。

## 禁止事项

除非任务明确要求，不要：

- 删除现有焦点 Coordinator；
- 将顶部栏改回纯 Compose；
- 在 Composable 热路径做 Feed 过滤；
- 用无限重试或长延迟解决焦点问题；
- 在 release 构建中打开 debug overlay；
- 在主线程做网络、数据库、文件 IO；
- 把临时测试 URL、账号 Cookie、token、签名密钥提交到仓库；

## 提交前检查清单

提交前至少自查：

- 代码能编译；
- D-Pad 上下左右行为符合 TV 预期；
- 返回键行为符合当前页面层级；
- 首页返回焦点不会丢到错误区域；
- 分页、刷新、插件更新不会导致焦点逃逸；
- 没有新增主线程重计算；
- release 配置没有被误改；
- 没有提交密钥、Cookie 或本地路径。

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
4. 清晰度/线路切换；
5. 弹幕开关与 fallback；
6. 播放页返回详情或首页；
7. Debug Overlay 不影响普通播放。

## Agent 输出要求

完成任务后，应说明：

- 改了哪些文件；
- 为什么这样改；
- 运行了哪些验证命令；
- 哪些验证未运行及原因；
- 是否存在后续风险或建议。
