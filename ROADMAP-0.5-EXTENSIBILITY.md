# StardewCraft 0.5.0 开放接口与数据驱动路线图

## 1. 版本目标

0.5.0 不只继续补原版内容，而是先把 StardewCraft 从“只有本体能继续写内容”改造成“数据包和附属 Mod 都能沿用本体系统继续做内容”的平台版本。

开放分成三层：

| 层级 | 能负责什么 |
| --- | --- |
| 数据包 | 定义任务、条件、物品池、奖励、触发器、商店库存、配方等已有行为的组合 |
| Java API | 注册新的行为类型、动态数据提供器、自定义机制和事件监听 |
| 资源包 | 模型、贴图、翻译、声音等客户端资源 |

数据包只能让已经注册到 Minecraft 注册表中的物品参与星露谷系统。新增物品、方块、实体、菜单或声音本身仍然需要 Mod。

## 2. 当前仓库的主要问题

| 问题 | 当前入口 | 0.5.0 的处理方式 |
| --- | --- | --- |
| 星露谷物品身份依赖本体 Java 类 | `IStardewItem` | Item Data Map + 动态 Provider + 旧接口兼容层 |
| 任务定义和运行状态耦合、类型写死 | `QuestDataLoader`、`QuestManager` | 命名空间任务定义注册表 + 可注册目标类型 |
| 每个系统各写一套条件判断 | `PreconditionEvaluator` 等 | 共用 Condition 系统 |
| 奖励、发信、开任务等效果散落在业务代码 | 各任务、邮件、剧情服务 | 共用 Action 系统 |
| 随机物品选择和物品分类大量硬编码 | 日常任务、商店、掉落等 | 共用 Item Query + 标签/Data Map |
| 商店、邮件、剧情、特殊订单只有本体能加 | 对应 Loader/Registry/Service | 可重载定义注册表 + Java 行为注册表 |
| 存档长期依赖数字 ID 或内部类 | 任务等旧系统 | `namespace:path` ID + 旧存档迁移适配器 |

其中 `IStardewItem` 是第一优先级：目前大量生产、售价、分类逻辑通过 `instanceof IStardewItem` 判断，附属 Mod 的普通物品即使数据正确也无法进入这些流程。

## 3. 全局兼容规则

### 3.1 ID

- 所有新定义、条件类型、动作类型和 Provider 都使用 `ResourceLocation`。
- 本体内容使用 `stardewcraft` 命名空间，附属 Mod 必须使用自己的命名空间。
- 旧数字 ID 和普通字符串 ID 在对应系统完成迁移前继续可读，但不再作为新接口格式。

### 3.2 数据加载

- 结构化数据统一使用 Mojang `Codec`，不再新增斜杠分隔字符串和临时 Gson 解析。
- 挂在 Minecraft 注册表对象上的静态元数据使用 NeoForge Data Map。
- 任务、商店、邮件等独立内容使用服务端权威的可重载定义注册表。
- 未知的条件、动作、目标类型必须给出文件、定义 ID 和类型 ID，且该条定义不生效。
- 一次重载必须原子替换：新快照校验失败时，不能把半套数据写进运行状态。

### 3.3 覆盖和存档

- 同 ID 完整定义按正常数据包优先级覆盖。
- 数组默认整体替换；只有 schema 明确声明时才允许合并。
- Java 注册遇到重复 ID 直接拒绝，不能由 Mod 加载顺序暗中决定结果。
- 定义和玩家进度分开存储；存档只保存定义 ID 和可变进度。
- 定义被移除时保留一个可取消的“失效内容”状态，不能删进度或导致存档崩溃。

### 3.4 公共 Java API

- 稳定接口放在 `com.stardew.craft.api.v1`。
- 内部 Manager 可以实现公共接口，但内部类本身不承诺兼容。
- 破坏性 API 修改通过新的 `api.v2` 等包版本发布。

## 4. 三个共用底层系统

这三个系统对应原版 Stardew Valley 的 `GameStateQuery`、`ItemQueryResolver` 和 `TriggerActionManager` 思路。后续任务、商店、邮件、剧情不再分别发明自己的条件和奖励语法。

### 4.1 Condition

