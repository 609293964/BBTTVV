# 贡献与仓库维护规范

本仓库以 `main` 为稳定主线。目标是让分支、提交、PR 和 Release 都能一眼看懂，并尽量保持历史可追溯。

## 分支

日常开发从最新 `main` 创建短生命周期分支：

- `feat/<topic>`：新功能
- `fix/<topic>`：缺陷修复
- `refactor/<topic>`：不改变外部行为的重构
- `perf/<topic>`：性能优化
- `docs/<topic>`：文档
- `test/<topic>`：测试
- `chore/<topic>`：构建、依赖、仓库维护
- `release/<version>`：仅用于确有必要的发布准备

不要长期保留已经合并的功能/修复分支。PR 合并确认无误后应删除源分支。

## 提交信息

新提交采用 Conventional Commits 风格：

```text
<type>(<scope>): <简短说明>
```

例如：

```text
fix(player): 修复切换账号后播放地址缓存复用
feat(plugin): 增加 JSON 规则资源上限
refactor(profile): 收敛异步请求 generation 管理
chore(repo): 统一仓库维护规范
```

避免使用 `1`、`update`、`test`、`noop` 等无法表达变更目的的提交标题。

推荐类型：`feat`、`fix`、`refactor`、`perf`、`test`、`docs`、`build`、`ci`、`chore`、`revert`。

## Pull Request

- 一个 PR 尽量只解决一个问题或一组强相关问题。
- PR 标题使用与提交相同的 `type(scope): summary` 风格。
- 修复 Issue 时在 PR 正文使用 `Closes #<number>`。
- 合并前至少完成与改动风险相匹配的测试或静态复核。
- 默认使用 **Squash merge**，让 `main` 上每个 PR 最终只留下一个语义清晰的提交。
- 合并完成后删除源分支。

除非发生凭据泄露等必须清除历史的安全事件，否则不要对已经公开的 `main` 执行 force-push 或历史重写。

## 版本与 Git 标签

历史上的数字标签和已有 Release 继续保留，避免破坏已发布 APK、下载链接和外部引用。从下一次正式发布开始统一使用语义化版本标签：

```text
vMAJOR.MINOR.PATCH
```

例如：`v1.30.0`、`v1.30.1`。

发布时应保证以下信息一致：

1. `version.properties` 中的 `versionName` / `versionCode`；
2. Git tag；
3. GitHub Release 标题；
4. APK 文件名。

推荐 APK 命名：

```text
BBTTVV-v1.30.0-arm64-v8a.apk
BBTTVV-v1.30.0-armeabi-v7a.apk
```

同一个 commit 不应创建多个含义不清或互相冲突的正式版本标签。

## Release 说明

Release 正文至少说明：

- 新功能；
- 重要修复；
- 兼容性或迁移说明；
- 已知问题（如有）。

避免使用“修复了一些问题但忘了是什么”这类无法帮助用户判断升级价值的描述。

## 仓库清理原则

仓库清理优先采用非破坏性方式：删除已合并分支、统一后续命名、保留已有公开 Release。已经进入 `main` 的旧提交即使标题不理想，也通常不为了“好看”而重写 SHA；可读性从后续提交开始改善。
