# BBTTVV

BBTTVV 是面向 Android TV 的第三方客户端，基于 Compose 及原生安卓开发。针对电视遥控器 D-Pad 操作进行了深度适配，采用 Compose + AndroidView/RecyclerView 混合架构以保证焦点稳定性和滚动流畅度。

纯 AI 开发，焦点乱跳及卡片按钮错位在逐步修复中。先支持常用功能，直播、番剧等还在迭代，可能出现播放不了、闪退等情况。

已知 BUG：
- 不显示弹幕：点一下快进

## 核心特性

- **高清播放**：支持 4K / 1080P60 / HDR / Dolby Vision（需登录 / 大会员），在 TCL 电视测试可点亮
- **推荐单同步**：同步上游推荐单，找不到喜欢的视频可开启推荐单功能
- **空降助手**：集成 SponsorBlock，自动跳过恰饭片段，支持空间助手数据库同步
- **弹幕系统**：Protobuf 分段弹幕 + XML fallback + 游客 fallback，支持弹幕过滤、样式增强、关键词 / 正则屏蔽
- **评论查看**：支持视频详情页和播放器内查看评论，建议在视频卡片中查看以避免详情页卡顿
- **视频热力图**：类似 YouTube 的播放热度图效果
- **音量均衡**：手动调整应用音量，避免其他 App 声音很小、打开本应用突然很大声
- **插件系统**：支持 FeedPlugin / PlayerPlugin / DanmakuPlugin / PlaybackCdnPlugin / RecommendationPluginApi / JsonRulePlugin 六种插件类型，内置赞助商跳过、广告过滤、弹幕增强、今日观看、CDN 区域选择
- **CDN 区域选择**：通过 CdnRegionPlugin 选择最优 CDN 节点
- **Debug Overlay**：播放页可唤出调试面板，实时查看分辨率、帧率、码率及解码器状态

## 应用预览

<p align="center">
  <img src="docs/images/1.png" alt="首页推荐与导航" width="600">
  <br>
  <em>首页多 Tab 导航与视频网格：支持 D-Pad 顺滑浏览，流畅展示海量推荐内容。</em>
  <br><br>
  <img src="docs/images/2.png" alt="分区动态展示" width="600">
  <br>
  <em>分区动态展示：快速浏览各个分区的热门视频与最新动态。</em>
  <br><br>
  <img src="docs/images/3.png" alt="视频详情页" width="600">
  <br>
  <em>视频详情页：提供丰富的视频介绍与相关视频推荐，焦点操作清晰稳定。</em>
  <br><br>
  <img src="docs/images/4.png" alt="播放界面与调试信息" width="600">
  <br>
  <em>播放界面与硬核调试面板：实时监控分辨率、帧率、码率及解码器等播放状态。</em>
  <br><br>
  <img src="docs/images/5.png" alt="设置与高级选项" width="600">
  <br>
  <em>播放设置与高级选项：支持清晰度切换、解码器选择及弹幕等设置，深度适配遥控器。</em>
  <br><br>
  <img src="docs/images/6.png" alt="热力图与空降助手" width="600">
  <br>
  <em>特色播放功能：支持视频弹幕热力图展示，集成空降助手帮助快速跳转高能时刻。</em>
</p>

## 操作指南

本应用针对电视遥控器进行了深度适配，支持以下快捷操作：

- **方向键（D-Pad）**：控制页面内焦点的上下左右移动。支持从顶部导航栏无缝下移至内容区，并在边缘提供弹回或预加载效果。
- **确认键（Center/OK）**：短按确认进入详情；长按可触发内容的更多操作。
- **菜单键（Menu）**：在首页列表等流媒体页面，按下菜单键可一键刷新当前内容列表。
- **返回键（Back/Escape/B）**：返回上一级页面或退出当前状态。
- **调试面板**：在视频或直播播放页面中，可通过遥控器快捷键唤出 **硬核调试信息面板（Debug Overlay）**，实时查看分辨率、帧率、码率及丢帧等性能状态。

## 技术栈

- Kotlin 2.3.10 / Java 21 (JVM Target)
- Android Gradle Plugin 8.13.2
- compileSdk 36 / minSdk 26 / targetSdk 35
- Jetpack Compose (BOM 2025.12.00) / AndroidX TV Material 1.0.0
- Compose Navigation 2.9.4
- Media3 ExoPlayer 1.10.0（含 DASH / HLS / OkHttp DataSource）
- Retrofit 2.9.0 / OkHttp 4.12.0 / kotlinx-serialization-json 1.6.3
- Room 2.8.4 / DataStore 1.2.1
- Coil 2.7.0
- Protobuf 3.25.3 (lite 模式)
- DanmakuRenderEngine v0.1.0（字节跳动）
- ZXing Core 3.5.3 / Brotli 0.1.2

