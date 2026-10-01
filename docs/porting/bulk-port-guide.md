# 1.20.1 Forge 整体迁移工作指南

分支 `1.20.1-forge-bulk`，worktree `../StardewCraft-1.20.1-Forge-bulk`。迁移源是 main 工作区的最新内容（含未提交改动），由 `scripts/port/sync_from_main.py` 生成快照（`refs/port/main-snapshots/*`），当前同步点记录在 `scripts/port/main-sync-state.txt`。对照 1.21.1 原始代码用 `git show $(cat scripts/port/main-sync-state.txt):<path>`。

本方案取代旧的逐切片搬运：整个 `src/main/java` 一次进入 Forge 1.20.1 编译，用机械改写 + 兼容垫片吸收平台差异，剩余错误按包修复。

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/main/java` | 迁移中的完整源码（基线 + 机械改写 + 手工适配） |
| `src/port/java` | 兼容垫片：在 Forge 1.20.1 上重建模组用到的 1.21.1/NeoForge API |
| `scripts/port/rewrite_1201.py` | 幂等机械改写；从 main 同步来的文件先跑它 |
| `scripts/port/shim-classes.txt` | 1.20.1 不存在、被重定向到垫片的类 |
| `scripts/port/adapt_nbt_provider.py` | 方块实体/SavedData 的 HolderLookup.Provider 签名适配 |
| `scripts/port/adapt_block_use.py` | useItemOn/useWithoutItem → 1.20.1 `use` 分派桥 |
| `scripts/port/adapt_overrides_1201.py` | 其他已知覆盖方法签名变化 |
| `scripts/port/fix_java21_errors.py` | 按 javac 报错把 JDK 21 集合方法改为 `PortJava`（精确复刻 JDK 21 语义） |
| `scripts/port/sync_from_main.py` | 从 main 工作区三方合并同步；资源镜像后重跑转换 |
| `scripts/port/javac_all.sh` | 全树快速编译（约 10 秒），远快于 Gradle |
| `src/port/resources` | 仅移植版需要的资源（同步不会覆盖） |

机械脚本顺序：`rewrite_1201.py` → `adapt_nbt_provider.py` → `adapt_block_use.py` → `adapt_overrides_1201.py` → `adapt_vertex_api.py` → `strict_optional_fields_1201.py`，全部幂等。之后按 javac 日志运行 `fix_java21_errors.py`、`fix_errors_1201.py`，最后 `lint_port.py` 必须为 0（它检查“1.20.1 能编译但语义不同”的写法：贴图 UV 区间、NbtUtils 方块坐标格式、DFU 可选字段、JDK 21 方法、1.21 顶点链）。

## 快速编译循环

```bash
PORT_JAVAC_OUT=build/pj-<你的名字> PORT_JAVAC_LOG=build/pj-<你的名字>.log scripts/port/javac_all.sh
grep -A3 ': error:' build/pj-<你的名字>.log | less
```

并行工作时必须使用自己的 `PORT_JAVAC_OUT`/`PORT_JAVAC_LOG`，不要用默认路径。不要运行 Gradle 构建（共享 build 目录会互相干扰）；类路径在 `build/port-classpath.txt`。javac 只有在符号解析通过后才报告更深层的类型错误，所以错误数下降不是线性的。

## 垫片约定

- 1.20.1 缺失的类 `X` 一律放在 `com.stardew.craft.port.X`（保留原 FQN 作为后缀），例如 `com.stardew.craft.port.net.minecraft.network.codec.StreamCodec`。改写脚本已把源码中的引用指向这些位置。
- 垫片保持 1.21.1 的 API 形状（类名、嵌套类、方法名、泛型、静态工厂），这样数百个调用点无需改动，也方便以后从 main cherry-pick。只实现模组实际用到的成员，不做全量复刻。
- 垫片内部用 Forge 1.20.1 实现。语义必须等价：网络方向、线程（enqueueWork 回主线程）、事件阶段（Pre/Post 对应 START/END）、取消语义都要对上。
- 不能用垫片表达的差异（比如方法重写签名变化）直接改调用点，保持改动最小；行为不明显时加一行 `// PORT(1.20.1): 原因`。
- 1.20.1 已有同名同义的 Forge 类时直接用 Forge 类，并在改写脚本 `SPECIAL_CLASSES` 中登记，不另写垫片。
- 不准用删除逻辑、空实现或注释掉功能来让编译通过。确实无法等价的地方抛出带说明的 `UnsupportedOperationException` 并在 `docs/porting/bulk-port-gaps.md` 记一行。

## 行为等价（最高要求）

用户要求 1.20.1 版本的所有逻辑与 1.21.1 完全一致，编译通过不算完成。因此：

- 每处适配都以 1.21.1 的实际行为为准（模组代码 + 1.21.1 原版/NeoForge 的被调用实现），不是“差不多能用”。需要时阅读 1.21.1 原版源码（NeoForge 反编译：`~/.gradle/caches` 或 1.21.1 基线 worktree `../StardewCraft-1.21.1-baseline` 的依赖）和 1.20.1 源码 `build/port-mc-src`，确认两边语义一致：数值、顺序、取消/返回值、线程、边界与异常。
- 1.20.1 原版行为与 1.21.1 不同、且模组逻辑依赖该行为时，用垫片或 Mixin 复刻 1.21.1 行为。
- 无法等价的地方必须写入 `docs/porting/bulk-port-gaps.md`（现象、原因、影响、建议方案），交由用户决定；禁止静默删减。
- 完成后由全量 GameTest（1.21.1 通过的 794 项必须在 1.20.1 通过）与内容导出比对验证。

## 运行 GameTest（运行期验证阶段）

`scripts/port/run_gametests.sh <命名空间,逗号分隔|ALL> <你的名字>`：加锁串行运行（多个代理共用工作区时会排队），每次用全新世界目录 `build/gt-run-<名字>`，日志 `build/gt-<名字>.log`。按测试的模板命名空间（`@GameTest(templateNamespace=…)` 或 `@GameTestHolder` 值）只跑自己负责的部分；1.21.1 基准结果见 `docs/porting/gametest-baseline.md`，基准日志在 `build/port-parity/baseline-d453632-gametest*.log`，1.21.1 基线 worktree `../StardewCraft-1.21.1-baseline` 可用于对照（只读，不要在那里运行 Gradle）。修复必须让 1.20.1 行为与 1.21.1 一致；不得修改测试断言来“通过”。

## 协作规则

- 不提交、不推送；由协调者统一提交检查点。
- 只改自己负责范围内的文件；必须修改范围外的共享文件时，只做最小必要改动并在结果里列出。
- 不启动游戏客户端或服务器。
- 不修改 `/Users/jiayuhan/游戏制作/StardewCraft`（1.21.1 主线）和 `../StardewCraft-1.20.1-Forge`（旧切片方案，原样保留）。
- 需要对照 1.21.1 原始代码时用 `git show $(cat scripts/port/main-sync-state.txt):<path>`；1.21.1 原版 + NeoForge 反编译源码在 `build/port-mc121-src`。
- Minecraft 1.20.1 / Forge 源码：反编译类在 `build/port-classpath.txt` 的 forge jar 中，可用 `javap -cp "$(cat build/port-classpath.txt)" -p <类名>` 查看签名。
