# BBTTVV 上游同步报告（2026 W30）

## 基线与范围

- 目标起始 SHA：`2101f89ddd7ac0e484af03464b98b70937f6bf35`
- 工作分支：`chore/upstream-port-2026w30`
- BiliPai 审计范围：`36739a9dde2de863fc60849c1789938b2ece11f6..e4f188deca87f6610e217a9b0ea4fd7ce6da1ee4`，窗口内 205 个提交。
- blbl 审计范围：`56155206ce35efaf63492ab0c563c8f6ae59f63b..cd09bd903e6d0c22f5986580c2105796d8df6471`，窗口内 27 个提交。
- 完整逐提交结果见 `upstream-port-manifest.json`。
- 清单分类汇总：`PORT=13`、`ALREADY_PRESENT=9`、`NOT_APPLICABLE=7`、`DEFER=111`、`REJECT=92`。
- 未执行 merge、rebase 或 cherry-pick；所有改动均按 BBTTVV 的 TV 架构做语义移植。

## P0 结果

| 项目 | 结论 | 行为 |
|---|---|---|
| P0-01 编码识别 | PORT | 统一识别 `hev*` / `hvc*`、AVC、AV1、Dolby Vision，DASH 选择复用同一分类。 |
| P0-02 高规格画质回退 | ALREADY_PRESENT | 已有 `127/126/125/120/116/112/80` 有界、去重回退链。 |
| P0-03 DASH 编码偏好 | ALREADY_PRESENT | 先按画质分组，再按设备解码能力和编码偏好选择。 |
| P0-04 稳定 DASH 默认值 | ALREADY_PRESENT | 本地 MPD 仅在音频直通开启时使用，默认关闭。 |
| P0-05 SurfaceView/HDR | ALREADY_PRESENT | Media3 `PlayerView` 未覆盖为 TextureView。 |
| P0-06 弹幕重绑定 | ALREADY_PRESENT | payload、attach、viewport、位置和速度均参与幂等同步。 |
| P0-07 覆盖层焦点 | PORT | 互动视频、弹幕投票和评论图片查看器使用唯一 modal owner；异步互动层关闭后恢复播放器 Surface，图片查看器关闭后保留评论侧栏并恢复来源评论。 |
| P0-08 起始位置续播 | PORT | DASH、合并流、多段流和 URL 在 `prepare` 前使用 Media3 initial-position API。 |
| P0-09 实际/预览进度 | ALREADY_PRESENT | 实际播放位置与 nullable scrub preview 已分离。 |
| P0-10 临时长按倍速 | NOT_APPLICABLE | 目标没有临时长按倍速入口。 |

## P1 结果

| 项目 | 结论 | 实现或不适用依据 |
|---|---|---|
| P1-01 PGC 独立默认画质 | PORT | `PlayerSettingsStore` 新增 nullable PGC 偏好；未设置时继承普通视频，显式设置后普通视频与 PGC 互不覆盖；首播仍经过现有登录/VIP 降级策略。 |
| P1-02 严格自定义 CDN | PORT | 设置页新增主机与默认关闭的严格开关；仅允许 `bilivideo.com` 域名。开启后只保留重写候选，并移除原始视频/音频 fallback；失败信息包含规则、URL 类型和原因，不含 Cookie/token/完整 URL。 |
| P1-03 字幕 gRPC 兜底 | NOT_APPLICABLE | 当前播放器没有消费 `PlayerInfo.subtitle` 的字幕轨道、渲染或控制链；仅有未接线的解析策略。单独增加 gRPC 发现会成为不可达死代码，因此未引入 Channel/proto/依赖。 |
| P1-04 收藏/稍后看后台队列 | NOT_APPLICABLE | 当前播放器没有收藏夹或稍后看来源队列模型；点击条目直接打开目标详情/播放器，自动续播只使用分 P 和相关推荐。没有可后台补页的队列所有者。 |
| P1-05 动态转发评论 | NOT_APPLICABLE | 当前动态能力是 Feed，没有动态详情评论面板、转发评论目标或评论分页状态机。 |
| P1-06 直播 SC | PORT | 从现有直播 WebSocket 解析 `SUPER_CHAT_MESSAGE`、`SUPER_CHAT_MESSAGE_JPN` 和 `SUPER_CHAT_MESSAGE_DELETE`；独立有界 FIFO、去重、过期和最长 10 秒显示，不进入普通弹幕轨道。叠层不可聚焦；隐私模式隐藏用户名。未新增鉴权。 |
| P1-07 TV 焦点回归 | PORT / ALREADY_PRESENT | 搜索提交完成后，有结果进入首项，无结果停在分类行并可向上回输入框；设置弹窗按稳定 key 回来源行。评论卡片只有 Surface 可聚焦；顶部栏继续由带 stable ID 的 RecyclerView 管理。删除后相邻项恢复沿现有稳定 key/位置策略。 |
| P1-08 更多倍速/字幕快捷键 | PARTIAL | 倍速菜单扩展为 `0.5x` 至 `2.75x` 的 10 个有界选项；字幕快捷键因目标没有字幕控制链而不适用。 |

