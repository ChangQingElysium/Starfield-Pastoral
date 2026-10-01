# 1.20.1 Forge 整体迁移工作指南

分支 `1.20.1-forge-bulk`，worktree `../StardewCraft-1.20.1-Forge-bulk`。迁移源是冻结基线 `2492f0300`（1.21.1 NeoForge）。

本方案取代旧的逐切片搬运：整个 `src/main/java` 一次进入 Forge 1.20.1 编译，用机械改写 + 兼容垫片吸收平台差异，剩余错误按包修复。

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/main/java` | 迁移中的完整源码（基线 + 机械改写 + 手工适配） |
| `src/port/java` | 兼容垫片：在 Forge 1.20.1 上重建模组用到的 1.21.1/NeoForge API |
| `scripts/port/rewrite_1201.py` | 幂等机械改写；从 main 同步来的文件先跑它 |
| `scripts/port/shim-classes.txt` | 1.20.1 不存在、被重定向到垫片的类 |
| `scripts/port/javac_all.sh` | 全树快速编译（约 10 秒），远快于 Gradle |

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

## 协作规则

- 不提交、不推送；由协调者统一提交检查点。
- 只改自己负责范围内的文件；必须修改范围外的共享文件时，只做最小必要改动并在结果里列出。
- 不启动游戏客户端或服务器。
- 不修改 `/Users/jiayuhan/游戏制作/StardewCraft`（1.21.1 主线）和 `../StardewCraft-1.20.1-Forge`（旧切片方案，原样保留）。
- 需要对照 1.21.1 原始代码时用 `git show 2492f0300:<path>`。
- Minecraft 1.20.1 / Forge 源码：反编译类在 `build/port-classpath.txt` 的 forge jar 中，可用 `javap -cp "$(cat build/port-classpath.txt)" -p <类名>` 查看签名。
