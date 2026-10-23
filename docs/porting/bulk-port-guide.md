# Forge 1.20.1 维护指南

长期分支为 `mc1.20.1-forge`，开发入口为 `StardewCraft-1.20.1-Forge-bulk`。
共同规则见 [多版本维护与发布](../forge-1.20.1-migration-plan.md)。

## 文件职责

- `src/main/java`：从主线移植的业务源码及必要的平台调用适配。
- `src/port/java`、`src/port/resources`：Forge 1.20.1 专属实现及资源，不能被主线覆盖。
- `scripts/port/`：源码/资源转换、三方同步与平台验证工具。
- `scripts/port/main-sync-state.txt`：上次完成同步的主线来源；不能手改来掩盖未合入内容。
- `parity-approved.tsv`：内容比对的已批准差异，仍由校验器读取，不能当作过程报告删除。

## 同步

先核对两侧 Git 状态和来源范围，再运行 `sync_from_main.py --dry-run` 审查快照及冲突。
开发可用 tree 快照，发布应使用明确来源提交：

```sh
python3 scripts/port/sync_from_main.py --snapshot <source-commit-or-tree>
```

转换后的旧来源作为三方基线，保留 Forge 手改与独有文件。发生冲突时，修复文本及二进制冲突，
检查后用 `--complete-snapshot <same-source>` 完成该次同步。脚本 lint 通过只表示转换检查完成，
不表示编译、行为或发布验证通过。避免直接调用默认的创建提交模式绕过提交授权。

平台适配保留网络方向与线程、事件取消与阶段、ItemStack NBT、资源目录格式及 Mixin 调用约定。
Apothic/Placebo、容器格子分派、Oculus/Embeddium 等 Forge 专属兼容不得被同名主线文件覆盖。
FLOW/BOLT 纹饰及模板、鱼塘水滴水石灌注为既有版本豁免，不扩展为其他功能的静默删除。

## 验证

Java 17 下开发编译使用 `./gradlew classes`；定向回归按改动选择。
发布完整执行本分支 `.github/workflows/build.yml`，包含重映射/内嵌依赖、GUI Mixin、兼容工具、
GameTest 服务端保存/退出及附属示例。不要沿用旧阶段的固定通过数量判断当前版本。
客户端、Oculus 光影、水面、HUD 和进出门仍需要实际版本组合的游戏验证。

历史行为差异参考 [平台差异记录](bulk-port-gaps.md)；其中旧实测数字不代表当前版本已验收。
