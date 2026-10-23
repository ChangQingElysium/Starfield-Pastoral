# 多版本维护与发布

## 维护线

| 分支 | Minecraft / 加载器 | 开发入口 |
| --- | --- | --- |
| `main` | 1.21.1 NeoForge；同目录 `fabric/` 构建 Fabric | `StardewCraft` |
| `mc1.20.1-forge` | 1.20.1 Forge | `StardewCraft-1.20.1-Forge-bulk` |

Fabric 是同仓库的平台子项目，不另开长期功能分支。共享玩法和资源以主线为源，
Fabric 维护加载器接入、Mixin、来源覆盖及验证；生成源码留在 `fabric/build/`，禁止手改。
Forge 因 Minecraft API、Java 和资源格式不同保留独立维护线，不能整树覆盖或直接合并主线。

旧的 `1.20.1-forge`、`1.20.1-forge-bulk` 是历史名称，不是新的开发入口。
任务分支使用 `codex/<task>`，合并并核对无独有提交后删除；历史版本由标签保存。
删除分支前确认没有工作树占用、开放 PR 或独有改动，不能强删未合并成果。

## 共享改动的同步

1. 先形成明确来源及范围；公共逻辑优先在 `main` 修改。平台独有问题在对应实现修复。
2. 同一主线改动检查 NeoForge 与 Fabric 两套构建。Fabric 覆盖文件的来源 SHA 变化必须重新审查，不能只刷新 SHA 来绕过差异。
3. Forge 使用 `scripts/port/sync_from_main.py` 做三方移植；发布以明确提交为来源，
   同步点由该分支的 `scripts/port/main-sync-state.txt` 记录。开发用 tree 快照不可冒充公开发布提交。
4. 保留 Forge 的 `src/port`、网络/NBT/资源转换、Mixin 及专属兼容修复；冲突逐项解决。
5. 在 PR 或发布说明用三行记录平台、来源/目标提交、已同步或不适用、验证和未完成的实机项。
   不再为每轮同步新增一篇仓库内的日期报告。源码同步、构建通过、客户端验收分别记录。

## 发布

版本号来自各维护线的 `gradle.properties`。一次三端发布须记录两条分支的准确提交，
主线标签使用 `v<版本>`，Forge 使用 `v<版本>-forge`，一个 GitHub Release 汇集三端安装包及 SHA256。
标签不移动，已发布附件不静默替换；修复包递增 fix。

候选提交必须在干净检出中执行各自 `.github/workflows/build.yml` 的全部命令，
推送后等待对应 Actions 完成。维护文档和文件收录整理独立提交，不夹带未验收功能。
玩家安装文件放 Releases，CI 日志和报告放 Actions artifacts；两者都不提交进源码树。

## 公开文件边界

保留正式源码、运行资源、构建必需的原始模型、锁定依赖、可复现验证、API 和维护文档。
诊断转储、报价、对话记录、逐轮验收流水账、测试存档、截图序列、本机路径绑定的研究脚本留本地。
是否保留按消费者判断，不能按体积或文件名删除母本、正式地图与测试夹具。

执行 `python3 compatibility/check_repository_contents.py` 检查 Git 索引；CI 执行相同检查。
`.gitignore` 不会自动取消已跟踪文件，强制添加忽略文件也会被此门禁拦截。
移出公开范围前在本地历史归档保存原路径、原提交和 SHA256；本地归档不是远端备份。

源码与母本职责另见 [工作区规则](workspace-layout.md)。