回答“这条内容现在能不能出现”。首批内置条件包括日期、时间、天气、玩家/农场年龄、技能、金钱、物品/标签、好感、邮件/Flag、事件、任务、地点和房主权限。

目标 Java 接口：

```java
StardewConditions.register(ResourceLocation id, StardewConditionType type);
```

### 4.2 Item Query

回答“这次应当选择哪些物品”。支持直接物品 ID、物品标签、权重池和附属 Mod 注册的动态解析器。

目标 Java 接口：

```java
StardewItemQueries.register(ResourceLocation id, StardewItemQueryType type);
```

### 4.3 Action

负责服务端状态修改，例如给予物品/金钱、开始任务、设置 Flag、发送邮件、改变好感和调用已注册机制。

目标 Java 接口：

```java
StardewActions.register(ResourceLocation id, StardewActionType type);
```

客户端剧情演出命令与服务端状态 Action 必须分开。客户端请求执行状态变化时，必须携带服务端正在执行并已授权的事件及命令标识。

## 5. 开发阶段

### 第一阶段：平台地基

- [x] 写明 0.5.0 兼容规则、阶段和验收口径。
- [x] 注册可同步到客户端的 NeoForge Item Data Map。
- [x] 提供稳定的物品元数据 API、动态 Provider 和 `IStardewItem` 兼容层。
- [x] 将 `DailyQuestGenerator` 的售价查询迁移到新 API，证明真实调用链可用。
- [x] 实现带命名空间、Codec 和校验信息的 Condition 类型注册表。
- [x] 实现 Item Query 类型注册表。
- [x] 实现 Action 类型注册表。
- [x] 抽取独立定义共用的 reload 快照、诊断和原子替换框架。
- [x] 确定共用的服务端到客户端定义同步方案。
- [x] 给剧情中的服务端 Action 增加“当前事件已授权”校验。
- [x] 补 Codec 往返、未知类型、重复 ID 和专用服务器启动测试。
- [x] 补原子 reload 和剧情 Action 授权测试。
- [x] 补数据包覆盖优先级测试。
- [x] 提供一个示例数据包和一个示例附属 Mod。

验收标准：

1. 第三方已有物品能只靠数据包获得星露谷分类、售价和食物属性。
2. 附属 Mod 能通过命名空间 Provider 提供随 `ItemStack` 变化的属性。
3. Condition、Item Query、Action 都能由附属 Mod 注册，而不修改本体内部代码。
4. 无效内容能指出文件、定义和错误类型，并且不会半加载。

### 第二阶段：任务系统作为第一套完整样板

- [x] 用可重载、带命名空间的定义注册表替换只读本体资源的 `QuestDataLoader`。
- [x] 分离 `QuestDefinition` 与玩家的 `QuestState`。
- [x] 用注册式目标类型替代数字任务类型。
- [x] 用共用 Condition/Action 表达任务发布、完成、奖励和后续任务。
- [x] 把日常委托的 NPC、物品、鱼类、资源、怪物池和概率搬进数据。
- [x] 旧数字任务 ID 迁移为 `stardewcraft:<legacy-id>`。
- [x] 保留并完善 validate、grant、complete、reset、inspect 调试指令。

验收标准：数据包能加入一整条任务链；附属 Mod 能注册一个新的任务目标类型。

### 第三阶段：商店与剧情内容

- [x] 提供可重载商店、条目、金币/物品交易、出现条件和静态库存格式。
- [x] 为旅行货车、书商等动态库存提供 Java Provider。
- [x] 数据化 NPC/地点到“打开商店”的交互绑定。
- [x] 开放邮件命名空间，并用共用 Condition/Action 处理送达和阅读效果。
- [x] 给剧情 ID 加命名空间，开放触发器、前置条件和演出命令注册。
- [x] 将特殊订单接入可重载注册表，并开放目标和奖励类型。
- [x] 将本体 57 个静态商店库存从 `ShopRegistry` Java 兼容表搬入数据文件，动态日库存保留运行时 Provider/算法。
- [x] 将 16 条本体特殊订单从 `SpecialOrderDefinitions` Java 兼容表搬入数据文件。

验收标准：数据包无需 Java 代码即可新增商店、邮件、剧情和特殊订单。外部扩展与本体内容迁移均已完成。

