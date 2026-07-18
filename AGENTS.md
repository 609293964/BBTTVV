# AGENTS.md

本文件是 BBTTVV 的根级开发约定，适用于整个仓库；若子目录出现更近的 `AGENTS.md`，以更近文件为准。

## 产品判断标准

BBTTVV 是 Android TV 第三方客户端，不是放大版手机 App。实现与评审按以下顺序取舍：

1. 遥控器操作可达、可预测，焦点和 Back 路径不丢失；
2. 点播、直播、字幕、弹幕等主链路正确，并能从常见失败中恢复；
3. 冷启动、首帧、滚动和焦点移动在低端电视上保持流畅；
4. 兼容不同 Android TV 版本、芯片、解码器和遥控器按键实现；
5. 再考虑视觉细节、代码简短或技术栈“纯粹性”。

出现冲突时，优先保护已经稳定的 TV 行为。不要为减少代码、追求统一 UI 技术栈或手机端习惯而牺牲焦点稳定性、播放恢复或设备兼容性。

## 开始任务前

- 先确认用户要的是诊断、修复还是重构；诊断任务不顺手改代码，修复任务不扩张为架构重写。
- 查看工作树并保留用户未提交改动。只编辑任务涉及的文件，不覆盖、还原或批量格式化无关内容。
- 阅读相邻实现、调用方和现有测试，并沿真实链路定位责任：遥控器输入 → 焦点/导航 → UI state → repository/player → 页面恢复。
- 修改前写清本次必须维持的行为不变量。焦点问题至少描述“从哪里来、按什么键、应到哪里、数据变化或返回后怎样恢复”。
- 依赖、SDK、ABI、构建类型和任务名称以 `gradle/libs.versions.toml`、`settings.gradle.kts`、根 `build.gradle.kts` 和各模块 `build.gradle.kts` 为准，不把本文件当版本清单。
- 选择能解决根因的最小改动；不要顺手升级依赖、重命名大批文件或重构无关模块。

## 仓库地图与边界

- `:app`：主应用。`app/` 负责 Application 与启动编排；`core/` 放 DI、网络、播放器、分页、插件和存储等基础设施；`data/` 放模型、服务、数据库和 repository；`domain/` 放用例；`feature/`、`ui/`、`navigation/` 负责功能页面、通用 TV UI、焦点和导航。
- `:network-core`、`:settings-core`：可复用核心库。修改公共 API 前检查 `:app` 调用方，并优先在模块自身补测试。
- `:baselineprofile`：启动和关键用户路径的 Baseline Profile 生成，不承载业务逻辑。
- 项目使用 `AppContainer` 手动依赖注入。除非任务明确要求，不引入 Hilt、Dagger、Koin 或新的 service locator。
- UI 只表达状态与用户意图；跨页面业务状态由 ViewModel/用例/repository 管理。不要为了修复显示问题创建第二条写入路径，或让 Composable 直接改写不属于它的持久化状态。
- 网络响应模型、数据库实体、领域/UI 状态尽量保持职责分离；新增映射前先复用现有 mapper/policy，不在多个页面复制同一业务判断。

## TV 输入、焦点与导航契约

任何新增或修改的可交互 UI 都必须回答：

- 初次进入、冷启动恢复和空数据时，首个可聚焦目标是什么；
- `DPAD_UP/DOWN/LEFT/RIGHT` 在区域内部和四个边界分别去哪里；
- `DPAD_CENTER/ENTER` 的短按是否只触发一次，长按是否有独立语义；
- `Back` 是关闭弹层、收起控制层、返回来源页面、切回默认 Tab，还是二次退出；
- loading、error、空列表、刷新、分页、删除 item、Tab 切换和进出详情/播放器后焦点如何恢复；
- 目标暂时不存在时，焦点停放在哪里，之后按哪个稳定业务 key 恢复。

实现时遵守以下规则：