## 项目架构

项目采用分层架构，依赖注入采用手动 DI（`AppContainer` 单例）：

| 层级 | 包路径 | 职责 |
|------|--------|------|
| 入口层 | `app/` | Application、启动编排 |
| UI 层 | `ui/` | Compose 页面、焦点协调、通用组件 |
| 功能层 | `feature/` | video / live / profile / search / settings / login / plugin / publisher |
| 导航层 | `navigation/` | Compose Navigation 路由和状态 |
| 数据层 | `data/` | Repository、API 响应模型、数据库 |
| 核心层 | `core/` | 网络、播放器引擎、插件系统、存储、分页状态机 |
| 领域层 | `domain/` | UseCase |

模块结构：

- `:app` — 主应用模块
- `:baselineprofile` — Baseline Profile 生成模块
- `:settings-core` — 设置核心库模块
- `:network-core` — 网络核心库模块

## 本地开发

开发环境要求：

- JDK 21
- Android SDK，compileSdk 36
- Android Studio 最新稳定版或兼容 AGP 8.13.x 的版本

### 构建命令

```bash
# 验证（lint + 单元测试 + 构建）
./gradlew tvVerification --no-daemon --stacktrace

# Release 构建（使用仓库中已有 Baseline Profile，不要求连接设备）
./gradlew tvBuild --no-daemon --stacktrace

# 每次 Release 构建会自动更新 version.properties：
# versionCode 递增 1，versionName 按 0.01 递增

# Release 验证
./gradlew tvReleaseVerification --no-daemon --stacktrace

# 正式性能发包：先刷新 TV Baseline Profile，再把最新 profile 打进 release APK（需连接设备或模拟器）
./gradlew tvProfiledRelease --no-daemon --stacktrace

# 安装到设备
./gradlew installDebug
./gradlew tvInstall                                     # installRelease

# UI 回归测试（需连接设备）
./gradlew tvUiRegression

# 仅生成 Baseline Profile（需连接设备或模拟器）
./gradlew tvBaselineProfile
```

Release ABI 默认为 `arm64-v8a`，可通过 Gradle 属性覆盖：

```bash
./gradlew tvBuild -Pbbttvv.releaseAbi=arm64-v8a,armeabi-v7a
```

### Baseline Profile 维护

`:baselineprofile` 模块会通过 TV 遥控器路径采集冷启动、首页 Tab 切换、推荐/热门网格滚动、搜索、个人页入口和播放首帧路径。生成结果会保存到 `app/src/release/generated/baselineProfiles/`，正式 release 前优先运行：

```bash
./gradlew tvProfiledRelease --no-daemon --stacktrace
```

如果当前机器没有连接 Android TV / 模拟器，可先用 `tvBuild` 完成普通 release 构建，并在有设备的 CI 或发版机上补跑 `tvProfiledRelease`。涉及首页、Compose Navigation、播放器首帧、弹幕首屏或启动流程的改动，应重新生成并提交最新 Baseline Profile。

### TV 焦点问题排查

遇到 Grid、Tab、播放器等 D-Pad 焦点乱跳时，可以临时加入 Debug-only 运行时日志，推荐统一使用 `BBTTVVGridFocus` 作为 logcat tag。排查完成后应删除临时日志点，避免常驻热路径。

建议记录以下信息：

- 输入事件：`KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT/CENTER/BACK`、`ACTION_DOWN/UP`、`eventTime`
- 当前焦点：`rootView.findFocus()`、是否为 RecyclerView 本身、是否在当前焦点区域内部
- Grid 状态：focused adapter position、visible range、scroll state、item count
- 焦点意图：pending focus key/position、scroll parking target、dataset restore target、request token
- 数据变化：submitList 前后数量、append/refresh generation、稳定 key 是否仍存在

详细的焦点架构约定、插件系统、播放器约定等开发约束请参考 [AGENTS.md](AGENTS.md)。

## 上传前检查

仓库根目录已提供 `.gitignore`，用于排除 Gradle 构建产物、IDE 缓存、logcat / window dump、`local.properties` 以及签名文件。上传到 GitHub 前请确认不要提交以下本地文件：

- `local.properties`
- `keystore.properties`
- `*.jks` / `*.keystore`
- `logs/`、`build/`、`.gradle/`、`.kotlin/`

---

本应用为第三方开源实现，仅用于个人学习、研究与技术交流，不得用于商业发行或盈利。项目中展示和访问的图片、视频、评论等业务数据版权归原权利方所有。使用、复制、分发或部署本项目所产生的任何账号、法律和合规风险由使用者自行承担。
