# Fabric 1.21.1 移植

目标由用户于 2026-10-15 确认：Minecraft 1.21.1 / Fabric，保持当前主线的功能、内容 ID 与资源。按一天冲刺推进；完整发布必须逐项取得验证证据，不能提前承诺在 24 小时内全部通过。

## 基线与入口

- 设计权威：仓库内 `src/main`，NeoForge 1.21.1。
- 主线 HEAD：`df2151c66a2a9c02855e4db23108e57c422ce78e`；本次开始时另有 1179 个已跟踪修改、45 个未跟踪文件。HEAD 不能代表完整工作区输入。
- Fabric 入口：仓库内独立 Gradle 工程 `fabric/`，复用根 `gradlew`；不另复制完整工程，不切换或覆盖主线、Forge 分支和存档。
- 工具链：Java 21、Gradle 9.2.1、Loom 1.14.10、Loader 0.19.5、Fabric API 0.116.17+1.21.1、Mojang 官方映射。版本已从官方元数据核实。
- 源码由 `scripts/port/prepare_fabric_sources.py` 完整生成到 `fabric/build/generated/sources/stardewcraft`；只进行已实现平台 API 的明确转换，其余引用保留并交给编译器暴露。
- 平台实现维护于 `fabric/src/main/java/com/stardew/craft/platform/fabric/`；资源使用仓库内相对路径，不读取另一工作树或本机研究素材。
- 每个生成源记录原文件与输出 SHA256。`fabric_inventory.py --baseline` 用于发现并行主线变化；哈希和词法覆盖不代表功能等价。

## 审计基线

本轮现场统计：4395 个 Java 文件、577263 行，1525 个文件直接导入 NeoForge；838 个事件处理方法、149 类事件参数、435 种 CustomPacketPayload、125 个启用 Mixin。事件数字使用正式词法审计修正初步检索的 832/147。主资源 39907 个文件，约 617.4 MB。

隐式平台依赖也在移植范围内：59 个文件中 443 处显式接收者的 `getPersistentData`，另有 3 处继承的裸调用，共 446 处；动态 ModelData、Data Maps、FluidType、物品/流体能力、configuration 握手和客户端渲染补丁。不能仅消除 imports。

软集成存在 Fabric 对应版本：Jade 15.10.4+fabric、JEI 19.32.0.358；AppleSkin 的 Fabric 对应版为 3.0.6+mc1.21，HUD 事件已改接其真实 Fabric API。兼容性结论按实际测试版本记录。

配置采用维护中的 Forge Config API Port 21.1.6 Fabric 发行版，保留 COMMON/CLIENT/SERVER TOML 作用域及世界配置。它是新增的明确运行依赖；其保留的 `net.neoforged.*` 配置包不等同于加载 NeoForge。

## 当前验证状态（2026-10-15）

当前已有可安装的重映射候选 JAR；客户端资源、画面、GUI 操作及可选渲染模组组合仍待实机验收。下面区分开发映射与正式生产映射，历史失败和早期未完成状态见后文记录。

| 验证 | 当前证据 |
| --- | --- |
| 完整构建与平台检查 | 4395 个源文件全部参与编译；`check auditClientMixins remapJar packageFabricCandidate` 成功。755 项平台测试，0 失败、0 错误、0 跳过；12 语言检查通过；Python 来源/转换/索引 fixture 39 项通过。Gradle 日志为 `fabric/build/reports/completion-final-validation.log`，逐项平台结果在 `fabric/build/test-results/platformTest/`。 |
| 无窗口 CLIENT Mixin 转换 | 222 个唯一目标中 199 个真实转换、23 个未安装的可选目标跳过、0 失败；禁止游戏与图形初始化。方法和边界见 [Mixin 审计](fabric-mixin-audit.md)。这项为开发映射字节码证据。 |
| 定向 GameTest | 20 个不同测试的最后一次结果均 PASS，共 22 次尝试；保留 Solar 两次地下定位失败及预加载后成功。见 `directed-gametests-summary.json` 与逐项 JSONL `directed-gametests.json`；未执行全部 908 项。 |
| 开发映射专服生命周期 | 正常 noise 世界创建、保存、停止与重开读取持久 marker 均通过；五维保存及 NBT 检查有独立记录，见 `server-lifecycle-evidence.json`、`server-world-inspection.json`。 |
| 正式生产映射专服 | `runFabricCandidateServer` 使用 `remapJar` 与原始 Fabric API/FCAP JAR，在 `fabric/run-candidate-server` 创建独立普通世界并启动成功。最终同一 JAR 的两次实际进程均经原生 RCON `save-all flush` / `stop` 完成五维保存和正常退出；重开同一存档后读取旧 marker 及等于完整 JAR SHA 的新 revision 成功。见 `completion-candidate-save.log`、`completion-candidate-reopen.log` 与 `candidate-rcon-commands.jsonl`。最终只读 NBT 检查确认 22 份模组 SavedData 可读、原生及模组玩家 UUID 一致，31 个 .dat 检查前后哈希相同，见 `candidate-world-completion-inspection.json`。 |
| 正式候选协议检查 | `protocol-peer-completion-candidate-sessions.json` 绑定最终 JAR SHA，记录重开存档后的两轮成功真实 socket 会话：configuration 任务 ACK、PLAY 登录包、主动断开与同 UUID 重连。peer 未定义客户端/图形类；manifest ACK 按服务端声明回送，未知业务及世界数据只作不透明观察，不能据此宣称 435 个业务 handler、registry remap 或菜单/UI 已验收。此前候选的两轮报告独立保留。 |

