# StardewCraft 1.20.1 Forge 迁移与双版本维护规划

状态：规划稿 1.1（已进入分层实施）
目标版本：Minecraft 1.21.1 + NeoForge、Minecraft 1.20.1 + Forge
主开发线：`main`（1.21.1 NeoForge）
长期支持线：`1.20.1-forge`

当前实施状态（2026-09-21）：Forge worktree 已建立，已完成入口/构建骨架、301 个物品注册对照，以及 12 个纯建筑材质方块、16 个矿井背景纯方块、2 个特殊纯方块、15 个生成树 root/log/branch 核心方块和完整 85 个生成树木制建筑件。树核心已带回原 `NewTreePartBlock` / `NewTreeLogBlock` 行为、`new_tree_part` 方块实体和原 NBT marker 合同；木制建筑件保留源注册顺序、方块类型、属性、配方、掉落、标签和资源闭包。树叶、树苗与树生成/砍伐运行时仍是后续切片。这些数字只表示已经进入 Forge source set 的范围，不代表全模组功能已迁移。

## 1. 结论

StardewCraft 不采用“复制一份目录后各改各的”，也不在当前四千多个 Java 文件中全面引入条件编译。

本项目采用以下组合：

1. `main` 继续作为 1.21.1 NeoForge 的主开发线和设计权威。
2. 新建长期分支 `1.20.1-forge`，只承载 Minecraft 1.20.1 Forge 的适配和发布。
3. 两个分支分别放在两个 Git worktree 中开发，避免反复切分支、混用运行目录和误加载错误版本的模组。
4. 通用业务修复通过小提交和 `cherry-pick -x` 同步，不定期把整个 `main` 合并进旧版本分支。
5. 网络、物品状态、事件、能力、注册和客户端渲染等高差异区域建立 StardewCraft 自己的窄兼容边界。
6. 资源目录和可机械转换的源码由幂等脚本生成或改写，禁止长期手工维护两份几乎相同的结果。
7. 两个版本各自构建、测试、打包和实机验收；“玩家可见功能等价”不等于二进制、存档和内部实现相同。

这套结构的目标不是让两个版本共享每一行代码，而是让真正相同的功能容易同步，让必然不同的底层实现集中在可控范围内。

## 2. 目标与非目标

### 2.1 必须实现

- 同一个 `stardewcraft` mod ID 和稳定的内容 ID。
- 两个版本都能独立构建、启动、创建新世界和进入星露谷维度。
- 核心玩法、GUI、NPC、建筑、农场、矿井、战斗、钓鱼、节日、动物、机器和联机逻辑达到功能矩阵约定的等价状态。
- 1.20.1 Forge 拥有独立的 GameTest、专用服务器、客户端和发布验证链路。
- JEI、Jade、地图、AE2、CTM、渲染优化和光影等已支持集成，逐项给出 1.20.1 Forge 的适配结论。
- 公开 API、示例附属和浣熊附属有明确的版本构建策略。
- 后续新增功能有固定的双版本开发和回移流程。

### 2.2 不承诺

- 不支持使用 1.20.1 客户端连接 1.21.1 服务端，反之亦然。
- 不承诺把 1.21.1 存档降级到 1.20.1。
- 不要求两个版本使用相同的底层存储方式；例如 1.21.1 可使用 Data Component，1.20.1 可使用 namespaced NBT。
- 不为了表面上的“单源码树”引入大量难以阅读的版本条件。
- 不在迁移初期同时重做 Git LFS、发布平台、资源组织和所有公共 API。

## 3. 当前基线与迁移量

以下数字是 2026-09-20 对当前工作目录的粗略盘点，用来确定工作流，不作为最终完成统计：

