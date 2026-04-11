# Miuix 对齐记录

最后更新：2026-04-11

## 背景

本仓库已经引入 `top.yukonga.miuix.kmp`，并在 `external/miuix/` vendored 了一份本地源码。

本页只记录基于本地源码核对后的结论，不依赖外部资料。

## 本地源码结论

- `Miuix` 不是“只换颜色”的封装，它有自己的主题对象、颜色槽位、文字样式和 smooth rounding 开关。
- `Miuix` 有独立的壳层和基础组件，包括 `TopAppBar`、`FloatingNavigationBar`、`TabRow`、`BasicComponent` / `Preference`。
- `MiuixTheme` 默认支持 G2 连续曲率平滑圆角；这一点是它和普通 `RoundedCornerShape` 的关键视觉差异。
- `Miuix` 默认文字体系不是 Material 3 token 的直接镜像，正文更接近 `17 / 16 / 14 / 13 / 11sp` 的分层。

主要源码参考：

- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/MiuixTheme.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/Colors.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/TextStyles.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/SmoothRounding.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/TopAppBar.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/NavigationBar.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/TabRow.kt`
- `external/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt`

## 对 BiliPai 当前实现的判断

当前 `Miuix` 变体的主要问题不是“颜色不够像”，而是“主题接管范围太窄”：

- 现状以 `Material ColorScheme -> Miuix Colors` 桥接为主。
- 大量页面仍然继续消费统一的 `MaterialTheme` token。
- `androidNativeVariant` 过去没有真正参与 typography / shapes / smooth rounding 这一层的全局决策。

结果就是：

- `Material 3` 和 `Miuix` 的颜色可切换。
- 但界面的骨相仍然接近同一套设计系统。

## 已落地的第一轮对齐

本轮先做低风险、全局收益高的 token 对齐：

- 为 `AndroidNativeVariant.MIUIX` 单独提供 Material typography 映射。
- 为 `AndroidNativeVariant.MIUIX` 单独提供更圆的 Material shapes。
- 让 `LocalCornerRadiusScale` 在 `Miuix` 变体下变大，而不是继续沿用更紧的 MD3 缩放。
- 让 `MiuixTheme.smoothRounding` 只在 `Miuix` 变体下开启。
- 修正 `ThemeController` 的 `remember` 依赖，避免 Miuix 颜色桥接对象切换后仍持有旧引用。

## 后续对齐顺序

优先级从高到低：

1. 首页顶栏和顶部分段控件优先切到更原生的 `Miuix` 组件语义。
2. 底栏继续减少项目内手搓外观，优先复用 `FloatingNavigationBar` 的节奏和层级。
3. 设置页列表项逐步收敛到 `BasicComponent` / `Preference` 语义，减少“iOS 列表壳 + Miuix 配色”的混合状态。
4. 只在必须时保留 Material 组件外观覆盖；优先让 `Miuix` 变体自身的 token 和组件说话。

## 非目标

本轮不追求：

- 一次性把所有页面完全替换成原生 `Miuix` 组件。
- 让 `iOS` / `MD3` / `Miuix` 三套视觉同时做大规模重构。
- 通过新增依赖解决风格问题。