以上报告名未带目录时均位于 `fabric/build/reports/`。报告对应本地候选验证，不代替发布用干净检出/CI gate。

安装包为 [Fabric 候选 ZIP](../../fabric/build/distributions/stardewcraft-fabric-0.6.3fix1-fabric-dev-candidate.zip)，包含三个必需 JAR、中文安装说明及 SHA-256 清单；由 `./gradlew -p fabric packageFabricCandidate` 生成。主 JAR SHA-256：`00eaf7d4e3d7cf2c9a13e83ef8af7d6ee3227e2f536c1c8f8f0d5555d25b1b52`。370 个 Mixin、40,016 个处理后资源、六个内嵌依赖及可选 AE2/Gecko 映射检查见 [安装产物审计](fabric-install-artifact-audit.md)。本轮源输入 44,432 项相对开工基线新增/修改/删除均为零，4395/4395 Java 生成覆盖完整。

最终 ZIP 为 248,873,067 bytes，[ZIP SHA-256 文件](../../fabric/build/distributions/stardewcraft-fabric-0.6.3fix1-fabric-dev-candidate.zip.sha256)独立保存。`candidate-bundle-validation.json` 已逐项复算包内三个 JAR 的哈希、精确核对五个文件，并关联最终生产重开、两次连接、12 项只读存档断言及 403 个客户端订阅类签名检查；全部通过。客户端实机验收仍待用户在现有实例完成，未自动启动额外客户端。

## 推进顺序与完成条件

| 阶段 | 工作 | 可观察完成条件 |
| --- | --- | --- |
| 工具链与基线 | 独立构建、全源覆盖、来源指纹、错误清单 | Loom 正确解析；不漏源文件；生成器不修改上游 |
| 注册与配置 | 原生 registry / Holder、内容顺序、配置迁移 | 全内容注册；稳定 ID；不靠空垫片 |
| 网络与菜单 | play / configuration、435 payload、握手、线程与方向 | 消息注册完整；客户端与专服类加载安全；握手失败可诊断 |
| 事件与持久化 | 回调/注入、取消/优先级、世界生命周期、SavedData/attachments | 每个原事件有真实触发路径；新档和重开数据一致 |
| 能力与流体 | 机器输入输出、多块代理、Fabric Transfer 事务、鱼塘 | 事务回滚与提交正确；无物品/流体复制或丢失 |
| 客户端 | 模型 loader、FRAPI、UI/HUD、光照、门洞、Sodium/Iris | Mixin 目标真实挂载；各资源 loader 成功；GUI 坐标契约保持 |
| 回归与交付 | 完整编译、资源加载、定向 GameTest、正常地形专服创建/保存/重开 | 各检查证据独立保留；用户在已有客户端验收；具备可复现安装包 |

不删除难移植的系统、过滤源码或发送空消息来降低错误数。开发期没有完整入口时阻止输出可分发空壳 JAR。

## 命令与证据边界

从仓库根目录执行，运行 Gradle 的环境须有 JDK 21：

```sh
./gradlew -p fabric platformClasses platformTest --console=plain
./gradlew -p fabric classes --console=plain
python3 scripts/port/fabric_inventory.py --root . --output fabric/build/reports/inventory.json
python3 -m unittest discover -s compatibility -p 'test_*fabric*.py'
```

`platformClasses` / `platformTest` 验证平台适配，`classes` 编译全部上游生产 Java。当前候选已额外运行 `check auditClientMixins remapJar packageFabricCandidate` 和隔离生产专服验证；不自动开启客户端窗口或全量 GameTest。后续发布仍遵守根 AGENTS 的干净检出及 CI gate。