| 项目 | 当前规模 | 迁移含义 |
| --- | ---: | --- |
| Java 文件 | 约 4,292 | 不适合全面手工双写 |
| 直接导入 NeoForge 的已跟踪文件 | 约 1,475 | 大量机械替换和事件签名差异 |
| 新版 Payload/StreamCodec 相关文件 | 约 438 | 必须集中设计 Forge SimpleChannel 适配 |
| Data Component 直接使用文件 | 约 162 | 需要 NBT/旧物品 API 等价实现 |
| `HolderLookup.Provider` 使用文件 | 约 161 | 1.20.1 NBT 方法签名需要批量回退 |
| Mixin 文件 | 约 126 | 每个目标、方法描述符和第三方版本都要复核 |
| 结构 NBT | 约 407 | 目录名、DataVersion 和 GameTest 模板均需验证 |
| 主资源目录 | 约 682 MB | 两个 worktree 会占用实际磁盘空间 |
| 预生成区域 | 约 108 MB | 必须建立 1.20.1 专用版本，不能直接共用 |

当前工作树还有大量未提交的玩法、NPC、建筑、渲染和预生成地图改动。迁移分支必须从一个明确、可复现、已验证的提交建立，不能直接把未收束工作树当作长期基线。

## 4. Git 与本地目录方案

### 4.1 分支职责

| 分支 | 职责 | 合入规则 |
| --- | --- | --- |
| `main` | 1.21.1 NeoForge 主开发、设计权威、正常发布 | 常规功能和修复先在这里完成 |
| `1.20.1-forge` | 1.20.1 Forge 长期支持和发布 | 只接收经过选择的通用提交及 Forge 专属实现 |

迁移过程可以在 `1.20.1-forge` 上直接进行；不使用带个人、工具或会话名称的永久分支名。

### 4.2 本地 worktree

规划目录：

```text
/Users/jiayuhan/游戏制作/
├── StardewCraft/                  # main，1.21.1 NeoForge
└── StardewCraft-1.20.1-Forge/     # 1.20.1-forge
```

建立基线提交后使用：

```bash
git branch 1.20.1-forge <baseline-commit>
git worktree add ../StardewCraft-1.20.1-Forge 1.20.1-forge
git config rerere.enabled true
```

约束：

- 两个 worktree 分别用独立 IDE 窗口打开。
- 各自保留自己的 `run/`、`run-game-test/`、`build/` 和 Gradle 项目缓存。
- 不把一个版本的 `mods/`、配置文件或测试世界复制到另一个版本。
- 不用软链接共享生成资源或预生成地图；共享源应通过 Git 提交和转换脚本同步。
- `.mca`、结构 NBT、Mixin 配置和平台元数据默认视为版本敏感文件。

### 4.3 提交同步

通用提交应保持小而完整，例如：

```text
fix(npc): keep route state after interior transition
feat(farm): add beach forage spawn layout
fix(network): reject stale building revision
```

同步到 1.20.1：

```bash
git cherry-pick -x <main-commit>
```

`-x` 会记录来源提交，便于以后判断某个修复是否已经回移。发生版本差异时，在同一个 cherry-pick 中完成 Forge 适配，不把半成品同步提交留在长期分支。

禁止把积累数周的 `main` 整体 merge 到 `1.20.1-forge`。Minecraft API、资源格式和 Mixin 目标一旦分叉，整体合并会产生大量无意义冲突。

## 5. 双版本代码边界

### 5.1 三类代码

每个系统应逐渐归入三类，而不是一次性重构整个工程。

#### A. 业务与规则代码

典型内容：季节计算、价格、任务条件、NPC 日程决策、建筑规则、战斗数值、掉落表选择。

要求：

- 尽量不直接依赖 Forge/NeoForge 类。
- 能以普通 Java 单元测试验证的部分优先保持两分支一致。
- 修改时优先形成可直接 cherry-pick 的提交。

#### B. Minecraft 适配代码

典型内容：实体、方块、物品、菜单、渲染器、SavedData、NBT、资源加载。

要求：

- 两分支保留相同的包和类职责，方便对照。
- 方法签名可以不同，但玩家可见行为和稳定 ID 应一致。
- 不为了追求源码完全相同而引入脆弱反射。