## P2 核心三项结果

| 项目 | 结论 | 实现 |
|---|---|---|
| P2-01 动态增量刷新 | PORT | 首包 `update_num` 作为增量目标，按接口原始 item 数累计；不可渲染项不再导致提前停止。仅在 `has_more=true`、offset 非空且前进、未收齐更新且未达到 10 页上限时继续。更新基线只由首包推进，后续页不能覆盖。 |
| P2-02 首页请求抢占 | PORT | `HomeFeedRequestOwner` 统一持有推荐流 Job、request id 和请求类型。refresh 会取消并等待 append/旧 refresh，只有最新 id 能提交列表、loading、错误及“今日看点”重建；重复 append 仍去重，取消不展示错误。上游封面尺寸语义已由现有固定 `480×270` 请求覆盖，本轮未重复修改。 |
| P2-03 评论图片与 TV 查看器 | PORT | 详情评论、回复页显示最多 3 张不可聚焦缩略图及 `+N`，播放器窄侧栏显示首图和数量。图片请求使用有效原始比例，否则回退 `16:9`；缩略图最长边 480px，全屏按视口请求且不超过 `1920×1080`。全屏查看器左右不循环、边界不逃焦，确认/Back 关闭；长按确认只触发一次并抑制 key-up 短按。播放器将查看器设为最高优先级 modal owner，关闭后保留评论侧栏并按稳定评论 key 恢复。 |

## 本轮主要文件与行为

- 播放与画质：
  - `app/src/main/java/com/bbttvv/app/data/model/VideoQuality.kt`
  - `app/src/main/java/com/bbttvv/app/data/model/response/VideoResponse.kt`
  - `app/src/main/java/com/bbttvv/app/core/player/PlayerMediaSourceCoordinator.kt`
  - `app/src/main/java/com/bbttvv/app/core/player/PlayerMediaStartPolicy.kt`
  - `app/src/main/java/com/bbttvv/app/core/store/player/PlayerSettingsStore.kt`
  - `app/src/main/java/com/bbttvv/app/feature/video/viewmodel/PlaybackLoadCoordinator.kt`
- 严格 CDN：
  - `app/src/main/java/com/bbttvv/app/core/store/SettingsManager.kt`
  - `app/src/main/java/com/bbttvv/app/feature/plugin/CdnRegionPolicy.kt`
  - `app/src/main/java/com/bbttvv/app/feature/video/usecase/VideoPlaybackUseCase.kt`
  - `app/src/main/java/com/bbttvv/app/feature/video/viewmodel/PlaybackQualityController.kt`
  - `app/src/main/java/com/bbttvv/app/feature/settings/SettingsScreen.kt`
- 直播 SC：
  - `app/src/main/java/com/bbttvv/app/feature/live/LiveDanmakuMessageParser.kt`
  - `app/src/main/java/com/bbttvv/app/feature/live/LiveSuperChatQueue.kt`
  - `app/src/main/java/com/bbttvv/app/feature/live/LivePlayerViewModel.kt`
  - `app/src/main/java/com/bbttvv/app/feature/live/LivePlayerScreen.kt`
- TV 焦点和倍速：
  - `app/src/main/java/com/bbttvv/app/feature/search/SearchScreen.kt`
  - `app/src/main/java/com/bbttvv/app/core/store/PlaybackSpeedSupport.kt`
  - `app/src/main/java/com/bbttvv/app/feature/settings/SettingsCatalog.kt`
- P2 动态与首页并发：
  - `app/src/main/java/com/bbttvv/app/data/repository/DynamicFeedFetchPolicy.kt`
  - `app/src/main/java/com/bbttvv/app/data/repository/DynamicRepository.kt`
  - `app/src/main/java/com/bbttvv/app/ui/home/HomeFeedRequestOwner.kt`
  - `app/src/main/java/com/bbttvv/app/ui/home/HomeViewModel.kt`
