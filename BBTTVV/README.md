# BBTTVV

BBTTVV 是一个专门给 Android TV 做的第三方客户端，目标很简单：在电视机那点可怜的硬件配置上，把流畅度做到极致。

项目主体是用 Kotlin 写的，UI 框架全套上了 Jetpack Compose 和 AndroidX TV Material，播放器底座是 Media3。我们花了不少力气去死磕电视遥控器（D-Pad）的焦点逻辑，优化了首页多 Tab 切换和视频网格的滑动性能，希望能给大家一个顺滑、不卡顿的现代 TV 追剧体验。

> **⚠️ 已知问题**：视频卡片快速上下按的时候，焦点偶尔会乱跳，这个坑还在填。

## 看看效果图

<details>
<summary>点击展开/收起应用截图</summary>
<br>

<p align="center">
  <img src="docs/images/1.png" alt="首页截图1" width="600">
  <br>
  <img src="docs/images/2.png" alt="首页截图2" width="600">
  <br>
  <img src="docs/images/3.png" alt="详情页截图1" width="600">
  <br>
  <img src="docs/images/4.png" alt="详情页截图2" width="600">
  <br>
  <img src="docs/images/5.png" alt="播放页截图1" width="600">
  <br>
  <img src="docs/images/6.png" alt="播放页截图2" width="600">
  <br>
  <img src="docs/images/screenshot_preview_1.png" alt="首页推荐与导航" width="600">
  <br>
  <img src="docs/images/screenshot_preview_2.png" alt="应用交互界面" width="600">
  <br>
  <img src="docs/images/screenshot_preview_3.png" alt="焦点与详情" width="600">
</p>
</details>

## 做了些啥？

- **丝滑的顶部导航**：原生 Compose 的重组开销在老电视上太感人了，所以抄袭了一套轻量级的 `Box` 方案来处理顶部 Tab 和焦点。结果就是：切换 Tab 基本零重组，老旧设备上也不掉帧、不丢焦点。
- **能播，且稳**：基于 Media3 ExoPlayer，搞定了各种奇葩的直播流（比如某些不太标准的 FLV）。加了一套解码器降级和重试机制，尽量保证画面能出来。
- **硬核的“极客面板”**：按个遥控器快捷键，就能在播放页呼出 Debug Overlay。里面实时显示分辨率、FPS、码率、用的什么解码器，还有丢没丢帧。看视频卡了？点开看看是网卡了还是解码器拉跨了。
- **遥控器友好**：
  - 首页想刷点新的？直接按遥控器的 **菜单键（Menu）** 就行。
  - 从上面的 Tab 到下面的视频墙，焦点过渡非常自然。
- **告别乱码**：把以前弹幕设置、插件中心里经常出现的各种火星文和乱码问题全干掉了。
- **省点内存**：重写了首页的状态管理，切 Tab 的时候该扔的 ViewModel 赶紧扔，把以前臃肿的 God Object 拆散了，内存占用一下子少了一大截。

## 遥控器怎么按

- **方向键（上下左右）**：指哪打哪，支持边缘回弹。
- **确认键（OK）**：短按进详情，长按有彩蛋（更多操作）。
- **菜单键（Menu）**：在流媒体列表页面一键刷新。
- **返回键（Back）**：退一步或者关掉弹窗。
- **调试面板**：播放视频时按遥控器快捷键，各种极客数据尽收眼底。

## 用到的技术

- Kotlin 2.x, Java 21, AGP 8.x
- Jetpack Compose, AndroidX TV Material, Compose Navigation
- Media3 ExoPlayer, Room, DataStore, Retrofit, OkHttp, Coil

## 下一步计划（画个饼）

- **主题切换**：支持更换应用皮肤，适配不同光线下的视觉体验。
- **动态页可选关注列表**：让动态页可以自由选择并展示关注对象的内容，方便追踪更新。

## 想自己跑跑看？

**环境准备：**
- JDK 21
- Android SDK (Compile SDK 36)
- 最新版的 Android Studio（或者能跑 AGP 8.x 的版本）

**常用命令：**

```bash
# 连上电视或者模拟器，跑起来
./gradlew installDebug

# 看看代码规不规范，测试能不能过
./gradlew tvVerification

# 打个 Release 包
./gradlew tvBuild

# 跑一把 UI 自动化测试
./gradlew tvUiRegression
```
*(Windows 的兄弟们记得把 `./gradlew` 换成 `.\gradlew.bat`)*

**⚠️ 提交代码前注意：**
我们已经配好了 `.gitignore`，一般不会把乱七八糟的东西传上来。但大家 commit 前最好还是扫一眼，别把你的 `local.properties`、签名文件（`*.jks`, `*.keystore`）或者什么奇怪的日志传到 GitHub 上了。

## CI

项目里自带了 GitHub Actions (`.github/workflows/android-tv.yml`)。你每次 push 或者提 PR，CI 都会拿 JDK 21 帮你跑一遍验证：
```bash
./gradlew tvVerification --no-daemon --stacktrace
```
*注：这玩意只负责跑测试，不管打包发布。*