#### C. 加载器与版本桥接代码

集中放置：

- 注册器包装
- 游戏/模组事件入口
- 网络注册、发送和处理上下文
- 能力与物品栏访问
- 物品持久化字段
- 客户端扩展和模型数据
- 配置注册
- 第三方模组探测

新兼容层必须窄且有真实调用者，不先设计一个包揽全部 Forge/NeoForge API 的抽象框架。

### 5.2 不采用统一 common Minecraft 模块的原因

1.20.1 和 1.21.1 的 Mojang 类、方法签名、资源规则和 Java 版本均不同。让同一个 Minecraft-aware `common` 模块同时编译到两边，需要条件编译或大量 facade，最终只是把分支差异隐藏到更难维护的位置。

后续可以抽取真正纯 Java 的 `core` 模块，但只在迁移中自然出现稳定边界时进行，不把它设为启动迁移的前置工程。

## 6. 主要技术工作流

### 6.1 构建与运行环境

1.21.1 NeoForge 保持：

- Java 21
- ModDevGradle
- `neoforge.mods.toml`
- NeoForge 21.1.x

1.20.1 Forge 建立：

- Java 17
- ForgeGradle 6
- `mods.toml`
- Forge 47.4.x
- MixinGradle/annotation processor/refmap
- 1.20.1 对应 Parchment 映射

初始目标使用 Forge 官方推荐版本作为编译下限，并在 CI 增加最新 47.4.x 的运行验证。最终最低版本根据依赖模组和实际 API 使用确定，不只按“能编译”决定。

### 6.2 Java 21 回退 Java 17

迁移脚本和编译检查应覆盖：

- `List#getFirst/getLast/removeFirst/removeLast/reversed` 等 Java 21 集合 API。
- 可能存在的 Java 21 语言语法。
- Java 21 才存在的库方法。
- 依赖 JAR 的 class file 版本。

Deque 自身已有的 `addFirst/removeFirst` 不应被误改。所有替换应基于编译类型或人工确认，不能仅做字符串全局替换。

### 6.3 注册与事件

机械部分：

- `net.neoforged.*` 包迁移到 `net.minecraftforge.*`。
- `DeferredHolder/DeferredBlock/DeferredItem` 映射到 Forge 1.20.1 的 `RegistryObject` 和 `DeferredRegister`。
- 模组构造器和事件总线取得方式改回 Forge 1.20.1 模式。
- `NeoForge.EVENT_BUS` 改为 `MinecraftForge.EVENT_BUS`。

语义部分必须逐项复核：

- tick 的 START/END 阶段。
- 伤害事件触发顺序。
- 玩家克隆、维度切换和登录同步。
- 方块破坏、掉落和交互取消结果。
- 客户端渲染阶段。
- 数据包同步和 reload listener 时机。

### 6.4 网络

这是最高优先级兼容层。

1.21.1 当前模型：

- `CustomPacketPayload`
- `StreamCodec`
- `RegisterPayloadHandlersEvent`
- `IPayloadContext`

1.20.1 Forge 模型：

- `SimpleChannel`
- `FriendlyByteBuf`
- 数字 discriminator
- `NetworkEvent.Context`
- `PacketDistributor`

迁移策略：

1. 保留数据 record 和现有字段验证。
2. 把发送目标、方向、编码器、解码器、处理器描述集中为 StardewCraft 自己的 packet spec。
3. 1.21.1 适配器把 spec 注册为 Payload。
4. 1.20.1 适配器把 spec 注册到 SimpleChannel。
5. 数字 discriminator 由稳定清单生成，不依赖运行时扫描顺序。
6. 保持当前服务端权威验证、主线程调度、最大长度和区块已加载检查。
7. 配置阶段的宠物/附属能力握手在 Forge 1.20.1 单独设计，不假定旧网络协议存在同一阶段。

迁移成功不要求两版本网络格式互通；要求同一版本客户端和服务端安全、稳定地工作。