## Forge 经验

已核实 Forge-bulk 的 HEAD 为 `1184e5887803ff5ad623d044694ebc110f91bf3b`，同步状态指向 `df2151c66a2a9c02855e4db23108e57c422ce78e`。其旧状态文档存在滞后；以实现和日志为准。

复用：整树诊断、幂等转换、窄平台层、按领域并行、严格内容对照与运行检查。不能复用 1.20.1 的组件转 NBT、旧渲染坐标或 Embeddium/Oculus 降版本适配。

Forge 既有同步脚本默认会创建 Git commit/tree/ref；当前没有提交授权，不直接运行。Fabric 生成器只写本工程 `fabric/build`，无 Git 对象或 ref 变更。

已知教训：JSON 一致仍可能引用不存在的 loot serializer；平坦 GameTest 不能证明正常噪声世界可启动；Mixin AP 成功不证明实际映射下可挂载；必须单独验证专服保存和重开。客户端表现、存档兼容和联机分别检查，编译不是最终验收。

## 工作记录

本节保留移植过程中的历史状态；当前结论以上方验证表为准。开发映射与正式候选专服重开均已通过；客户端表现尚待验收。

2026-10-15 首批历史证据（尚未建立完整入口时）：

- `fabric/` 已可独立解析工具链。实现了原生 registry/Holder、play/configuration 网络及大消息分片、实体持久数据、首批世界/玩家/客户端 tick 事件、物品处理接口与配置接入。它们仍需连接完整的 common/client 初始化入口，不能作为可玩 Fabric 模组分发。
- `./gradlew -p fabric platformTest checkTranslations processResources` 成功：当轮 86 项测试，12 语言检查，52 个原生 NPC 模型与纹理、6 个家具资源及正式资源合并均通过。
- 随后接入客户端 tick 的 `platformTest` 为 **88 项，0 失败、0 错误、0 跳过**。Python 转换/审计 fixture 为 **16 项通过**。
- 同轮完整 `classes` **未通过**，javac 报告 5047 个错误，主要为尚未实现的加载器接口和 Minecraft 扩展。错误量受级联影响，不是剩余功能数或完成百分比。日志为 `fabric/build/reports/whole-project-validation.log`；此前首轮全源诊断为 5645。
- 已有 446 处实体持久数据调用全部转换；来源覆盖仍为 4395/4395。手工 override 保存上游 SHA256，主线上游变化或转换期间并发修改会使生成失败，避免静默遮掉新功能。
- 该首批阶段的 Mixin 仅编译与源码/字节码核对，当时尚未启动客户端、专服或 GameTest，也未生成安装 JAR。后续候选与运行证据见当前验证表；整个任务未提交或推送。

同日后续实现与验证记录（完整构建以 `completion-final-validation.log` 为最新批次）：

