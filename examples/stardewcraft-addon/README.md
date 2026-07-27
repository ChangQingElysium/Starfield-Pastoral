# StardewCraft 0.5 附属 Mod 示例

这个小工程集中展示当前 `api.v1` 的主要 Java 扩展入口：

- 按 `ItemStack` 动态提供星露谷物品元数据；
- 为动态内容追加统一物品获得来源；
- 注册自定义货币，通过可退款事务消费，并用 `shop_costs` 让标准商店显示该货币；
- 用统一 Requirement 报告解释组合费用及成本 Provider 解析后的货币/物品不足，
  只读预检不替代服务端原子支付；
- 用 `shop_products` 把自定义治疗 Action 声明成不生成物品的服务商品，由核心负责先支付、失败退款、库存同步，并通过候选商店目录报告单次购买限制等 Handler 阻断；
- 用 `shop_stock_rules` 把苹果库存声明为每位玩家每周重置，且不修改旧商店行结构；
- 用 `(shopId, entryId)` 查询统一运行时库存快照和售罄 Requirement，不依赖客户端
  `itemIndex`，也不修改旧购买网络协议；
- 注册自定义 Condition；
- 为自定义 Condition 注册只读要求说明，使菜单可以展示锁定原因而不接管服务端判定；
- 注册自定义 Item Query，并用强类型引用 Provider 声明查询会产出的确定物品；
- 注册服务端 Action；
- Action 可用强类型引用 Provider 声明 payload 依赖的附属货币，使嵌套引用也进入统一目录；
- 注册带独立进度状态的任务目标类型；
- 用统一进度 Key/快照查询任务、特殊订单、邮件、社区中心、博物馆奖励和节日会话，并观察它们共同的提交后生命周期；
- 为附属自己的进度领域注册只读 Provider，不要求继承本体任务类或复用本体存档；
- 通过 Provider 的 `entries` 把公开进度项加入稳定领域目录，供菜单、图鉴和诊断枚举；
- 向统一内容目录发布附属节点，并跨系统引用本体地点与 Minecraft 物品；目录只诊断引用，不接管定义所有权；
- 为改名后的内容发布同类型 alias，使旧引用解析到规范节点，并由统一目录报告冲突、循环或缺失目标；
- 为进度项提供只读奖励预览，使附属 UI 能展示物品、货币或动态 Action，而领取仍由原系统的服务端事务负责；
- 注册动态商店库存 Provider；
- 注册服务端剧情触发器；
- 在纯客户端类中注册视觉演出命令；
- 注册特殊订单目标类型；
- 注册特殊订单奖励类型。
- 邮件附件、社区中心静态奖励和博物馆标准物品奖励会自动进入统一物品获得来源；动态任务/订单奖励仍可由附属 Provider 补充；
- 注册有序 NPC 交互 Provider；
- 用统一 NPC Profile 注册一次静态身份与展示，再组合社交规则、礼物确认/前后钩子、
  友情奖励和实体 resolver；
- 用附属命名空间下的裸 `shop`/`npc` 引用声明 NPC 商店绑定，并通过统一 NPC 内容
  快照诊断 Profile、对话、日程、喜好、商店和当前实体；
- 观察核心 NPC 的生成/移除事实；附属自有实体也可上报同一生命周期事件；
- 注册动态作物、果树、动物和建筑 Provider；
- 用生产计划 Provider 在机器消耗输入和燃料前修改产物/时长，并同时覆盖手动与自动化入口；
- 观察统一的机器 `STARTED/READY/COLLECTED` 生命周期事件，用于附属任务与统计；
- 用通用机器周期 Provider/事件区分批次、重复、被动和环境生产，不把晶球复制机等持续设备伪装成一次性配方；
- 注册动态装备元数据和武器技能 Handler；
- 用纯数据包声明不规则逻辑地点、共享世界锚点和引用它的节日地图 overlay；
- 世界锚点与农场相对挂点共享通用地图槽位 role，可按玩家农场解析最终世界坐标；
- 用纯数据 `building_blueprints` 向附属自己的建筑目录增加可购买条目；
- 让果园地点继承本体森林，声明同步展示、环境标签、白天音乐和可扩展命名空间属性；
- 用统一地点与时间 Condition 限制 NPC 商店，不再复制坐标、地点字符串或营业时间判断；
- 注册有序且异常隔离的逻辑地点进入/离开观察器；
- 注册版本化节日会话状态 Key；
- 农场布局可声明版本和类型化创建配置，由服务端下发安全预览、重新校验并持久化；
- 用纯数据包定义完整农场布局、三个入口、洞穴、配置字段和跨系统命名挂点；
- 农场布局升级使用显式、失败可重试的版本迁移，不自动覆盖旧农场几何；
- 观察所有主动/被动节日共用的阶段、地图恢复和参与者生命周期；
- 用能力声明注册可组合节日 mechanic layer，并由纯数据节日中的裸
  `mechanic_id` 自动绑定到附属命名空间；
- 节日 `world.shops` 可通过公共目录和服务端权威打开入口复用，不需要附属复制
  本体商店发包、过滤和库存逻辑；
- 注册两阶段节日奖励 Provider，由统一服务验证参与状态并将一次性领取记录持久化
  到当前会话；
- 为 Handler 拥有的节日奖励注册只读描述符，使其自动进入奖励目录、统一预览、
  玩家进度和领取事件，同时不把展示状态当作领取授权；
- 节日奖励 UI 可读取统一 Requirement preflight 展示会话、参与和已领取阻断原因，
  但实际领取仍必须调用服务端权威事务；
- 注册稳定 activity ID；附属 NPC/方块可在服务端权威校验后启动自己的小游戏，
  不需要接管本体节日 Handler；