### 6.5 物品数据和 Data Component

1.20.1 没有 1.21 的 Item Data Component 系统。处理方式按语义分类：

| 1.21.1 用途 | 1.20.1 实现 |
| --- | --- |
| StardewCraft 自定义状态 | namespaced NBT 子标签 |
| 自定义名称、Lore、损伤等旧有字段 | 1.20.1 ItemStack/Tag API |
| 食物、堆叠上限等物品定义属性 | 1.20.1 Item 属性或专用逻辑 |
| 网络中的 ItemStack | Forge 1.20.1 FriendlyByteBuf ItemStack 编码 |
| 数据迁移版本 | 保留 StardewCraft 自己的 schema/version 字段 |

不得在业务代码中散布两套 key。应提供按语义命名的方法，例如“读取品质”“写入武器状态”“读取建筑蓝图 ID”，由各分支选择 Component 或 NBT。

### 6.6 NBT、Codec 和 Holder

1.21.1 中带 `HolderLookup.Provider` 的实体、方块实体、SavedData 和 ItemStack 保存方法，需要按 1.20.1 签名恢复。

批量迁移后必须验证：

- 方块实体卸载重载。
- 玩家退出重进。
- 服务端重启。
- 跨维度移动。
- 列表和注册表引用的序列化。
- 老存档缺字段时的默认值。

### 6.7 资源与数据包

建立 `tools/porting/` 下的幂等检查/转换工具，负责生成或验证 Forge 资源：

| 1.21.1 | 1.20.1 |
| --- | --- |
| `recipe/` | `recipes/` |
| `loot_table/` | `loot_tables/` |
| `structure/` | `structures/` |
| `advancement/` | `advancements/` |
| `tags/item/` | `tags/items/` |
| `tags/block/` | `tags/blocks/` |
| `c:` 通用标签 | 对应 `forge:` 标签或显式兼容标签 |
| pack format 34 | pack format 15 |

转换工具只处理已确认可机械转换的内容。配方 serializer、战利品函数、组件字段、维度/世界生成 JSON 和结构 NBT 必须由专门检查验证。

语言、纹理、声音和大部分普通模型应保持内容一致；版本分支只保留必要差异。

### 6.8 世界、预生成区域和存档

- 1.20.1 Forge 使用独立的新测试世界。
- 1.21.1 `.mca` 不直接复制到 1.20.1 发布物。
- 1.20.1 中重新导出或验证所有需要发布的预生成区域。
- 两版本分别维护 pregen version 和 manifest 哈希。
- 发布检查阻止把另一个版本的 manifest 或 region 集合误装进 JAR。
- 结构 NBT 的目录、DataVersion、方块/实体字段和 GameTest 加载都要验证。
- 若未来支持 1.20.1 存档升级到 1.21.1，作为单独的单向迁移项目处理。

### 6.9 Mixin 和客户端渲染

Mixin 分三批处理：

1. 服务端和核心 Vanilla Mixin。
2. 客户端 Vanilla Mixin。
3. 第三方模组内部 Mixin。

每个 Mixin 必须记录：

- 目标类。
- 目标方法和描述符。
- 1.20.1 对应位置。
- 是否仍然需要。
- 无目标模组时是否安全跳过。
- 自动化证据和实机观察目标。

1.21.1 的 Sodium/Iris 适配不能原样搬到 Forge 1.20.1。Forge 版本需要针对实际采用的 Embeddium/Oculus 或其他渲染栈重新调查。基础 Forge 客户端必须先在没有这些可选模组时通过，随后再恢复等价兼容。

### 6.10 外部依赖与附属

建立依赖矩阵，至少包含：

- JEI
- Jade
- GeckoLib
- AppleSkin
- Curios
- AE2
- Create
- CTM
- Xaero Minimap/World Map
- Embeddium/Oculus 或对应渲染栈

每项记录：