### 第四阶段：生产与收集系统

- [x] 将现有 13 类机器配方升级为命名空间、原子重载、可诊断定义，并支持一个文件一条配方。
- [x] 机器分类输入改用公共物品元数据 API，第三方物品可通过 Data Map/Provider 进入本体机器。
- [x] 迁移烹饪、合成和配方解锁来源数据。
- [x] 迁移钓鱼宝箱、鱼塘、博物馆奖励、探险家公会目标和藏书。
- [x] 用物品标签、Data Map、Item Query 替换通用业务流程中按 Java 类和路径判断分类的代码。
- [x] 将晶球、兑奖和普通矿井层奖励迁移到 Loot/Item Query 数据。
- [x] 将蚯蚓点、采集、采石场和骷髅矿井奖励池迁移到结构化数据。

验收标准：附属物品仅通过元数据和标签即可进入完整生产与收集循环。

### 第五阶段：NPC、节日与世界内容

- [x] 给 NPC 定义加入命名空间并开放有序交互 Provider。
- [x] 节日基础信息进入命名空间数据、原子 reload 与客户端同步，复杂小游戏继续由可注册 Java Handler 承担。
- [x] 数据化矿井层主题、生成池和宝箱表，保留 Java 地形算法并开放怪物 Provider。
- [x] 数据化室内、地点和传送门元数据；明确预生成地图只开放元数据，结构平移与触发器放置仍由世界迁移负责。

### 第六阶段：高级附属 Mod API

- [x] 提供作物、树木、动物、建筑的同步 Data Map 和动态行为 Provider。
- [x] 提供武器/装备元数据以及技能、效果 Handler。
- [x] 数据化精通奖励列表和职业效果元数据，并同步客户端 UI 所需快照。
- [x] 在存档、网络和 UI 中的技能枚举被移除前，不承诺运行时创建任意全新核心技能，并在 API 文档明确该边界。

## 6. 系统优先级

| 优先级 | 系统 |
| --- | --- |
| P0 | 共用 Condition/Action/Item Query、ID、Codec、同步、物品元数据、存档迁移 |
| P1 | 任务、日常委托、商店、邮件、剧情、特殊订单 |
| P2 | 机器、烹饪、合成、钓鱼、鱼塘、博物馆、公会目标、藏书、掉落池 |
| P3 | NPC、节日、社区中心、矿井内容、作物、树木、动物、建筑、室内系统 |
| 后置 | 动态技能/职业、任意 GUI 协议、世界生成算法 |

## 7. 每个系统迁移时的统一验收表

- 本体内容迁移前后行为一致。
- 外部命名空间能够加载。
- 同 ID 数据包覆盖行为明确。
- `/reload` 能原子更新，错误数据不会污染当前快照。
- 专用服务器能启动、登录并同步客户端所需定义。
- 存档引用的定义消失时能够恢复或取消。
- 未知类型和无效字段能给出可定位的错误。
- 有一个可运行的示例数据包。
- 支持 Java 扩展的系统有一个示例 Provider/Handler。

## 8. 第一阶段已经落地的第一块

本轮新增 `stardewcraft:stardew_item_data`，它是挂在 Minecraft Item 注册表上的同步 Data Map。

数据包路径：

```text
data/stardewcraft/data_maps/item/stardew_item_data.json
```

示例：

```json
{
  "replace": false,
  "values": {
    "examplemod:blueberry": {
      "category": "stardewcraft:crop",
      "base_sell_price": 50,
      "edibility": 10,
      "energy": 25,
      "health": 11
    },
    "#c:gems": {
      "category": "stardewcraft:mineral",
      "base_sell_price": 80
    }
  }
}
```

解析顺序固定为：

1. Java 动态 Provider，优先级高者先，同优先级按 Provider ID 排序。
2. 同步 Item Data Map。
3. 旧 `IStardewItem` 适配器。
4. 没有星露谷物品元数据。

这样，静态的第三方物品只需数据包；会随 NBT、品质或其他状态变化的物品则由附属 Mod 注册 Provider。

## 9. 第一轮实现检查点（共三轮）

已完成：