- **平台定向测试 755 项通过，0 失败、0 错误、0 跳过**；12 语言检查通过。Python 来源/转换/测试索引 fixture 为 39 项通过。这些测试运行于独立 JVM；直接读原生字节码和实际 API 的合同检查，仍不能替代 Fabric Loader 实际应用 Mixin。
- **全部 4395 个源文件参与编译，完整 `classes`、正式资源合并和订阅索引生成均通过，0 条 javac 错误。** 日志与结构化诊断位于 `fabric/build/reports/phase43-directed-validation.log`；事件索引为 403 个订阅类、816 个静态处理方法。此前各轮诊断受 Gradle 重复输出与错误级联影响，不将数量变化解释为功能完成率。
- common/client 初始化入口已建立，保留原主类业务初始化；事件订阅由编译后 ASM 索引按物理侧与 MOD/GAME 总线注册。完整索引已从全量编译产物生成；common 实际装载另由开发和生产专服启动取得证据，client 入口尚待客户端运行检查。
- 注册、配置、play/configuration 握手与分片、菜单 32 位 DataSlot、SavedData、旧实体 NBT、农场附件迁移、资源重载、七类 Data Maps 及同步已连接平台实现。Data Maps 区分同一 JVM 中客户端和服务端的所有权；尚不支持的条件格式和动态 registry 同步会明确拒绝，不静默丢弃。
- 玩家 Clone、完整跨维度分支、实体加入/离开/死亡/tick、追踪/区块与数据同步、拾取/精确交互、攻击/采掘速度、状态适用与掉落、工具行为、物品使用优先级和可回滚的放置快照已有真实原生入口。永久建筑草地及原地块身份仍保留。模型数据走 Fabric BlockView 的渲染快照，不在渲染线程直接读 BE。
- 实核 212 个 GameTest 类、908 个静态测试方法（含目录外 6 类），835 方法显式 namespace、73 方法按 holder；private 构造不影响静态测试索引。注解转换和注册器已实现，其中 20 项定向运行的最新结果通过；**尚未执行完整 908 项套件**。25 处菜单构造与 29 处生产单参数 `openMenu` 使用原生 Fabric API 能力；没有改为另一套菜单协议。
- 模型复用固定的 Porting Lib `3.1.0-beta.91+1.21.1`：`gametest/core/render_types/model_loader/model_data` 五个模块，显式打包依赖闭包。几何加载、动态 FRAPI quads、材质/AO、物品多 pass、模型事件和 HUD/input 已逐批接入；手持/世界模型渲染的额外参数、客户端物品/方块扩展、渲染边界、动态光照、键位冲突/修饰键及 EditBox 阴影已连接原生消费点。屏幕事件与视口/fog、玩家与实体渲染事件已接入，实际客户端资源与表现仍待运行检查。
- 原生 Container 的 Fabric Transfer 桥保留组件、嵌套事务和共用 Junimo 库存；38 类注册机器/容器均接入对应入口；蟹笼保留原 0-slot 禁用行为。GardenPot 已覆盖本模组作物、茶树、采集作物及已核实的原版作物，未知第三方作物明确拒绝。共享 RNG、燃料缓冲、随机配方与延迟提交采用统一预提交检查，abort 不修改库存/世界/随机源。GardenPot 的共享随机源、命名 loot sequence、库存和世界修改采用统一预提交检查；第三方 loot 条件在同一事务内读取尚未提交的世界状态仍有边界，**不能宣称任意附加模组自动化等价**。独立 Transfer 测试使用官方 variant 实现构造器补足无 Mixin 的缓存前提；这类 fixture 不等于真实 Loader 下全部自动化组合已验收。
- 专服早期历史：隔离的 `fabric/run-server` 首次装载因平台 Mixin 配置包范围过大而失败，235 个平台 Mixin 随后迁入专用包，再新增 7 个投射物入口，合计 242 个。Modrinth FCAP POM 缺少的开发期 NightConfig 依赖已补足；四个 payload 的客户端 synthetic lambda 移入专属 holder，消除了 common 类验证时提前链接 Screen 的故障，原生 Loader stripper 字节码探针 4/4 通过。该阶段曾出现 Gradle 成功但 Minecraft 启动失败，以后者记录失败，不将它与后续成功混淆。
- 开发映射专服后续成功创建正常 noise 世界，载入 1408 个配方与 1399 个进度；两轮控制台 `save-all flush` / `stop` 均完成五维保存并正常退出，重开读取上一轮持久 marker 成功。证据为 `server-console-save.log`、`server-clean-reopen.log`、`server-lifecycle-evidence.json`。只读原生 NbtIo 确认 1.21.1/DataVersion 3955、指定种子、主世界 noise 及两个 Stardew 维度；19 份模组 SavedData 可读，26 份 .dat 检查前后哈希一致，见 `server-world-inspection.json`。正式 remapJar 候选的独立启动、两轮协议检查和保存/停止另见当前验证表；最终 JAR 已完成同存档重开及两轮协议复核，客户端实机验收尚未进行。

独立区块票据保留 controller/owner/tick mode 身份，使用原生距离票据并补足无人强制 tick；不修改 vanilla `/forceload`。旧 `chunks.dat` 的其他 controller 原始 NBT 保留，本模组旧租约只按原有清理回调退役。动物迁移 checkpoint 使用已检查异常、落盘 flush 和原子替换；原生 Fabric 1.21.1 SavedData 为同步写，不保留 Neo 异步队列空垫片。

已知上游/兼容边界：Template 梯子缺 CLIMBABLE 标签的既有状态保留；GeodeCrusher 原本无玩家自动输入被拒绝，保留其语义。现有 CTM soft Mixin 面向 Neo CTM，不能据此宣称 Fabric Continuity 已兼容；动态模型与 Continuity 的 FRAPI wrapper 顺序需要单独运行检查。

睡眠、区块数据、战斗、传送、钓鱼、种植与原生生物生成事件，以及附魔/修理、活塞/外表面剔除/中键拾取扩展已接入真实原生消费点。原有 125 个 Mixin 与 245 个平台 Mixin 已接受 CLIENT 环境真实 Knot/Mixin 字节码转换：222 个唯一目标中 199 成功、23 个可选集成目标因依赖未安装而跳过，0 失败。隔离子进程严格移除唯一 preLaunch 调用，并用 Java agent 阻止游戏/图形初始化；这项证据不代表窗口、资源烘焙或视觉验收。完整方式与已知上游缺陷见 [Mixin 审计](fabric-mixin-audit.md)。