- 1.20.1 Forge 版本和下载来源。
- 编译依赖还是软依赖。
- API 是否改变。
- 是否存在内部 Mixin。
- 无该模组时是否能启动。
- 组合测试状态。

公开 API 保持包名和语义稳定，但发布两个不同的开发工件。示例附属和浣熊附属分别维护 NeoForge 1.21.1 与 Forge 1.20.1 构建，不允许一个 JAR 宣称同时兼容两个版本。

## 7. 分阶段实施与完成门槛

### 阶段 0：冻结迁移基线

工作：

- 收束当前未提交改动。
- 按现有规则运行相应编译、资源检查和定向测试。
- 明确哪些内容进入迁移基线。
- 创建可复现的基线提交。

完成门槛：

- 工作树边界明确。
- 基线提交能从干净检出完成现有验证。
- 记录基线 commit ID。

### 阶段 1：Forge 构建骨架

工作：

- 建立 `1.20.1-forge` worktree。
- 配置 Java 17、ForgeGradle、映射、`mods.toml` 和 Mixin refmap。
- 建立独立运行目录和最小 CI。
- 完成依赖矩阵第一版。

完成门槛：

- Gradle 配置可解析。
- 最小模组能在纯 Forge 1.20.1 客户端和专服加载。
- JAR 元数据正确标识 1.20.1 Forge。

### 阶段 2：机械迁移和错误分类

工作：

- 包名、ResourceLocation、Java 17 集合 API、注册包装和 NBT 签名的受控转换。
- 生成编译错误分类报告。
- 建立禁止项检查，避免 1.21-only API 回流。

完成门槛：

- 所有错误都有明确类别和负责人/处理阶段。
- 重复机械错误由脚本解决，不继续逐文件手改。

### 阶段 3：注册、数据加载和服务端启动

工作：

- 方块、物品、实体、方块实体、菜单、粒子、声音、效果和配置注册。
- reload listener、SavedData、维度和基础资源加载。

完成门槛：

- 专用服务器启动并完成数据包加载。
- 注册 ID 清单与主线预期一致。
- 新世界可以创建并保存重启。

### 阶段 4：物品数据、玩家数据和网络

工作：

- Data Component→NBT/旧 API 适配。
- SimpleChannel 网络实现。
- 登录、重连、维度切换和附属握手。

完成门槛：

- 同版本客户端/服务端协议完整。
- 关键物品退出重进后不丢数据。
- 恶意长度、非法 ID、未加载区块等输入仍被拒绝。

### 阶段 5：核心玩法纵向切片

建议按可以真正游玩的顺序恢复：

1. 进入星露谷与农场创建。
2. 时间、季节、体力、睡眠和晕倒。
3. 种植、采集、机器和库存。
4. NPC、建筑和商店。
5. 钓鱼、矿井、战斗和怪物。
6. 动物、节日、社区中心、任务和后期系统。

每个切片同时恢复服务端逻辑、网络、资源、GUI 和存档，避免出现“所有系统都编译了一半但没有一条完整玩法链”的状态。

完成门槛：

- 对应功能矩阵中的自动化和实机项均有结果。
- 不以临时禁用核心逻辑作为通过。

### 阶段 6：客户端、Mixin 和可选集成

工作：

- GUI、HUD、动画、渲染、输入和 Vanilla Mixin。
- JEI/Jade 等 API 集成。
- 地图、AE2、CTM 和渲染优化/光影集成。

完成门槛：

- 纯 Forge 环境稳定。
- 每个声明支持的可选模组组合有启动和目标行为记录。
- 不支持的组合明确拒绝或不声明支持。

### 阶段 7：资源、结构和预生成地图

工作：

- 完成数据目录转换和 schema 修订。
- 恢复 GameTest 结构。
- 建立 1.20.1 专用预生成世界资源。

完成门槛：

- 资源审计没有错误版本目录。
- GameTest 能实际发现并执行要求的测试数量。
- 新存档和重启后的预生成地图通过客户端观察。

