# Fabric 1.21.1 Mixin 挂载审计

2026-10-15 的首轮完整 CLIENT 字节码复核覆盖原有 `stardewcraft.mixins.json` 的 125 个 Mixin（公共 34、客户端 91），以及 `stardewcraft.fabric.mixins.json` 的 242 个平台 Mixin（公共 178、客户端 64）。合并后有 221 个不同目标：198 个完成真实 Mixin 转换，23 个可选模组目标未安装而跳过，挂载失败为 **0**。这份数字对应当时的配置快照；后续结果以重新执行审计产生的 JSON 为准。

复跑命令，在仓库根目录执行：

```sh
./gradlew -p fabric auditClientMixins --console=plain
```

任务先完成 `classes` 与 `platformTestClasses`，再通过 `writeRuntimeClasspath` 导出正式 `main.runtimeClasspath`。因此检查会包含本次生成的全部共享源码、平台代码、处理后的资源与实际运行依赖。审计任务没有复用上次结果的输出缓存，每次调用都会执行转换。

输出位于：

- `fabric/build/reports/mixin-audit/client-transform.json`：两份配置的数量及 SHA256、每个目标的转换状态、已合并的本项目 Mixin、异常堆栈，以及子进程退出码。
- `fabric/build/reports/mixin-audit/client-transform.log`：Loader 与 Mixin 日志。
- `fabric/build/reports/client-runtime-classpath.txt`：本次正式运行类路径。

成功需要 Gradle 任务成功、`audit_process_exit_code` 为 `0`、`failed_targets` 为 `0`，且没有 `setup_failure`。`optional-target-absent` 单独记录，不能计为已挂载。报告和日志属于可重新生成的构建输出，不是新检出仓库必需的输入。

完成审计另检查了 Loader 实际补丁与 Mixin 转换后的 `Minecraft` 构造器：客户端入口位于 Options、RenderBuffers、Gui 和首次资源重载之前；入口调用时单例与 User 已赋值。晚期客户端扩展初始化则位于模型、物品渲染器、BE dispatcher、粒子引擎赋值之后、首次资源重载之前。`client_bootstrap_order` 保存各调用点指令位置并逐次断言；缺少真实 Loader hook 或时序错误会使审计失败。该检查只读取字节码，没有执行这些入口或构造器。

完成审计修复客户端世界/区块生命周期后，最新配置为 245 个平台 Mixin 加 125 个原有 Mixin；222 个目标中 199 个完成转换、23 个可选目标缺失、0 失败。`physical_client_subscriber_linkage` 另在同一真实 Knot 下定义并解析 403 个物理客户端订阅类及父类的方法签名，0 失败；不调用处理方法或构造器，游戏初始化守卫仍生效。该额外检查的日志为 `completion-client-linkage.log`。自动订阅总线及可选插件发现的修复另见 [事件闭包审计](fabric-event-closure-audit.md)与[完成度审计](fabric-completion-audit.md)。

## 验证方式与隔离

工具在独立、短命的 Java 子进程中设置 CLIENT 环境，使用当前 Fabric Loader 与 FAPI 的真实转换链。开发映射遵循 Loom：游戏及运行时命名空间为 `named`，模组分发命名空间为 `intermediary`。缺少后一项会让 FAPI 的引用映射出现大量环境造成的失败。

Loader 0.19.5 的 `Knot.init()` 会执行 `preLaunch`。工具在内存中严格移除这一处调用，数量必须恰好为一；不修改依赖 JAR。随后只调用 `KnotClassDelegate.getPostMixinClassByteArray` 获取转换后的字节码，逐个收集失败，不通过 `Class.forName` 定义目标类，也不调用游戏入口或构造器。

子进程的 Java agent 拒绝 Minecraft／Blaze3D 类初始化及游戏 `main`、GLFW／GL 初始化。两个安全测试验证了“可定义但禁止初始化”的行为，以及对固定 Loader 字节码只移除一次 `preLaunch`。运行目录独立于游戏存档，退出后清理；父进程取消时终止子进程，正常审计设置 180 秒上限。开始新一轮时移除旧报告，避免失败后误读上次成功结果。

