# AI Source Map / AI 事实导航

Verified path map for repository reading.
面向仓库阅读的已核实路径映射。

Maintained periodically, but still subject to freshness limits; reference only.
会定期维护，但仍存在时效性限制，仅供参考。

## Repository Paths / 仓库路径

| Path | Verified Note |
| --- | --- |
| `../../AI.txt` | AI compatibility entry / AI 兼容入口 |
| `../../llm.txt` | LLM compatibility entry / LLM 兼容入口 |
| `../../llms.txt` | canonical AI / LLM entry / 主 AI / LLM 入口 |
| `app/` | Android app module / Android 应用模块 |
| `baselineprofile/` | baseline profile module / Baseline Profile 模块 |
| `docs/wiki/` | wiki documents / Wiki 文档 |
| `docs/perf/` | performance notes / 性能记录 |
| `plugins/` | plugin files / 插件文件 |
| `scripts/` | shell scripts / Shell 脚本 |
| `settings.gradle.kts` | module declarations / 模块声明 |
| `build.gradle.kts` | root build config / 根构建配置 |
| `app/build.gradle.kts` | app build config / 应用构建配置 |

## Source Roots / 源码根路径

| Path | Verified Note |
| --- | --- |
| `app/src/main/java/com/android/purebilibili` | main source root / 主源码根路径 |
| `app/src/test/java/com/android/purebilibili` | unit test root / 单元测试根路径 |
| `app/src/androidTest/java` | instrumentation test root / 仪器测试根路径 |

## Primary Module Directories / 主要模块目录

| Path | Verified Note |
| --- | --- |
| `app/src/main/java/com/android/purebilibili/app` | app entry / 应用入口 |
| `app/src/main/java/com/android/purebilibili/core` | shared core / 公共核心层 |
| `app/src/main/java/com/android/purebilibili/data` | data layer / 数据层 |
| `app/src/main/java/com/android/purebilibili/domain` | domain layer / 领域层 |
| `app/src/main/java/com/android/purebilibili/feature` | feature layer / 功能层 |
| `app/src/main/java/com/android/purebilibili/navigation` | navigation layer / 导航层 |

## Task-to-File Map / 任务到文件映射

| Task | File |
| --- | --- |
| AI / LLM entry / AI / LLM 入口 | [`../../llms.txt`](../../llms.txt) |
| AI compatibility alias / AI 兼容入口 | [`../../AI.txt`](../../AI.txt) |
| LLM compatibility alias / LLM 兼容入口 | [`../../llm.txt`](../../llm.txt) |
| project overview / 项目总览 | [`../../README.md`](../../README.md) / [`../../README_EN.md`](../../README_EN.md) |
| release changes / 版本变更 | [`../../CHANGELOG.md`](../../CHANGELOG.md) |
| architecture / 架构 | [`ARCHITECTURE.md`](./ARCHITECTURE.md) |
| QA or regression / QA 与回归 | [`QA.md`](./QA.md) |
| release workflow / 发布流程 | [`RELEASE_WORKFLOW.md`](./RELEASE_WORKFLOW.md) |
| structure constraints / 结构约束 | [`../../STRUCTURE_GUIDELINES.adoc`](../../STRUCTURE_GUIDELINES.adoc) |
| JSON plugin development / JSON 插件开发 | [`../PLUGIN_DEVELOPMENT.md`](../PLUGIN_DEVELOPMENT.md) |
| native plugin development / 原生插件开发 | [`../NATIVE_PLUGIN_DEVELOPMENT.md`](../NATIVE_PLUGIN_DEVELOPMENT.md) |
| plugin examples / 插件示例 | [`../../plugins/samples/`](../../plugins/samples/) |
| community plugins / 社区插件 | [`../../plugins/community/README.md`](../../plugins/community/README.md) |

## Source Priority / 事实优先级

1. Source code and build files
1. 源码与构建文件
2. [`../../CHANGELOG.md`](../../CHANGELOG.md)
2. [`../../CHANGELOG.md`](../../CHANGELOG.md)
3. Files under `docs/wiki/`
3. `docs/wiki/` 下的文件
4. [`../../README.md`](../../README.md) and [`../../README_EN.md`](../../README_EN.md)
4. [`../../README.md`](../../README.md) 与 [`../../README_EN.md`](../../README_EN.md)
5. Files under `docs/plans/`
5. `docs/plans/` 下的文件

## Document Freshness / 文档时效

- `app/build.gradle.kts` currently declares `versionName = "7.3.2"` and `versionCode = 134`
- `app/build.gradle.kts` 当前声明 `versionName = "7.3.2"`、`versionCode = 134`
- `README.md` and `README_EN.md` header text: 2026-04-01 / v7.3.2
- `README.md` 与 `README_EN.md` 页头：2026-04-01 / v7.3.2
- `docs/wiki/README.md` and `docs/wiki/AI.md` refreshed: 2026-04-01
- `docs/wiki/README.md` 与 `docs/wiki/AI.md` 最近刷新：2026-04-01
- `CHANGELOG.md`, `README.md`, and `README_EN.md` align with `versionName = "7.3.2"` in `app/build.gradle.kts`
- `CHANGELOG.md`、`README.md` 与 `README_EN.md` 已与 `app/build.gradle.kts` 中的 `versionName = "7.3.2"` 对齐
- `AI.txt` and `llm.txt` are compatibility aliases that mirror the current AI entry guidance
- `AI.txt` 与 `llm.txt` 是当前 AI 入口说明的兼容别名
- Use code and `CHANGELOG.md` to verify release-specific answers.
- 涉及具体版本发布答案时，使用代码与 `CHANGELOG.md` 共同核验。

## Constraints / 约束

- `docs/plans/*.md` are planning records, not default evidence.
- `docs/plans/*.md` 是计划记录，不是默认事实证据。
- Current behavior must be verified in Kotlin or Gradle files.
- 当前行为必须在 Kotlin 或 Gradle 文件中核验。
- If a linked path changes, update this file after the tree changes.
- 如果链接路径变更，应在仓库树变更后更新本文件。