- 用 festival 派生进度 domain 为 `apple_toss` 暴露可枚举目标，保留 festival 与
  activity 两个命名空间，不把复合身份压成私有字符串；
- 注册命名空间矿井怪物 Profile，并由附属数据包声明楼层、主题、距离和权重
  （动态选择器与旧 EntityType Provider 仍兼容）；
- 注册职业效果 Handler，并由示例数据包绑定到 `Tiller` 的售价操作。
- 声明一个可选、版本化的客户端目录展示能力；远端缺失时连接继续且展示安全降级。

先在仓库根目录构建本体 JAR，再编译示例：

```bash
./gradlew jar
./gradlew -p examples/stardewcraft-addon build
```

真实附属工程应把本地 `fileTree` 依赖替换为 StardewCraft 发布后的 Maven 坐标，并保留 `neoforge.mods.toml` 中对 `stardewcraft` 的 `required + AFTER` 依赖。

注册 ID 必须使用附属自己的命名空间。重复 ID 会直接抛出异常，不按加载顺序覆盖。

Java 示例发布 `orchard_tasting` 内容节点并引用本体 Town 与原版苹果。它不会覆盖地点或物品定义；可用
`/stardew debug content example_stardew_addon:content_type/orchard_feature`
查看该类型，再追加 `example_stardew_addon:orchard_tasting` 下钻节点引用。

`farm_layouts/orchard_preview.json` 是完整纯数据布局的 schema canary。它借用
本体标准农场结构且设为 `selectable=false`，因此安装示例不会向玩家提供一个名称
像果园、实际地形却不是果园的假选项。真实布局附属应改用自己的 `.schem` 和图标；
需要运行时代码时也可使用
`StardewFarmLayouts.register(layout, version, fields, attachments)`。
修改已生成世界时用 `StardewFarmLayoutMigrations.register(...)` 显式迁移。
`StardewFarmSelectionOptions` 只为旧附属保留，不应再用于需要持久化的布局配置。

`locations/orchard.json` 给出兼容的粗边界，
`regions/orchard_shape.json` 用两个 include box 和一个 exclude box 描述精确
不规则区域，`anchors/orchard_stage.json` 提供共享坐标，
`festival_map_overlays/orchard_festival_overlay.json` 再引用同一锚点；NPC 日程
也可以写 `@example_stardew_addon:orchard_stage`。示例 overlay 没有被任何节日
定义引用，所以安装这个验收 Mod 不会实际修改地图。真实附属应把示例中借用的本体
`.schem` ID 换成自己命名空间下的结构资源。
果园还在地点属性中声明了 `06:00-18:00` 的音乐；档案管理员商店绑定则组合地点
与 `09:00-17:00` 的通用时间 Condition。两者都由统一服务端/客户端状态解析，
不需要附属在 NPC 交互或客户端音乐 tick 中复制时间分支。
`festivals/orchard_celebration.json` 展示纯数据日历/地点/商店声明，Java 仅叠加
奖励与持久状态能力；静态地图描述仍不需要注册代码。
`npc/dialogue/archivist.json`、`npc/schedules/archivist.json` 和
`npc/tastes/archivist.json` 则与 Java 注册的同名 Profile、实体 resolver、社交规则和
友情奖励组成一个完整角色。它的日程、节日和商店都引用同一果园地点及世界锚点，用于验证
角色扩展不依赖本体 NPC 名单或另一套坐标表。

Java 示例还注册了 `orchard_blossom` 世界事件类型。调用者应先通过地点/区域/锚点
解析安全的服务端坐标，再使用唯一实例 UUID 请求启动；处理器只声明期望方块与临时
替换方块。本体会一次预检整份计划、提交、在部分失败时回滚，并保存精确逆操作。
之后即使示例附属暂时卸载，本体仍能用实例 UUID 清理事件。示例不会自动启动，
因此安装它不会修改现有世界。

`building_blueprints/orchard_storehouse.json` 展示不依赖 Robin/Wizard 的附属
建筑目录。示例用原版木桶作为安全的结果物品，所以不会假装数据包能注册新方块；
真实 Mod 注册自己的建筑物品后可替换 `result_item`，并在已经验证自己的交互权限
后调用 `StardewBuildingBlueprints.open(player, builderId)`。

客户端剧情命令位于独立的 `client` 包，并通过 `Dist.CLIENT` 订阅客户端初始化事件。服务端入口不能直接引用该实现，否则附属会破坏专用服务器启动。

## 0.5 API 验收锦标

这些 Provider 故意使用可重复的条件，避免安装示例后改写整个世界：

| 路径 | 触发条件 | 可观察结果 |
| --- | --- | --- |
| 作物 Provider | Y > 80 的 `stardewcraft:parsnip_crop` | 本体收获链使用 12 点农业经验 |
| 作物 Runtime | 附属自己的 `moonberry_crop` 方块 | 不继承本体作物类也可进入换日、收获、Jade 与农业获得来源 |
| 果树 Provider | Y > 80 的 `stardewcraft:apple_tree` | 果树存储链使用金苹果、每次 2 个、最多 4 个 |
| 动物 Provider | Y > 80 的已管理奶牛 | 生产链每 2 天产出蜂蜜瓶 |
| 建筑 Provider | Y > 80 的 coop/barn manager | 建筑容量为 2，且只接受奶牛 |
| 装备 Provider | 任意带附魔光效的钻石剑 | 按公共 `weapon` 槽保存完整物品栈，主技能键（默认右键）调用 `apple_dash` |

静态钻石剑 Data Map 在示例数据包中；带附魔光效时，Java Provider 以更高优先级覆盖它。这同时验收了完整 `ItemStack` 解析、装备后保存/重连和服务端技能 Handler 三条路径。