- `StardewConditions`：附属可注册 `ResourceLocation + Codec + evaluator`，未知类型失败关闭。
- `StardewItemQueries`：附属可注册物品解析器，统一输出 `List<ItemStack>`。
- `StardewActions`：附属可注册服务端状态动作并返回明确执行结果。
- 首批本体 Condition：`always`、`has_item`、`money`、`flag`；后续生产与收集阶段补充了 `skill`、`season`。
- 首批本体 Item Query：`item`、`random_tag`、`one_of`；后续补充了可组合的 `one_of_queries`、`random_count`。
- 首批本体 Action：`set_flag`、`add_money`、`add_item`、`remove_item`、`start_quest`、`remove_quest`。
- `ServerPreconditionEvaluator` 已开放命名空间 Condition 的旧剧情格式适配。
- `DailyQuestGenerator` 的候选物品选择已走 `one_of` Item Query。
- 五种剧情状态命令已通过旧格式适配器转入公共 Action 注册表。
- NeoForge JUnit 环境已配置，该检查点的 5 项平台契约测试通过。
- 隔离专用服务器已启动到 `Done`。

第二轮已完成：

- 新增 `AtomicDefinitionStore`、`DefinitionSnapshot` 和 `DefinitionDiagnostic`，错误候选不会替换活动快照。
- `EventRegistry` 已使用单一不可变状态、递增版本和 SHA-256 内容哈希；登录与 `/reload` 后同步携带版本和哈希，客户端拒绝过期或被篡改的快照。
- 现有 41 个剧情定义会整批校验；重复 ID、无效 ID、未知触发器和结构错误会带来源文件进入诊断。
- `enter_area` 只由客户端发现候选，服务端重新校验事件、已观看状态、维度、AABB 和全部前置条件后才创建播放会话。
- 每次播放拥有随机 `sessionId`；状态 Action 绑定事件 ID、会话 ID 和原始顶层命令序号，服务端按自己的 JSON 核对动作与参数并拒绝重放。
- 问答命令会在第一次状态 Action 时锁定选择分支，不能在同一会话执行另一个分支的状态变化。
- 剧情完成包同样必须匹配活动会话；旧的冬日星盛宴礼物旁路已迁入统一授权动作。
- 该检查点的 9 项 JUnit 测试通过；隔离专用服务器成功加载 `v1 / 41 events` 并启动到 `Done`。

第三轮已完成：

- 公共 API 使用说明位于 `docs/0.5-addon-api.md`，明确区分了当前已开放能力与仍待迁移的旧系统。
- `examples/stardewcraft-data-pack` 是可直接放入世界的数据包，实际服务端验证时被自动发现并正常加载。
- `examples/stardewcraft-addon` 是独立 Gradle 工程，演示动态物品 Provider 以及自定义 Condition、Item Query、Action，并已成功编译。
- 测试增至 13 项，覆盖实际示例 Data Map Codec、同 ID 后加载快照覆盖、reload 失败回滚、剧情授权拒绝，以及注册表同步和 Action Payload 往返。
- 隔离专用服务器携带示例数据包启动到 `Done`，剧情定义正常加载为 `v1 / 41 events`。

第一阶段收口结论：

- 四项平台地基验收均已有对应实现和测试；第三方物品静态元数据、动态 Provider、三类扩展注册表与原子加载框架可以开始供后续系统使用。
- 在该第一阶段检查点，物品元数据的真实调用链首先覆盖 `DailyQuestGenerator` 的售价；任务、商店、邮件及其余消费者已在后续阶段完成迁移。
- 自定义 Condition、Item Query、Action 已可注册和直接调用；任务系统现已成为第一套完整接入公共 schema 的宿主系统。

## 10. 第二阶段收口：任务系统