- 复用三层焦点体系：`MainActivity` 中的 `TvFocusEscapeGuard` / `TvFocusReturn`，页面级 `HomeFocusCoordinator`、`DetailFocusCoordinator`、`PlayerFocusCoordinator`、`ProfileFocusCoordinator`，以及组件/网格级 focus state/controller。
- 区域间移动交给 Coordinator 表达明确的 focus intent；卡片只处理自身视觉、点击和长按，不决定跨区域流向。新增首页区域必须注册目标、进入路径、顶部/边缘退出路径和返回语义。
- `TvFocusEscapeGuard` 是异常兜底，不是正常导航器。不要依靠 guard 反复抢回焦点来掩盖缺失的方向图。
- 不在组件中散落 `requestFocus()`、无限重试、长 `delay` 或多轮 `withFrameNanos`。先修稳定 key、目标注册时机、pending intent 和数据/焦点状态所有权；若确需等待布局，只允许有生命周期边界的有限重试。
- 列表和网格使用稳定业务 key/ID。视频优先使用 `bvid`/`aid`，直播或番剧使用其稳定业务 ID；index 只可表示瞬时位置，不可作为跨刷新或跨页面恢复身份。
- 顶部栏的 `AndroidView + RecyclerView` 混合实现是有意保留的 TV 稳定性方案。保持 stable ID、边界不逃逸、合理关闭 change animation，并把向下进入内容交给页面协调器；不要无故迁回纯 Compose。
- 弹窗、侧栏、播放器控制层开启时要形成明确的焦点域；关闭后回到打开它的控件或显式 fallback，不能落到背景页面或无焦点状态。
- 处理 `KeyEvent` 时区分 down/up、repeat 和 long press，避免一次按键触发两次；只有真正处理事件时才消费，不能吞掉系统或上层仍需处理的按键。
- 焦点视觉必须清楚且不依赖颜色单一表达。缩放、描边和阴影不得造成相邻卡片跳动、裁切或滚动位置漂移，并注意电视 overscan 与观看距离。
- 不为 TV 主流程添加仅触摸可完成的交互；文本输入、二维码、菜单等场景都要有遥控器可完成的路径。

## Compose、View 与生命周期

- 保持现有 Compose + View/RecyclerView 混合架构。选择哪一侧以实际焦点稳定性、滚动性能和生命周期正确性为准，不做无任务依据的全面迁移。
- Composable 应尽量无副作用；监听器、View 绑定、焦点目标和播放器绑定在 `DisposableEffect`/明确生命周期中成对注册与释放。
- 使用 lifecycle-aware state 收集；不要让离屏 Tab、已退出页面或被替换的请求继续更新可见 UI。
- `remember` 保存组合期轻量状态，`rememberSaveable` 只保存小型且可序列化的恢复信息；业务真相放在 ViewModel/repository，不缓存 Activity、View、Player 或大列表。
- `AndroidView` 更新必须幂等，避免每次重组重建 adapter、listener 或重置滚动/焦点；Compose 与 View 之间保持单一状态所有者。
- 导航参数保持最小、稳定并可编码；从详情或播放器返回时沿 `AppNavigationState` / `TvFocusReturn` 的既有语义恢复，不另建平行的全局变量。
- 状态至少区分首次加载、保留旧内容的刷新、追加、空数据和可恢复错误，避免用全屏 loading 替换已有内容并导致焦点树消失。

## 状态、分页、网络与插件

- 网络、数据库和文件操作放在 `Dispatchers.IO`；大列表过滤、去重、排序、JSON/正则处理等 CPU 工作放在 `Dispatchers.Default`；UI state 尽量一次性提交。
- 长任务必须可取消或可忽略过期结果。页面退出、查询条件/Tab 改变和 refresh 后，旧请求不得覆盖新状态。
- Feed 保持 source 与 visible 两层数据；refresh 才可替换/清空来源，append 只处理新页，并使用 `PagedGridStateMachine` 的 generation/token 防止旧结果覆盖。
- 分页失败保留已有列表、当前焦点和可重试能力；到达末页不能继续由焦点移动制造重复请求。
- 焦点预取、详情预取和搜索输入要 debounce/deduplicate；快速移动遥控器不能产生无节制网络请求。
- 保持登录态与游客 fallback 的既有边界。不要在日志、异常、测试 fixture 或提交内容中泄露 Cookie、token、账号、签名密钥、临时播放 URL 或本地绝对路径。
- 插件更新可以从 source 重算 visible，但普通分页不能反复重滤全量列表。单个插件异常不能让 Feed 或播放器崩溃，插件能力授权校验和隔离不能绕过。
- 修改 Retrofit/序列化模型、Room 实体/迁移、Protobuf、反射入口或插件 API 时，同时检查兼容默认值、旧数据、调用方、测试以及 R8/ProGuard keep 规则。

## 播放器、直播、字幕与弹幕

- 播放主链路是 Media3 ExoPlayer。除非任务明确要求，不引入新播放器内核或重写播放器架构。
- `BasePlayerViewModel` 及其子类受 `ensureMainThread` 调用约束；不要绕过。网络解析、缓存、历史/心跳上报等耗时工作异步执行，结果回主线程并验证 session/媒体身份仍有效。
- Player/PlayerView/Surface、listener、音频处理和会话资源必须按既有生命周期成对 attach/detach/release。退出播放页不能等待网络或上报，也不能阻塞详情/首页返回首帧。
- 保持点播、直播、字幕、弹幕、清晰度/线路切换、进度和历史上报的状态隔离；旧媒体的异步结果不得污染新媒体会话。
- 涉及 CDN failover、缓冲恢复、解码器 fallback、Surface 切换、清晰度选择或直播重连时，保留有节制且不含凭据的诊断信息，并提供终止条件，禁止无限重试。
- 弹幕遵守分段 Protobuf 优先、XML/游客 fallback 和有限预取的现有模型；seek、切集、切源和退出后要取消/丢弃旧分段，XML fallback 不得引发后续分段无限请求。
- 播放错误要区分可重试网络问题、线路/资源问题和设备解码能力问题，给出遥控器可操作的恢复路径；不能用永久 loading 隐藏终态。
- Debug Overlay 只服务调试场景，不进入 release 热路径，不持续制造昂贵采样、分配或日志。

