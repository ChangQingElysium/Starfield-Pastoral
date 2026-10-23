# Fabric 1.21.1 完成度审计

2026-10-15 维护快照。原目标是将当前 NeoForge 主线完整移植到 Fabric 1.21.1、参考已有 Forge 移植经验压缩工期，并给出人民币报价。当前已有经过生产专服验证的候选基线；本轮已补齐客户端生命周期、自动订阅分类及可选插件发现，尚不能宣称全部功能等价或客户端验收完成。详细进度与报价见 [移植记录](fabric-1.21.1.md)，无窗口转换及定向测试环境见 [Mixin 审计](fabric-mixin-audit.md)。

本文把源码调用链、独立测试、真实服务端与客户端验收分别记录。以下未注明目录的报告均位于 `fabric/build/reports/`；构建报告可重新生成，不是新检出的输入依赖。

## 原目标与证据

| 目标 | 已有证据 | 当前边界 |
| --- | --- | --- |
| Fabric 1.21.1 独立工程 | `fabric/build.gradle` / `fabric/settings.gradle`，复用根 wrapper；Java 21、Loom 1.14.10、Loader 0.19.5、Fabric API 0.116.17+1.21.1。真实 common 入口为 `fabric/src/main/java/com/stardew/craft/fabric/StardewCraftFabric.java`，client 入口独立。 | 不覆盖 NeoForge/Forge 开发入口和正常存档。当前是候选工程，尚无发布提交/CI 结论。 |
| 全源与正式资源保全 | `source-conversion.json` 与 `current-inventory.json` 记录 4395/4395 源文件；输入基线含 39907 个资源文件与 118 个原生模型构建输入。全量编译、资源合并、403 类/816 方法订阅索引生成已通过。严格 SHA 覆盖及转换期间指纹复核会拒绝上游漂移。 | 文件存在、哈希和编译不证明业务等价。`native-resources.gradle` 使用主线登记母本重编译 NPC/家具，并按主线约定排除退役 NPC 资源；不是删除难移植内容。 |
| 参考 Forge 经验、一天冲刺 | 已核对 Forge-bulk 同步入口，采用整树诊断、幂等转换、窄平台层、领域并行、资源/存档/映射分别验证；来源与已核版本见移植记录。 | 未直接运行会创建 Git 对象/ref 的 Forge 同步脚本；不套用 1.20.1 的组件/NBT或渲染降版本规则。一天是冲刺目标，不是全部客户端表现已等价的承诺。 |
| 平台真实消费 | 下节列出能力、机器、流体、持久化与资源的实际入口。构建快照有 755 项平台测试通过、12 语言检查通过、39 项 Python 来源/转换/索引检查通过；见 `completion-final-validation.log` 与 `fabric/build/test-results/platformTest/`。 | 这些数字覆盖本次平台与发现入口修复；不能按数量推断全部功能或客户端画面等价。 |
| 网络与菜单注册闭包 | `network-menu-production-audit.json` 对照主线与正式候选 JAR：434 个注册调用一致，其中一个双向调用展开为 435 项，新增 9 项 Fabric 协议后 manifest 共 444 项；方向/codec/handler 静态绑定 0 差异。25 个菜单均有对应的 25 个客户端 screen 绑定。 | 这是正式字节码绑定及注册元数据核对，不等于每个业务 handler、分片或菜单操作均已真实执行。 |
| 正常地形生产保存/重开 | `completion-candidate-save.log` / `completion-candidate-reopen.log` 使用重映射 JAR 与原始 FAPI/FCAP 启动独立普通 noise 世界。两轮原生 RCON 保存、五维落盘和正常停止，重开读取旧 marker；`candidate-world-completion-inspection.json` 验证 22 份模组 SavedData 可读、玩家 UUID 一致、31 个 .dat 只读检查前后哈希不变。 | 证明该候选基线的新建世界与这次重开；不等于任意旧 NeoForge/Forge 存档迁移完成。最终同一 JAR 已实际启动两次，持久 revision 等于完整 JAR SHA；12 项存档断言通过。 |
| 报价与交付约束 | 移植记录保留 48000 元分项报价建议，谈判区间 30000—60000 元；按固定 1.21.1 基线、约定功能/兼容矩阵与 14 天移植缺陷修复范围估算。候选包、三份必需 JAR 与校验清单已有基线验证。 | 是本工程工作量估算，不是成交价或市场统计；新增版本、追更和上游既有缺陷另定范围。未 commit/push；发布仍需根 AGENTS 的干净检出与 CI gate。客户端由用户在现有实例验收，不自动打开额外窗口。 |