### 阶段 8：全功能等价与发布

工作：

- 清空功能矩阵中的未决核心项。
- 验证示例附属和正式附属。
- 从干净检出运行 Forge 分支的完整 CI。
- 生成版本明确的发布 JAR 和说明。

完成门槛：

- Forge 1.20.1 与 NeoForge 1.21.1 均有独立通过记录。
- 所有必需功能达到约定等级。
- 客户端、专服、联机、重启和存档边界分别说明。
- 发布物不混入另一版本的元数据、类或世界资源。

## 8. 功能等价矩阵

在迁移分支建立 `docs/porting/1.20.1-feature-parity.md`，每行只描述一个可观察能力：

| 子系统 | 功能 | 1.21.1 基准 | 1.20.1 实现 | 自动检查 | 客户端/联机检查 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 农场 | 创建标准农场 | 当前行为 | Forge 实现路径 | GameTest | 新存档进入并重启 | planned |
| 建筑 | 筒仓放置与阻挡反馈 | 当前行为 | Forge 网络/NBT | 定向测试 | 联机放置 | planned |
| NPC | 日程与室内门通行 | 当前行为 | Forge 实体/导航 | GameTest | 观察完整日程 | planned |

状态只使用：

- `planned`
- `compiling`
- `headless-passed`
- `client-passed`
- `multiplayer-passed`
- `blocked`
- `complete`

`complete` 必须满足该行约定的全部检查，不能因为编译成功或某个 GameTest 通过就提前填写。

## 9. 双版本日常开发流程

### 9.1 新功能

1. 在 `main` 完成设计和 1.21.1 NeoForge 实现。
2. 尽量把纯规则、资源内容和平台调用拆成独立提交。
3. 主线相应检查通过后，把可移植提交 `cherry-pick -x` 到 `1.20.1-forge`。
4. 在 Forge 分支补齐平台差异。
5. 更新功能矩阵并执行该版本验证。

若功能必须两个版本同时发布，可以在主线设计完成后并行实现，但仍以两个独立可验证提交链结束。

### 9.2 Bug 修复

- 只存在于一个版本：在对应分支修复并记录版本原因。
- 两版本共有：优先在能够复现问题的分支修复，再把最小修复同步到另一个分支。
- 底层不同但玩家现象相同：允许两套实现，提交信息使用相同问题描述。

### 9.3 资源修改

- 纹理、语言、声音和源模型内容先在主线修改。
- 运行转换/同步工具生成 Forge 资源。
- 两个版本分别运行资源检查。
- 世界区域和版本敏感 NBT 不走普通资源同步。

### 9.4 发布节奏

两个版本共享内容版本号，但允许补丁号暂时不同。例如：

```text
stardewcraft-0.7.0+mc1.21.1-neoforge.jar
stardewcraft-0.7.0+mc1.20.1-forge.jar
```

若只有 Forge 修复：

```text
stardewcraft-0.7.0-forgefix1+mc1.20.1-forge.jar
```

发布说明必须明确：Minecraft 版本、加载器、最低加载器版本、支持的附属版本和存档限制。

## 10. CI 与验证

两个长期分支各自拥有适合自己的 workflow，不在单个 checkout 中强行编译两个巨大工程。

共同门槛：

- 干净检出。
- 固定 Java 版本。
- `build` 与显式 `check`。
- 翻译完整性检查。
- 兼容性脚本。
- GameTest 服务端。
- 明确检查实际发现并执行的测试数量。
- 示例附属构建。

Forge 分支额外检查：

- Java 17 class file 审计。
- 禁止 NeoForge 类和 `neoforge.mods.toml` 进入 JAR。
- 禁止 pack format 34 和 1.21 单数数据目录进入 JAR。
- Mixin refmap 存在且能在重混淆 JAR 中工作。
- 以最低支持 Forge 版本编译，并在最新兼容 47.4.x 上进行运行烟雾测试。
- 1.20.1 专用 pregen manifest 和 region 文件一致性检查。