## 本批修复与保留项

原有 Mixin 的五处必需目标修复采用 `fabric/src/overrides/java/com/stardew/craft/mixin/` 下的严格 SHA256 源覆盖，共享主线保持原样：

| Mixin | 修复 |
| --- | --- |
| `ItemStackCodecCountRangeMixin` | 将 Neo 合成方法名改为当前 Loom 原生的 `method_57371`，保留物品数量编码上限扩展。 |
| `MinecraftTerrainPickMixin` | 在原生方块选取结果存入局部变量后处理，保留上下文 clone 桥、空结果和 Ctrl-NBT 顺序。 |
| `ArmorLayerCombatCollapseMixin` | 在原生可见部位设置之后替换实际盔甲模型参数；保留纹理、染色、饰纹和光效的原生消费。 |
| `GardenPotCropRenderMixin` | 使用原生 `renderBatched` 的七参数描述符，保留盆栽世界模型取消逻辑。 |
| `FishingArmorLayerMixin` | 使用原生 `renderArmorPiece` 描述符，保留钓鱼时的盔甲隐藏逻辑。 |

真实转换还暴露并验证了平台侧的 receiver、区块读取参数、数据包包名与附魔局部变量兼容修复；这些修复已包含在上述零失败复扫中。五份源覆盖的上游 SHA 变化会使源码生成失败，必须重新核对后更新，不能静默沿用。

两个 `require=0` 目标经核对后保持原样：`InventoryMaxStackMixin` 的可选具体类入口不存在，但 `ContainerMixin` 已覆盖实际继承的默认方法；`FishingHookDisableVanillaBiteMixin` 的 `lavaHurt` 在原生与当前 Neo 源码中均只由父类声明，原注入也没有生效。这是保留的上游岩浆钓鱼保护缺陷，本批没有顺带修复；`catchingFish` 注入仍正常挂载。

## 定向 GameTest 的区块加载前提

同日隔离服务端运行 `solarminutechargingpersistsandfinisheswithinoneday` 时，前两次结构位置为 `(8648, -64, 8003)`，面板位于 Y=-52，因上方地形遮挡而未满足晴空充能前提。原生 `TestCommand.createTestPositionAround` 使用 `WORLD_SURFACE` 决定高度，会忽略 `execute positioned` 指定的 Y；`Level.getHeight` 在区块未加载时返回最低构建高度。模板 `minecraft:bastion/mobs/empty` 实际仅为 1×1×1，原生清场只延伸到结构上方 20 格，不能清除地下测试上方的全部地形。Solar 的 Fabric 源覆盖仅增加事务存储，充能、天空、天气与时钟逻辑和共享源一致。

在同一隔离测试世界先加载命令源所在区块，等待第二条命令确实输出 `SOLAR_SOURCE_LOADED`，然后执行第三条；测试逐项运行，并等待本项 JSONL 结果后再启动下一项：

```text
forceload add 8648 8000
execute if loaded 8648 128 8000 run say SOLAR_SOURCE_LOADED
execute positioned 8648 128 8000 run test run solarminutechargingpersistsandfinisheswithinoneday
```

原生 `GameTestRunner` 在批次结束会解除该维度的强制加载，下一项运行前需重新确认加载状态。上述复跑结构位置为 `(8648, 75, 8003)`，耗时 4 ms，结果 PASS；没有修改太阳能板业务或测试，也没有执行成功的地形清除。证据保存在 `fabric/build/reports/directed-gametests.json`（内容为 JSONL，保留两次失败及后续成功）与 `phase43-directed-validation.log` 的加载确认和 PASS 日志。当时 14 个不同定向测试的最后一次结果均为 PASS，这不是全部 GameTest 的通过结论。

## 结论边界

本审计证明当前配置与依赖下，已存在目标的必需 Mixin 能完成 CLIENT 字节码转换。它不执行 `preLaunch`、模组入口、游戏类初始化、OpenGL 绘制或真实网络流程，不能替代客户端启动、画面／输入检查、联机验收、独立服务端运行或发布用的干净检出验证。23 个缺失的可选目标也没有获得兼容性通过结论。
