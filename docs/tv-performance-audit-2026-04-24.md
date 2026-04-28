# Android TV 性能与模块审计记录

审计日期：2026-04-24

目标设备假设：Android TV / 电视盒子，CPU 性能偏低，内存常见 4GB，D-Pad 操作稳定性优先。

## 结论摘要

本轮审计确认当前代码已经在两个关键方向上做了正确拆分：

- 播放器：`PlayerScreen.kt` 已从大型单文件拆成 Surface、Overlay、Panel、ActionBar、Effects、Bindings 等文件，应继续保持这种分域结构。
- 首页：`HomeViewModel.kt` 已将分页、详情预取、今日观看、Tab Store 管理拆出为 `HomeFeedController`、`HomeDetailPrefetcher`、`TodayWatchCoordinator`、`HomeTabStoreOwner`，方向符合 TV 低端设备的性能约束。

本轮已落地的代码优化集中在运行时热路径，避免把审计演变成大范围重构：

- Feed 插件过滤：每页只获取一次启用插件快照，避免每个视频重复复制插件列表。
- JSON 规则引擎：缓存规则条件和正则编译结果，避免分页/弹幕过滤时频繁编译正则。
- WBI 签名：复用非法字符正则，手写 MD5 十六进制转换，避免 `String.format` 分配。
- 搜索 fallback：复用 `Json` 实例，消除 Kotlin 编译器提示的重复 `Json` 构造。
- 推荐隐藏过滤：没有“不感兴趣”记录时跳过 `filterNot`，避免正常分页路径额外复制列表。

## 已处理位置

| 位置 | 问题 | 处理 | 预期收益 |
| --- | --- | --- | --- |
| `app/src/main/java/com/bbttvv/app/core/plugin/PluginManager.kt` | `filterFeedItems()` 旧实现对每个 item 调 `shouldShowFeedItem()`，而后者每次都会复制并过滤启用插件列表。 | 批量过滤时先取一次 `enabledFeedPluginsSnapshot()`，逐 item 复用。 | 推荐/热门/动态分页中，插件列表复制次数从 `视频数` 降为 `1`；减少 CPU 和短生命周期对象。 |
| `app/src/main/java/com/bbttvv/app/core/plugin/json/RuleEngine.kt` | `regex` 规则每次匹配都 `Regex(pattern)`，旧格式 rule 每次都可能重新转换 condition。 | 增加弱引用 condition cache 和 96 条有界正则 LRU；非法正则也缓存为 miss。 | 插件过滤和弹幕过滤中，正则编译从 `item * rule` 降为 `唯一 pattern`；明显降低 GC 抖动。 |
| `app/src/main/java/com/bbttvv/app/core/network/WbiSigner.kt` | 每次签名构造非法字符 `Regex`，MD5 使用 `"%02x".format()` 对每个 byte 创建格式化开销。 | 复用正则，使用 `CharArray` 手写 hex，debug 日志改为 lambda。 | 搜索、详情、评论等签名请求减少分配；低端 CPU 下请求密集时更稳。 |
| `app/src/main/java/com/bbttvv/app/data/repository/SearchRepository.kt` | fallback 路径重复创建 `Json { ignoreUnknownKeys = true }`。 | 提升为 `fallbackJson` 单例。 | 消除重复配置对象，减少搜索分页 fallback 的分配。 |
| `app/src/main/java/com/bbttvv/app/ui/home/HomeFeedController.kt` + `RecommendDismissStore.kt` | 常规情况下没有 dismissed item，仍然会执行 `filterNot` 复制列表。 | 增加 `hasDismissed()`，无隐藏项时直接返回插件过滤结果。 | 推荐页正常分页路径少一次列表遍历和分配。 |
| `app/src/main/java/com/bbttvv/app/feature/live/LiveDebugSnapshot.kt` | `lintDebug` 被 Media3 `UnstableApi` opt-in error 阻断。 | 给 `resolveFormatBitrate()` 补充 `@OptIn`。 | 不改变运行逻辑，恢复项目级 lint 验证。 |

## 模块拆分审计

### 播放器

当前状态：

- `PlayerScreen.kt` 约 12KB，已变成组合入口。
- UI 与副作用拆入 `PlayerOverlayHost.kt`、`PlayerScreenEffects.kt`、`PlayerScreenBindings.kt`、`PlayerSurfaceHost.kt` 等文件。
- 焦点仍通过 `PlayerFocusCoordinator` 表达 intent，而不是在各 UI 处散落 `requestFocus()`。

建议：

- 不要把这些文件合回 `PlayerScreen.kt`。
- 下一步应拆 `PlayerViewModel.kt`，当前约 68KB，建议分成：
  - 播放会话与进度上报；
  - 清晰度/音频/解码器选项；
  - 评论面板状态；
  - SponsorBlock 状态；
  - debug snapshot 与诊断导出。

预期收益：

- ViewModel 状态更新更局部，降低播放器 overlay 重组面。
- 更容易保证播放退出不阻塞返回首帧。
- 调试 overlay、评论、赞助跳过等低频功能可从主播放链路剥离。

风险：