自动化证据之外，以下仍需分别实机确认：

- 客户端视觉与 GUI。
- 存档重启。
- 局域网/专服联机。
- 门、传送和跨维度。
- 渲染优化及光影组合。
- 预生成地图安装和迁移。

## 11. 风险与止损规则

| 风险 | 控制方式 |
| --- | --- |
| 当前工作树混入迁移基线 | 先冻结、验证并提交明确基线 |
| 两版本改动长期漂移 | 小提交、`cherry-pick -x`、功能矩阵 |
| 条件编译淹没业务代码 | 使用版本分支，只在窄边界抽象 |
| 网络类数量巨大 | 先建立统一 packet spec 和转换工具 |
| Data Component 大面积缺失 | 按语义建立 ItemState 访问层 |
| 结构/世界降级损坏 | 独立 1.20.1 资源和新存档 |
| Mixin 静默失效 | 描述符审计、refmap、启动和行为检查 |
| 可选模组内部类变化 | 按具体版本建兼容矩阵，默认可安全缺失 |
| 迁移期间主线继续变化 | 以基线迁移，之后按小提交回移，不反复重置 |
| 仓库二进制继续膨胀 | 迁移完成后单独评估 Git LFS，不与本次端口混做 |

如果某个子系统连续多轮只能靠禁用才能启动，应停止继续堆补丁，回到该子系统的兼容边界重新设计。

## 12. 第一批实际工作

开始实施时按以下顺序，不跳步：

1. 列出当前工作树的在研主题、完成状态和应否进入迁移基线。
2. 完成当前 1.21.1 改动所需的验证。
3. 在获得提交授权后建立迁移基线提交。
4. 创建 `1.20.1-forge` 分支和独立 worktree。
5. 建立 ForgeGradle/Java 17/Mixin 的最小构建。
6. 输出第一次完整编译错误报告，不在这一步追求一次修完。
7. 根据实际错误数量确定机械转换规则和兼容层的第一版边界。
8. 先打通“启动专服→创建新世界→加载注册与数据”的纵向切片。
9. 再进入网络、物品数据和各玩法系统迁移。

## 13. 外部参考

- Forge 1.20.1 下载与版本：<https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html>
- Forge 1.20.1 注册：<https://docs.minecraftforge.net/en/1.20.1/concepts/registries/>
- Forge 1.20.x SimpleImpl 网络：<https://docs.minecraftforge.net/en/1.20.x/networking/simpleimpl/>
- Forge 能力系统：<https://docs.minecraftforge.net/en/latest/datastorage/capabilities/>
- NeoForge 21.0/MC 1.21 迁移变化：<https://neoforged.net/news/21.0release/>
- Sodium 的主干与版本发布分支策略：<https://github.com/CaffeineMC/sodium/wiki/Nightly-Builds>
- JEI 多版本仓库：<https://github.com/mezz/JustEnoughItems>
- Stonecutter 多版本/多加载器模板：<https://github.com/rotgruengelb/stonecutter-mod-template>

## 14. 已确定的架构决策

| 决策 | 结果 |
| --- | --- |
| 主开发版本 | 1.21.1 NeoForge |
| 旧版本线 | 1.20.1 Forge |
| 永久分支名 | `main`、`1.20.1-forge` |
| 本地管理 | 两个 Git worktree |
| 跨版本同步 | 小提交 + `cherry-pick -x` |
| 全量 common/Stonecutter | 不采用 |
| 局部兼容层 | 网络、物品状态、事件、能力、注册、客户端平台接口 |
| 世界文件 | 每个 Minecraft 版本独立 |
| 存档方向 | 不支持 1.21.1→1.20.1 降级 |
| 完成定义 | 功能矩阵中的自动化、客户端、联机和重启门槛分别通过 |

本规划在第一次 Forge 完整编译和依赖矩阵完成后更新为 1.1；只有实际编译证据才能决定最终转换脚本范围和兼容层数量。