## TV 性能、内存与设备兼容

- 把冷启动到首页可操作、首页 Tab 切换、网格快速滚动、详情返回和播放首帧视为关键性能路径；修改时保留 `AppPerformanceTracker` 和 Baseline Profile 的既有语义。
- 不在 Composable 热路径或每个 item 中做全量计算、同步 IO、反复创建 formatter/regex/JSON parser、大 bitmap 或无约束协程。
- 图片请求提供与卡片接近的目标尺寸，保留 placeholder/error，避免列表中加载原图；内存压力下遵守现有 `ComponentCallbacks2` 缓存清理策略。
- 高频焦点变化只更新必要的局部状态，避免整个页面/网格重组、adapter 全量刷新或同步预取。列表更新优先使用稳定 ID、payload/diff 和现有复用池。
- 不删除或随意移动启动任务。首屏非必要初始化继续延后到 first frame 之后；新增启动工作必须说明是否阻塞主线程和为何必须在该阶段执行。
- 不假设所有电视都有触摸屏、固定分辨率、相同按键码、充足内存或一致的硬解码支持。厂商特例必须隔离在 `system`/兼容层并经过能力或设备判断，不能改变通用 TV 行为。
- 修改 Manifest 时检查 Leanback launcher、banner、`touchscreen` 非必需声明、exported 组件和权限最小化；厂商辅助能力不得成为基本导航或播放的前置条件。
- Release 默认 ABI、R8、资源压缩、不可调试和签名策略以 Gradle 配置为准。没有明确任务和设备证据，不扩大 ABI、不关闭压缩、不添加宽泛 keep 规则。

## 测试与验证策略

优先把焦点决策、分页、fallback、状态归约等 Android 无关逻辑提炼为 policy/coordinator/state machine 并做单元测试；真实焦点树、按键分发、Activity/Player 生命周期和厂商差异再用 instrumentation/真机验证。不要用纯 JVM 测试声称已经证明真实 TV 焦点行为。

按改动范围从小到大运行：

```powershell
# 单个或同包 JVM 测试（优先）
.\gradlew.bat :app:testDebugUnitTest --tests "完整测试类名" --no-daemon --stacktrace

# 模块测试
.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat :network-core:testDebugUnitTest :settings-core:testDebugUnitTest --no-daemon --stacktrace

# Debug lint + unit tests + assemble
.\gradlew.bat tvVerification --no-daemon --stacktrace

# 已连接 TV/模拟器时
.\gradlew.bat tvUiRegression --no-daemon --stacktrace
```

- 只有改动触及 release/R8/资源/Manifest/ABI/播放器原生依赖时，才追加 `tvBuild` 或 `tvReleaseVerification`。
- release 任务需要有效 `keystore.properties`；缺少签名材料时不要伪造配置，明确报告未运行原因。Baseline Profile 任务需要兼容的已连接设备。
- 焦点/UI 改动至少设备验证：冷启动默认焦点、TopBar ↔ 内容、四向边界、快速连按/长按、分页/刷新、空/错/重试、弹层开关、Tab 切换、详情/播放器/设置返回。
- 播放改动至少验证：点播和直播首帧、暂停/恢复/seek、线路或清晰度切换、网络失败与恢复、播放结束、弹幕/字幕 fallback、Home/Back 后资源释放与焦点返回。
- 数据/插件改动至少验证：游客与登录态、refresh/append 竞态、插件启停/异常、旧数据兼容、进程重建或重新进入页面后的状态。
- 无设备时如实区分“代码/单测通过”和“TV 交互尚未验证”，不要用编译成功替代遥控器验收。

## 完成标准与输出

结束任务前检查：

- 改动落在正确层，未复制状态所有权或引入无关框架；
- D-Pad、确认、长按、Back、加载/空/错、刷新/分页和返回恢复均有确定行为；
- 没有新增主线程 IO、热路径全量计算、资源泄漏、无限重试或敏感日志；
- 新行为有最接近层级的回归测试，相关测试实际通过；
- Manifest、序列化/Room/Protobuf、插件 API 或 release 路径的连带影响已检查；
- `git diff` 只包含任务范围内的改动，没有覆盖用户工作。

最终报告必须包含：改了哪些文件与行为；为何符合 TV 客户端约束；实际运行的命令与结果；未运行的验证及原因；仍需真机验证的风险。报告事实，不把推测写成已验证结论。