- `QuestDataLoader` 现在维护原子、带版本的 `ResourceLocation -> StardewQuestDefinition` 快照，现代路径为 `data/<namespace>/quests/*.json`。
- 原有 `quests.json` 的 22 条定义在加载时转换为结构化定义，数字 ID 统一解析为 `stardewcraft:<id>`，外部数据包可按优先级覆盖同 ID。
- `DataDrivenQuest` 只持有玩家状态和目标运行时；持久化 NBT 不再复制标题、描述、奖励与目标配置，旧完整 NBT 继续兼容。
- 已注册 10 种内置目标类型；附属可以通过 `StardewQuestObjectives` 注册自己的 Codec 和 `QuestObjectiveRuntime`。
- `available_when`、`on_accept`、`on_complete` 已直接使用公共 Condition/Action；任务事件遍历使用稳定快照，完成 Action 可以安全添加后续任务。
- 日常委托概率、持续时间、NPC、四季交付物/鱼、资源和怪物池已迁入 `daily_quest_pools/default.json`。
- 示例数据包包含可运行的两段式任务链；示例附属包含带进度存档的 `break_targets` 目标。
- 调试入口覆盖 `validate`、`grant`、`complete`、`reset`、`inspect`，并继续保留原有专项准备指令。
- 该检查点的 18 项 JUnit 测试通过；示例附属独立构建通过；专用服务器携带示例包加载 24 条任务定义、1 套日常委托池并启动到 `Done`。

第二阶段完成的是任务平台与扩展链路。原版 66 条任务的逐条内容复刻、秘密纸条依赖和法师后续剧情仍按任务内容账本及对应系统阶段继续推进，不混作平台验收。

## 11. 第四阶段进度：生产配方与解锁

- 13 类机器配方、烹饪配方和合成配方均已使用命名空间定义，并支持原子 reload、诊断和服务端到客户端同步。
- 旧机器合并表、旧烹饪 token 表和旧本体合成表会在加载时适配成现代定义，继续兼容现有内容与存档 ID。
- 烹饪食材支持物品、标签和星露谷物品分类；合成食材支持物品和标签，第三方配方保留完整命名空间 ID。
- 配方解锁来源已独立为 `data/<namespace>/player/unlock_sources/*.json`，可由公共 `stardewcraft:apply_unlock_source` Action 应用。
- `stardewcraft:skill` Condition 可用于现代合成配方的自动解锁条件；旧本体解锁条件字符串仍保留为兼容输入。
- 示例数据包现包含机器、烹饪、合成和邮件授予解锁来源的完整链路；该检查点的网络协议测试覆盖七种同步文档，后续又加入地点、精通与职业快照。

### 第四阶段进度：钓鱼与收集扩展

- 钓鱼宝箱保留原版上下文算法作为基础层，数据包可通过 `fishing/treasure_pools/*.json` 追加带权重、条件、钓鱼等级和离岸距离的 Item Query 池。
- 鱼塘规则可通过 `fishpond/pond_data/*.json` 按鱼种新增；旧 36 条规则继续兼容，分类匹配已改走公共物品元数据和上下文标签。
- 探险家公会杀怪目标可通过 `adventurers_guild/monster_slayer_goals/*.json` 新增，奖励使用公共 Action；服务端负责目标显示字段和领取校验。
- 博物馆里程碑可通过 `museum_rewards/rewards/*.json` 新增，支持总数、矿物、古物和指定物品组合，奖励使用公共 Action。
- 原版博物馆藏书已成为独立于 1.6 技能书的系统：世界共享发现计数、玩家分别保存阅读标志，`LOST_BOOK_OR_ITEM` 与钓鱼宝箱恢复原版分支；数据包可通过 `museum/lost_books/*.json` 增加书文、条件和交互点。
- 示例数据包覆盖上述五类收集定义，所有现代定义均使用名字空间 ID，并在候选快照失败时保留上一版。
- 自定义晶球可通过 `geode/drops/*.json` 同时接入克林特和晶球破碎机；本体处理费用仍统一为 25 金。
- 兑奖表和普通矿井层宝箱已经完整移出 Java switch，奖励使用公共 Item Query；附属可新增兑奖序号、循环奖励和普通矿井宝箱层，并用 `priority` 明确覆盖顺序。

第四阶段的生产、钓鱼、鱼塘、博物馆、公会、藏书、晶球、兑奖和普通矿井奖励扩展入口已经完成。出售、出货、职业加成、礼物、博物馆、鱼塘、节日陈列、目录与 tooltip 等通用业务消费者均已改走公共物品元数据 API；`IStardewItem` 只保留在 API 的旧接口适配层，以及旧物品专属的食用后 tooltip 扩展钩子。矿区的石头、矿石、矿物和铱矿分类已分别改为 `stardewcraft:stardew_stones`、`stardewcraft:stardew_ores`、`stardewcraft:stardew_minerals`、`stardewcraft:iridium_ores` 方块标签。