## 能力、持久化与资源的调用链

本节平台路径以 `fabric/src/main/java/com/stardew/craft/platform/` 为前缀；源覆盖位于 `fabric/src/overrides/java/`。本轮只读抽查未发现可证实的本模组可达 P1/P2 简化或漏接入；这是审查结论，不是全功能形式化证明。

- **能力注册与原生自动化：** `StardewCraftFabric.onInitialize` 在完整注册后发布 `RegisterCapabilitiesEvent`；原 `UtilityAutomationCapabilities.registerCapabilities` 的 provider 链由 `fabric/capability/RegisterCapabilitiesEvent.install` 发布到真实 `ItemStorage.SIDED` / `FluidStorage.SIDED`。FAPI 的 `HopperBlockEntityMixin` 对无原生 Container 的机器查询此入口；原生 Container 则保留其漏斗路径，不是只存下一份 handler 而无人消费。
- **机器事务：** `UtilityItemHandler.stardewcraft$itemStorage` 调用机器的 `TransactionalMachineAccess`；箱子 `InvWrapper` 接 `ContainerItemStorage`，共享 Junimo 库存使用实际 backing-list 身份。计时机器接 `PlannedMachineItemStorage`，提交前由 `mixin/item/transfer/mixin/TransferTransactionCommitMixin` 触发冲突检查，提交后才执行真实收取/开工。`HeavyFurnaceBlockEntity.planTransactionalInsertion` 有独立重型熔炉配方与燃料计划，没有误用父类普通熔炉配方。未知适配器显式失败，不以静默空库存掩盖。
- **花盆与鱼塘水：** `GardenPotBlockEntity.WaterHandler` 实现 `TransactionalFluidHandler` 并返回 `GardenPotWaterStorage`，保留 mB/滴单位换算、提交浇水和中止不写世界。禁止抽水是共享主线 `WaterHandler.drain` 的既有规则；蟹笼 0-slot 禁自动化同样来自主线。`fabric/fluid/FishPondWaterFluid` 注册真实原生源/流动流体，通过正式 WATER tag 参与水物理；原生滴水桥与客户端 tint/fog 桥分别消费其数据，客户端实际效果仍待验收。
- **实体与玩家数据：** `mixin/data/mixin/EntityPersistentDataMixin` 在 `Entity.saveWithoutId` 的子类保存 hook 前写入、在 `Entity.load` 的子类读取 hook 前恢复。`fabric/data/PersistentEntityDataStorage` 以 `NeoForgeData` 为权威，兼容独立 `ForgeData`，保留冲突与损坏载荷；玩家 restore 只复制 `PlayerPersisted`，其余业务由真实 Clone 事件处理。未挂 Mixin 时明确抛错，没有全局空 Map 后备。
- **SavedData 与 chunk attachments：** `DimensionDataStorageMixin` 保留本模组 nullable data-fix 类型；`SavedDataFactories` 仍调用原生 Factory，原生 1.21.1 保存为同步写。`AtomicNbtFiles` 对动物迁移 checkpoint 先写临时文件并 flush，再替换，错误继续向调用者传播。`LegacyChunkAttachmentMixin` 在原生 `ChunkSerializer.read` 前迁移、`write` 返回时保留 NeoForge attachment 容器；损坏完成标记会阻断土壤迁移，不伪装为空集合后重做。
- **BE 数据与加载：** `BlockEntityChunkDataMixin` 接初始 chunk update tag，`BlockEntityPacketDataMixin` 接增量 BE packet，分别调用本模组的 `handleUpdateTag` / `onDataPacket`。`LevelChunkBlockEntityLoadMixin` 将完整注册后的 BE 排入每个 Level 自有队列，`LevelBlockEntityLoadMixin` 在实际 tick pass 前调用 `onLoad`；不把 NBT 尚未读取完的即时创建回调冒充完成加载。
- **资源重载：** `ServerResourceReloadListenersMixin` 在原生 `ReloadableServerResources.listeners()` 上链式追加 `ServerResourceReloads` 收集的真实 listener，完整异步执行仍归原生 reload 引擎。DataMap 的独立返回值修改能组合；七类 DataMap 由实际 reload/标签绑定及 configuration 同步消费，服务端权威快照与客户端快照隔离。本轮未发现本模组正式 data JSON 仍依赖未转换的 Neo condition/serializer；资源成功加载不等于客户端模型已实际烘焙。

