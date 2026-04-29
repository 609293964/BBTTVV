# BBTTVV

BBTTVV 是面向 Android TV 的第三方客户端，专为电视硬件打造的极致流畅体验。项目基于 Kotlin、Jetpack Compose、AndroidX TV Material、Media3 构建，重点优化了电视端遥控器 D-Pad 操作体验、首页多 Tab 导航性能、播放链路及多维度的调试信息，致力于实现流畅无卡顿的现代化 TV 应用。


焦点类问题排查重点：视频卡片快速上下、分页 append、数据刷新后焦点恢复，应按下方 TV 焦点问题排查流程采集输入、滚动、数据提交和焦点恢复时序。


已知BUG：1、动态页下拉，无法正常显示，会有和动态直播错位，         解决方案：在设置关闭动态页显示管理


       
         2、 焦点混乱：例如无法回到顶部菜单、无法返回、菜单遮挡     解决方案：退出app重新进入
## 应用预览

为了直观展示应用的界面设计与交互效果，以下是各核心场景的截图与功能说明：

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

## 核心特性
支持 4K / 1080P60 / HDR / Dolby Vision (需登录/大会员) 在TCL电视测试，可点亮
- **极致性能导航架构**：采用自定义的轻量级 `Box` 方案重构了顶部 Tab 导航和相关焦点管理。实现了零重组（Zero-recomposition）切换，显著降低 CPU/GPU 开销，杜绝了低端设备上的 UI 卡顿和焦点丢失问题。
- **完善的直播与点播播放器**：基于 Media3 ExoPlayer 构建的强大播放链路，解决各类直播流格式（如特定 FLV）的播放错误问题。具备完备的解码器回退与协商机制，确保视频内容稳定渲染。
- **硬核调试信息面板（Debug Overlay）**：在视频和直播播放页面中集成了详尽的调试信息面板，可通过遥控器快捷键唤出。实时显示：分辨率、帧率（FPS）、实时码率（Bitrate）、当前解码器内核（Codec）以及丢帧数（Dropped Frames），方便监控播放器性能状态。
- **为遥控器而生的交互**：
  - 支持使用 D-PAD 菜单键（Menu）一键刷新当前流媒体列表（推荐、热门、直播、动态等）。
  - 自动管理从顶部 Tab 栏到下方视频网格（Grid）的无缝焦点交接。
- **丰富的播放体验增强**：支持视频弹幕热力图展示，直观呈现高能片段；内置空降助手功能，可一键跳转至视频精彩时刻，大幅提升观影体验。
- **UI 与本地化保障**：彻底修复了部分界面（如弹幕设置、插件中心）的字符编码和乱码（Mojibake）问题，保障一致的专业视觉体验。
- **高效的内存管理**：重组了首页复杂 UI 的状态管理逻辑，通过生命周期感知设计在不同 Tab 切换时及时回收不再使用的 ViewModel，消除原有的“God Object”反模式，大幅降低后台内存占用。

## 操作指南

本应用针对电视遥控器进行了深度适配，支持以下快捷操作：

- **方向键（D-Pad）**：控制页面内焦点的上下左右移动。支持从顶部导航栏无缝下移至内容区，并在边缘提供弹回或预加载效果。
- **确认键（Center/OK）**：短按确认进入详情；长按可触发内容的更多操作。
- **菜单键（Menu）**：在首页列表等流媒体页面，按下菜单键可一键刷新当前内容列表。
- **返回键（Back/Escape/B）**：返回上一级页面或退出当前状态。
- **调试面板**：在视频或直播播放页面中，可通过遥控器快捷键唤出 **硬核调试信息面板（Debug Overlay）**，实时查看分辨率、帧率、码率及丢帧等性能状态。

## 技术栈

- Kotlin 2.x，Java 21，Android Gradle Plugin 8.x
- Jetpack Compose、AndroidX TV Material、Compose Navigation
- Media3 ExoPlayer、Room、DataStore、Retrofit、OkHttp、Coil

## 本地开发

开发环境要求：

- JDK 21
- Android SDK，Compile SDK 36
- Android Studio 最新稳定版或兼容 AGP 8.x 的版本

常用命令：

```bash
# 编译并安装 Debug 调试包到连接的电视或模拟器
./gradlew installDebug

# 运行 lint、单元测试和 release 构建验证
./gradlew tvVerification

# 构建 Release APK
./gradlew tvBuild

# 运行连接设备上的 UI smoke test
./gradlew tvUiRegression
```

Windows PowerShell 下可使用 `.\gradlew.bat` 替代 `./gradlew`。

### TV 焦点问题排查

遇到 Grid、Tab、播放器等 D-Pad 焦点乱跳时，可以临时加入 Debug-only 运行时日志，推荐统一使用
`BBTTVVGridFocus` 作为 logcat tag。排查完成后应删除临时日志点，避免常驻热路径。

建议记录以下信息：

- 输入事件：`KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT/CENTER/BACK`、`ACTION_DOWN/UP`、`eventTime`。
- 当前焦点：`rootView.findFocus()`、是否为 RecyclerView 本身、是否在当前焦点区域内部。
- Grid 状态：focused adapter position、visible range、scroll state、item count。
- 焦点意图：pending focus key/position、scroll parking target、dataset restore target、request token。
- 数据变化：submitList 前后数量、append/refresh generation、稳定 key 是否仍存在。

常用采集命令：

```bash
adb logcat -c
adb logcat -s BBTTVVGridFocus
adb shell input keyevent 20   # DPAD_DOWN
adb exec-out uiautomator dump /dev/tty > focus-ui.xml
```

分析时按时间线对齐“输入事件 -> parking/scroll -> 数据提交 -> 焦点恢复”。如果出现数据或布局变化后焦点被恢复到错误卡片，优先检查是否有旧子项焦点覆盖了 pending directional target，而不是先加延迟或循环 requestFocus。

## 上传前检查

仓库根目录已提供 `.gitignore`，用于排除 Gradle 构建产物、IDE 缓存、logcat / window dump、`local.properties` 以及签名文件。上传到 GitHub 前请确认不要提交以下本地文件：

- `local.properties`
- `keystore.properties`
- `*.jks` / `*.keystore`
- `logs/`、`build/`、`.gradle/`、`.kotlin/`

## CI

仓库包含 GitHub Actions 工作流 `.github/workflows/android-tv.yml`，在 push、pull request 和手动触发时使用 JDK 21 执行验证：

```bash
./gradlew tvVerification --no-daemon --stacktrace
```

CI 只负责验证，不进行签名发布、GitHub Release 或外部对象存储上传。

## 免责声明

本应用为第三方开源实现，仅用于个人学习、研究与技术交流，不得用于商业发行或盈利。项目中展示和访问的图片、视频、评论等业务数据版权归原权利方所有。使用、复制、分发或部署本项目所产生的任何账号、法律和合规风险由使用者自行承担。