- 播放器状态耦合较强，拆分时必须保留点播、直播、弹幕、字幕、进度上报隔离，不能一次性大重构。

### 首页 Feed

当前状态：

- `HomeViewModel.kt` 已降到约 15KB。
- 分页与插件过滤在 `HomeFeedController.kt`，今日观看在 `TodayWatchCoordinator.kt`，Tab ViewModel 保留策略在 `HomeTabStoreOwner.kt`。
- 首页网格使用 `AndroidView + RecyclerView`，并关闭 item animator，符合 TV 焦点稳定优先原则。

建议：

- 保持 `HomeFeedController` 作为 source/visible 双层状态的唯一入口。
- 不要把今日观看逻辑塞回首页 ViewModel。
- `VideoCardRecyclerViews.kt` 约 22KB，可后续拆为 Grid、Row、Recycler 配置、边界 focus search 四个文件，但不急于改行为。

预期收益：

- 分页 append 只处理新增页，避免插件过滤时全量重算。
- Low RAM 设备通过 `HomeTabStoreOwner` 保留更少 Tab Store，降低内存常驻。

### Profile / 设置 / 搜索页面

发现：

- `ProfileScreen.kt` 约 83KB，是当前最大的 UI 文件。
- `SettingsScreen.kt` 约 26KB，尚可接受。
- `SearchScreen.kt` 约 20KB，结合 `SearchRepository` 的 fallback 优化后暂不需要急拆。

建议：

- 优先拆 `ProfileScreen.kt`：
  - 登录/账号区域；
  - 历史/收藏/稍后再看 rails；
  - 用户统计与空间入口；
  - Profile 专用焦点 coordinator。

预期收益：

- Profile 首屏组合成本下降。
- D-Pad 边界与返回焦点更容易单独测试。

### 网络与签名

发现：

- 多个 repository 自己解析 `wbi_img` 的 `imgKey/subKey`，例如视频、评论、搜索、番剧、发布者相关仓库。
- `network-core` 已存在，但 app 模块内仍有较多网络细节。

建议：

- 合并 WBI key 解析到统一 provider，例如 `WbiKeyManager`/`WbiUtils` 暴露 `signWithLatestKeys(params)`。
- Repository 只表达业务请求，不重复拼签名细节。

预期收益：

- 减少重复字符串处理和错误处理分支。
- WBI key 缓存策略可集中优化，避免页面间重复拉取 nav。

### 弹幕与日志

发现：

- `DanmakuRepository.kt` 约 44KB，包含分段、fallback、缓存等多条链路。
- `Logger.kt` 约 33KB，日志收集与敏感信息脱敏在同一文件。

建议：

- 弹幕拆成 protobuf segment fetcher、XML fallback、cache policy、parser。
- 日志拆出 `SensitiveLogSanitizer`，并将大量脱敏正则预编译。

预期收益：

- 弹幕预取更容易限制 XML fallback 的请求边界。
- 崩溃日志导出/运行日志持久化时减少正则构造与主线程风险。

## 资源占用与内存建议

- Release ABI 当前应保持 `arm64-v8a`，不要无理由扩大到 universal APK。
- 首页、热门、动态等 Feed 必须保留 source/visible 双层数据，插件更新时允许全量重滤，普通分页只处理新增页。
- Coil 图片加载建议后续按 TV 网格尺寸设置统一 thumbnail size，避免 4K 封面原图进入内存。
- RecyclerView 网格的 `setHasFixedSize(true)`、禁用 animator、有限 item cache 是正确方向；后续预取行数应保持小而稳定。
- 低内存设备应继续使用 `TabStorePolicy.KeepSelectedOnly`，避免切 Tab 常驻多个大型 ViewModel。

## 运算效率建议

- Composable 热路径不要做过滤、排序、JSON 解析、正则匹配。
- 插件过滤应保持在 `Dispatchers.Default`。
- 网络、数据库、文件 IO 保持在 `Dispatchers.IO`。
- `Logger.d/i` 在 release 上应优先使用 lambda overload，避免禁用日志时仍构造字符串。

## 后续优先级

1. 拆 `ProfileScreen.kt`，这是最大 UI 文件，也是 D-Pad 焦点最容易膨胀的页面。
2. 拆 `PlayerViewModel.kt`，但要按播放会话、选项、评论、调试四条线小步走。
3. 合并 WBI 签名 key 获取，减少 repository 重复逻辑。
4. 拆 `DanmakuRepository.kt`，限制 XML fallback 和缓存策略边界。
5. 抽出 `SensitiveLogSanitizer` 并预编译脱敏正则。

## 验证状态

- 已运行 `.\gradlew.bat :app:compileDebugKotlin --no-daemon --stacktrace`，通过。
- 已运行 `.\gradlew.bat testDebugUnitTest --no-daemon --stacktrace`，通过。
- 已运行 `.\gradlew.bat lintDebug --no-daemon --stacktrace`，通过；仍有 75 个 warning 和 baseline 过期提示，未在本轮展开处理。
- 未执行 Android TV / 模拟器 D-Pad 手测，因为当前会话未连接目标设备。
