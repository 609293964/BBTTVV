# BiliPai Wiki

最后更新：2026-04-01（按当前 main 分支结构校对）

## 目录

- [功能矩阵](FEATURE_MATRIX.md)
- [架构说明](ARCHITECTURE.md)
- [AI 导航指南](AI.md)
- [Miuix 对齐记录](MIUIX_ALIGNMENT.md)
- [发布流程](RELEASE_WORKFLOW.md)
- [QA 测试手册](QA.md)
- [插件开发指南（JSON）](../PLUGIN_DEVELOPMENT.md)
- [插件开发指南（原生）](../NATIVE_PLUGIN_DEVELOPMENT.md)

## 维护约定

每次 Release 至少同步以下内容：

1. `CHANGELOG.md` 新版本段落
2. `README.md` / `README_EN.md` 的 Latest 与 Roadmap
3. 本 Wiki 的功能矩阵、架构、QA 与发布流程
4. 若 `app/build.gradle.kts` 的 `versionName` 已领先 `CHANGELOG.md`，需先补齐发布文档或明确说明仍是主线未同步状态
5. 若调整了 AI 入口或文档优先级，需同步 `AI.txt`、`llm.txt`、`llms.txt` 与 `docs/wiki/AI.md`

## 快速入口

- Android 主代码：`app/src/main/java/com/android/purebilibili`
- 测试代码：`app/src/test/java/com/android/purebilibili`
- 版本配置：`app/build.gradle.kts`
- 发布日志：`CHANGELOG.md`
- AI 入口：`llms.txt`
- 兼容别名：`AI.txt` / `llm.txt`