## 运行证据及仍待收口项

定向 GameTest 共 20 个不同测试，最后一次结果均 PASS，记录见 `directed-gametests-summary.json` 和逐项 JSONL `directed-gametests.json`。Solar 两次地下定位 FAIL 原样保留：未加载命令源区块时原生命令取最低高度，预加载后同一断言在地表通过，没有修改太阳能板业务或测试。完整 908 项套件未自动执行。

生产基线的两轮无窗口真实 socket 会话通过，见 `protocol-peer-completion-candidate-sessions.json`。configuration ACK、PLAY 登录包及同 UUID 重连有证据；manifest ACK 按服务端声明回送，未知业务与世界包只作不透明观察，peer 没有定义客户端/图形类。`network-menu-production-audit.json` 已独立核实：原 434 个调用因一个双向注册展开为 435 项；9 项 Fabric 扩展为 offer/ACK 两项、双向分片两项、DataMap 三项、菜单一项、光照一项，合计 444。真实 peer 两轮均没有分片传输或菜单包，七类 DataMap 快照均为空；不将静态闭包与 ACK 证据扩大为这些未触发业务或客户端 registry/UI 已验收。

`mixin-audit/client-transform.json` 的 `client_bootstrap_order` 来自真实 Knot 游戏补丁与应用后的 Mixin 字节码：client entrypoint 258、Options 270、RenderBuffers 702、Gui 888、late extensions 998、初次资源 reload 1111；`entrypoints_precede_all_consumers`、`early_setup_user_ready`、`late_extensions_after_renderer_fields_before_reload` 均为 true，日志为 `completion-client-bootstrap.log`。这些是指令索引及顺序合同，不是 constructor 执行、窗口、资源烘焙或画面证据。

本轮发现并修复三项可达缺口：客户端 **Level/Chunk Load/Unload 事件 producer** 补齐三个客户端 Mixin 与六项回归；自动订阅按真实 FML 4.0.42 的事件参数类型分类，schema 2 支持同类混合总线，403 类/816 方法正确分为 MOD 168、GAME 648，仅纠正原四项错分；metadata 补齐 36 个 Jade 与 1 个 JEI 可选入口，动态 census 与实际 Loader 隔离回归通过，未增加强依赖。详见 [事件闭包审计](fabric-event-closure-audit.md)、`optional-plugin-discovery-audit.json`。独立复核包含原 22 个非索引处理方法的手工注册、可选回调或明确退休去向。

统一 `completion-final-validation.log` 为 BUILD SUCCESS；755 项平台测试、39 项 Python 检查均通过。随后 `completion-client-linkage.log` 在真实 Knot 中进一步定义并解析 403 个物理客户端订阅类签名，0 失败；不调用处理方法或构造器。最终 JAR SHA：`00eaf7d4e3d7cf2c9a13e83ef8af7d6ee3227e2f536c1c8f8f0d5555d25b1b52`。370 个 Mixin、正式资源与六个内嵌依赖审计见 [安装产物审计](fabric-install-artifact-audit.md)。两个新专服进程均真实执行，第二次读取等于该 SHA 的落盘 revision；两轮 peer 与最终只读 NBT 报告已绑定同一 SHA。生产运行任务明确禁用 up-to-date/build-cache，避免把跳过执行误当重开。

`candidate-bundle-validation.json` 将本轮 JAR、ZIP、精确五文件闭包、两次实际生产生命周期、两轮 peer 及 12 项存档断言关联，全部通过；旧候选及旧报告保留为历史，不能代替新 SHA 的证据。用户已选择自行安装候选包并反馈；客户端资源烘焙、画面/输入/GUI、Jade/JEI 实际界面及 Sodium/Iris/Continuity 组合、旧存档完整迁移仍未实机验收。工程与无界面验证已经收口，完整游玩验收仍待该反馈，不作全功能等价的提前结论。