资源审计发现的 51 个 `neoforge:composite` 模型已补真实 Fabric loader，保留命名子模型、可见性、ModelData、多层与物品 pass；嵌套 FRAPI mesh 保留原材质。实际 Porting Lib 元素烘焙路径已通过，仍待用户客户端资源与视觉验收。

已串行执行 20 个定向 GameTest，最新结果全部 PASS，涵盖永久草地/材质变体、容器/Junimo 库存/移动与原子交换、复合外观、太阳能/生产参数/时钟、植物支撑、弹弓菜单/投射物保存及鱼塘进度。原始逐项记录为 `directed-gametests.json`（内容为 JSONL）。太阳能前两次因测试位置的区块未加载、原生命令将场景放到 Y=-64 而失败；同一测试预加载后在 Y=75 通过，业务与原断言未改，历史失败保留。复跑方法见 [定向测试的区块加载前提](fabric-mixin-audit.md#定向-gametest-的区块加载前提)。未自动执行全部 908 项。正式候选的两轮真实 socket 协议会话已通过，最终记录于 `protocol-peer-completion-candidate-sessions.json`；无窗口 peer 的 ACK/登录/重连证据不代表完整客户端业务 handler、资源或 UI 验收。

完成审计补修三项可达缺口：客户端 Level/Chunk Load/Unload 事件已在原生加载、替换、断线和有效区块包/drop 位置发布；自动订阅按实际 FML 4.0.42 的每方法事件类型分流，schema 2 保留 403 类/816 方法（MOD 168、GAME 648），纠正模板模型/染色三项与维度效果注册一项；Fabric metadata 补齐 36 个 Jade 与 1 个 JEI 可选插件入口，保持可选依赖。详见 [事件闭包审计](fabric-event-closure-audit.md) 与 [完成度审计](fabric-completion-audit.md)，真实插件发现 API 及 Loader 懒创建检查见 `optional-plugin-discovery-audit.json`。真实 CLIENT Knot 中的 403 个订阅类定义与方法签名解析均通过，处理函数没有被执行，游戏初始化仍被守卫禁止。

Fabric-only 断线翻译放在独立资源命名空间 `assets/stardewcraft_fabric/lang`，保持 12 个语言文件同键；共享主线语言资源没有因本次移植被覆盖。

## 参考报价

以下是针对本工程的报价建议，不是已成交价格或 Minecraft 移植市场统计。当前主线范围冻结后，完整商业交付建议报价 48000 元，合理谈判区间约 30000—60000 元。普通人工估算 15—30 人日；现成 Forge 经验、脚本和并行执行可压缩执行时间，功能责任与验证范围保持一致。

| 项目 | 金额（人民币） |
| --- | ---: |
| 构建、依赖、注册与配置 | 6000 元 |
| 事件、维度、持久化与机器能力 | 12000 元 |
| 网络、菜单与进入世界前握手 | 7000 元 |
| 模型、GUI/HUD、渲染与约定集成 | 13000 元 |
| 专服/联机回归、安装说明、交付与 14 天移植缺陷修复 | 10000 元 |
| 合计 | 48000 元 |

交付条件：固定的 1.21.1 基线、可复现 Fabric 工程及完整安装产物、双方约定功能与兼容矩阵、启动/保存/重开/联机/资源加载记录。14 天从验收交付开始，限本次移植引入的问题；新功能、其他 Minecraft 版本、后续主线持续追更另计。已有上游缺陷逐项记录并单独定范围，不以承接移植为由无限包修。24 小时作为冲刺目标，不在缺少证据时写成完整等价交付保证。

价格参照仅用于校准人工量级：[程序员客栈短期雇佣说明](https://support.proginn.com/shortterm/qa/)给出开发者自行设置的 300—2000 元/天范围；该范围并非 MC 专项报价。上述分项价格由本工程范围、专项经验与验收责任估算。

参考：[Fabric 1.21.1 文档](https://github.com/FabricMC/fabric-docs/tree/main/versions/1.21.1)、[Fabric 开发入口](https://fabricmc.net/develop/)、[Porting Lib](https://github.com/Fabricators-of-Create/Porting-Lib)。Porting Lib 仅按已核实源码和 API 引入上述固定模块；不会默认引入重叠的完整 fluids/transfer/models 平台。