仍然保留的精确物品/方块 ID 判断用于原版特定行为，例如鱼塘珊瑚与海胆的表现、不同矿石的掉落物与经验映射；它们不是通用分类判断。附属矿物要定义独立掉落和经验时，应由矿井内容定义或附属 Handler 承担，而不是依赖路径命名约定。蚯蚓点、采集、采石场与骷髅矿井奖励池已完成结构化迁移。

### 第五阶段进度：节日基础定义

- 12 个本体节日已从 `FestivalRegistry` Java 常量表迁移到 `data/stardewcraft/festivals/*.json`，文件路径是完整命名空间 ID。
- `legacy_id` 保持旧存档、调试命令和本体 Handler 的兼容查找；新节日无需声明旧 ID。
- 节日候选表使用公共原子快照，失败保留上一版，并通过 `DataRegistrySyncPayload` 同步给专用服务器客户端的日历。
- `available_when` 使用公共 Condition；日期、开放时段、公告、地点、地图覆盖、商店引用和玩法 ID 均由数据定义。
- 主动与被动节日均开放带命名空间的 Java Handler 注册，`mechanic_id` 优先绑定附属玩法，找不到时才回退旧节日 ID。
- NPC capabilities、对话、日程、喜好和事件的第三方 ID 会自动归入资源文件命名空间，本体旧 ID 保持兼容；一次 reload 现在通过单个不可变快照替换全部 NPC 数据。
- `StardewNpcInteractions` 提供服务端交互 Provider，按优先级和完整 ID 稳定排序，并在本体任务交付、商店、送礼与普通对话之前执行。

### 第五阶段收口：世界、矿井与室内

- 蚯蚓点、采集区、采石场、骷髅矿井奖励、矿层主题与地点元数据均使用带命名空间的 Codec 定义和原子 reload。
- 矿层地形算法保留在本体，数据包提供主题、方块池和奖励池，附属通过 `StardewMineMonsterProviders` 提供需要世界状态的怪物选择。
- `locations` 和 `interior_portals` 提供区域识别与目的地坐标；预生成地图的结构平移和传送触发器放置仍由世界迁移代码负责。
- 地点快照纳入 `DataRegistrySyncPayload`，客户端音乐、日历和区域查询使用服务端权威数据。

### 第六阶段收口：高级附属 API

- 作物、树木、动物、建筑和装备均提供同步 Data Map；依赖方块位置、实体状态或 `ItemStack` 的行为由有序 Provider 解析。
- 武器主/副技能、矿井怪物和职业效果均有公开 Handler 注册表；重复 ID 拒绝注册，运行时按完整 ID 路由。
- 五系精通奖励和 30 个本体职业元数据已迁入数据文件，并通过登录/reload 快照同步到客户端。
- 固定五项核心技能和职业拥有状态仍属存档协议边界；0.5.x 可覆盖现有职业效果，不在运行时新增第六项核心技能或第 31 个职业槽位。
- 示例附属已演示 15 类 API，包括动态作物、装备、武器技能、矿井怪物与职业效果 Provider/Handler。

## 12. 0.5.0 开放工程收口

- 路线图六个阶段的开发项均已完成，本文不再保留未勾选项。
- 本体静态 Java 表已迁出 57 个商店和 16 条特殊订单；动态日库存、地形生成和 GUI 协议作为明确的 Java 边界保留。
- 无示例包的隔离专用服务器成功加载 57 个商店、16 条特殊订单、5 套精通奖励、30 个职业并到达 `Done`。
- 装入示例数据包后，专用服务器自动发现数据包，并加载 58 个商店、17 条特殊订单、6 个矿层主题、21 个地点、1 个数据传送目标和 6 套精通奖励，然后到达 `Done`。
- 完整用法与边界见 `docs/0.5-addon-api.md`，可运行资产见 `examples/stardewcraft-data-pack` 和 `examples/stardewcraft-addon`。