- P2 评论图片：
  - `app/src/main/java/com/bbttvv/app/ui/components/CommentPictures.kt`
  - `app/src/main/java/com/bbttvv/app/ui/detail/DetailCommentsSection.kt`
  - `app/src/main/java/com/bbttvv/app/ui/detail/CommentRepliesScreen.kt`
  - `app/src/main/java/com/bbttvv/app/ui/detail/DetailScreen.kt`
  - `app/src/main/java/com/bbttvv/app/feature/video/screen/PlayerCommentsPanel.kt`
  - `app/src/main/java/com/bbttvv/app/feature/video/screen/PlayerExclusiveOverlayPolicy.kt`
- 审计产物：
  - `scripts/generate_upstream_port_manifest.ps1`
  - `upstream-port-manifest.json`

这些实现保持 TV 约束：设置与播放器弹层均有稳定返回焦点；评论图片不增加卡片内部焦点，查看器形成封闭焦点域；SC 是不可聚焦的独立显示层；CDN 严格模式默认关闭且没有无限重试；首页取消是正常终止并保留旧内容；没有引入第二播放器、手机 UI 框架、长期 UI 层网络 Channel 或主线程 IO。

## 自动化验证

- P1 定向 JVM 测试通过：`LiveSuperChatTest`、`CdnRegionPolicyTest`、`SettingsManagerTest`、`PlaybackContentQualityPolicyTest`、`SettingsCatalogTest`、`PlaybackSpeedSupportTest`。
- P2 定向 JVM 测试通过：`DynamicFeedFetchPolicyTest`、`DynamicPaginationRegistryTest`、`HomeFeedRequestOwnerTest`、`HomeFeedControllerRepositoryTest`、`CommentPicturePolicyTest`、`PlayerOverlayActionsTest`。
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace` 通过：120 个测试套件、513 项测试，0 failure、0 error、0 skipped。
- `.\gradlew.bat tvVerification --no-daemon --stacktrace` 通过：Debug APK、单测与 lint 均完成；lint 报告为 92 warnings、4 hints，无阻断错误。
- `.\gradlew.bat tvUiRegression --no-daemon --stacktrace` 在 `BBTTVV_TV_API36`（API 36 Android TV AVD）通过：15 项测试，0 失败、2 项因测试前置条件跳过。
- `git diff --check` 通过；仅提示现有 `dm_web_view.proto` 将来被 Git 写入时可能发生 LF → CRLF 转换，没有空白错误。
- `upstream-port-manifest.json` 已用 PowerShell JSON 解析复核，共 232 项：`PORT=13`、`ALREADY_PRESENT=9`、`NOT_APPLICABLE=7`、`DEFER=111`、`REJECT=92`。

未运行 `tvBuild` / `tvReleaseVerification`：本轮未修改 release、R8、Manifest、ABI 或原生播放器依赖，按仓库验证策略无需追加 release 路径。未运行实体 TV 验证；当前 UI 结果来自 Android TV 模拟器。模拟器没有可稳定复现的“含图片评论 + 登录/网络状态”测试 fixture，因此没有把真实评论图片加载、长按打开和失败占位的人工浏览写成已验证结论；自动化只证明图片映射/尺寸、按键状态机、查看器边界和 modal owner 策略。

## 真机风险

- 实体电视上的 AVC/HEVC/HVC1/AV1、SDR/HDR10/Dolby Vision 和高画质降级尚未验证。
- 严格 CDN 需要用真实可用 CDN 主机、DASH 音视频和分段流分别验证失败文案及无原始线路回退。
- 直播 SC 需要真实房间验证消息、日文消息、删除命令、连续队列、遮挡和隐私模式；当前自动化只证明解析/队列策略。
- 搜索提交、设置弹窗、删除相邻项和顶部栏焦点仍需实体遥控器快速连按、长按及厂商键码验证。
- 评论图片仍需实体电视验证厂商确认键 repeat/long-press 差异、弱网加载失败、4K 与低内存设备的图片峰值，以及详情/回复页/播放器侧栏三条链路的来源焦点恢复。
- 字幕、收藏播放队列和动态评论不是“已实现但未验证”，而是因当前目标架构没有对应可达能力而明确不适用。

## 工作树与回滚

开始时工作树已有大量未提交且与播放器、弹幕、Profile 焦点重叠的用户改动；本轮按用户指示直接在当前工作区继续，未覆盖或还原这些改动，也未创建提交。回滚时应按文件和具体 hunk 区分本轮改动与原有用户改动，不能整体 reset。
