# StardewCraft 0.5 数据包示例

这个独立数据包同时演示：

- 把原版苹果和甜浆果标记成星露谷物品；
- 给原版南瓜派和金胡萝卜追加可重载的 StardewCraft Buff；
- 用 `data/<namespace>/quests/*.json` 定义一条两段式任务链；
- 在任务定义里调用公共 Condition 和 Action；
- 新增静态商店和地点交互绑定；
- 新增带附件和阅读 Action 的邮件；
- 新增只使用本体命令的剧情；
- 新增特殊订单。
- 用一个现代单配方文件扩展本体小桶。
- 新增一条结构化食材的烹饪配方。
- 新增合成配方，在商店中出售完整的第三方命名空间 ID，并通过邮件 Action 提供另一条解锁路径。
- 追加钓鱼宝箱池、鱼塘规则、杀怪目标、博物馆奖励和一本可交互藏书。
- 把紫水晶簇声明成可由克林特和晶球破碎机处理的自定义晶球。
- 为兑奖序号 5 添加金苹果，并为普通矿井 30 层添加金镐宝箱奖励。
- 新增一个无需 Java 玩法 Handler 的秋季“苹果日”，以及一对条件恒真/恒假的主动节日验收定义。
- 给原版对象演示作物、树、动物、建筑和装备 Data Map。
- 用完整 `farm_animals` 定义新增一只复用本体鸭子实体、声音和商店贴图的鹅。
- 追加蚯蚓点奖励、远端采集区、13 层矿井主题、怪物刷怪表、地点和传送目标。
- 用可组合 patch 调整 Abigail 对原版苹果的喜好，不覆盖她的完整 NPC 文件。
- 给农业精通追加奖励，并把 Tiller 的效果绑定到示例附属 Handler。

## 安装

把整个 `stardewcraft-data-pack` 目录放进存档的 `datapacks/`，然后执行 `/reload`。

Data Map 的固定路径是：

```text
data/stardewcraft/data_maps/item/stardew_item_data.json
data/stardewcraft/data_maps/item/stardew_food_effects.json
```

`values` 可以直接使用物品 ID，也可以像本例一样使用 `#namespace:tag`。标签仍然放在数据包自己的命名空间下。

## 字段

| 字段 | 默认值 | 含义 |
| --- | ---: | --- |
| `category` | `stardewcraft:unknown` | 星露谷分类 ID |
| `base_sell_price` | `-1` | 基础售价；`-1` 表示不可出售 |
| `edibility` | `-300` | 原版式可食用值；高于 `-300` 时将已有 Minecraft 食物接入星露谷食用流程 |
| `energy` | `0` | 在星露谷维度食用后恢复的能量 |
| `health` | `0` | 在星露谷维度食用后恢复的生命 |
| `hidden` | `false` | 预留的隐藏内容标记；0.5.x 暂不改变目录或 JEI 可见性 |

数据包只能给已经注册的物品添加元数据，不能创建新物品。食用兼容要求目标物品本身已经具有 Minecraft Food Component；Data Map 不会把普通材料凭空改造成带动画和食用时长的食物。配置后的原版或第三方食物可以在星露谷维度满饥饿时食用，并保留原物品的效果和容器返还；本体随后按 `energy` 和 `health` 结算星露谷属性。

食物 Buff 使用独立的 `stardew_food_effects` Data Map。`effects` 是按稳定条目 ID
组织的对象；多个数据包对同一食物添加不同条目时会组合，同 ID 的高优先级条目会覆盖
低优先级条目。`effect` 可引用 Minecraft、本体或任意已加载附属注册的 MobEffect：

```json
{
  "replace": false,
  "values": {
    "minecraft:pumpkin_pie": {
      "effects": {
        "myaddon:harvest_focus": {
          "effect": "stardewcraft:farmer_blessing",
          "duration_ticks": 1200,
          "amplifier": 0,
          "chance": 1.0,
          "ambient": false,
          "show_particles": false,
          "show_icon": true
        }
      }
    }
  }
}
```

`duration_ticks` 必须大于零；`amplifier` 使用 Minecraft 的零起始等级；`chance`
范围为 `0..1`。Buff 在原物品完成食用并处理自身效果和容器后追加。只配置 Buff、
不配置能量/生命也有效，并能在星露谷主维度和矿井维度满饥饿时食用。

需要隐藏物品时，0.5.x 请继续使用 `stardewcraft:hidden` 物品标签；`hidden` Data Map 字段目前只保留并同步元数据。

## 自动静态验收

在仓库根目录执行：

```bash
python3 examples/stardewcraft-data-pack/validate.py
```

该检查会解析两个数据包中的全部 JSON，并验证配方商店行、邮件解锁源、主动节日对照、晶球、装备技能和 Java Provider 之间的跨文件关系。它是快速失败检查，不代替服务端 Codec 加载和真实客户端验收。

## 登录与 `/reload` 验收

1. 先只安装主数据包并登录。执行 `/stardew mail send example_stardew_addon:apple_club`并阅读后，信件收藏应显示该邮件；JEI 应有 `apple_crate`、`apple_stand` 和紫水晶簇晶球条目。
2. 保持客户端在线，把 `acceptance/reload-overlay` 作为第二个数据包放入同一存档的 `datapacks/` 中。
3. 执行 `/reload`。如果服务器没有自动启用新包，先用 `/datapack list` 查看并启用 `reload-overlay`。
4. 无需重连，`/stardew mail send example_stardew_addon:reload_probe` 应立即可用，阅读后信件收藏应新增该邮件；JEI 商店应新增价格 321 的绿宝石，晶球页应新增“石英块 → 绿宝石”。

## 关键人工验收

| 能力 | 操作 | 通过标准 |
| --- | --- | --- |
| 第三方配方 | 执行 `/stardew shop example_stardew_addon:apple_stand`，购买 `recipe:example_stardew_addon:apple_crate`，再用 4 个苹果合成 | 解锁、判重、制作和重连后存档都保留 `example_stardew_addon` 命名空间 |
| 邮件备选解锁 | 未购买时阅读 `example_stardew_addon:apple_club` | 邮件 Action 通过同名解锁源授予配方；之后商店不再显示已知配方 |
| 外部食物 | 在星露谷维度、原版饥饿值已满时食用示例标签中的苹果或甜浆果 | 能正常开始并完成食用，原物品效果保留，同时恢复 Data Map 声明的星露谷能量与生命 |
| 外部食物 Buff | 食用南瓜派和金胡萝卜，并在修改 Data Map 后执行 `/reload` 再次食用 | 南瓜派获得农业 Buff、金胡萝卜获得幸运 Buff；修改只影响之后的食用，原版行为不变 |
| 主动节日条件 | 查看春 2 日并进入对应日期 | 只有 `conditional_active_enabled` 出现/触发，`conditional_active_disabled` 在服务端和日历都被过滤 |
| 装备与农业 Provider | 同时安装示例附属 | 按附属 README 中的 Y > 80 锦标和带附魔光效钻石剑逐项验收 |

`apple_stand` 商店还声明了示例附属提供的动态库存 Provider。只安装数据包时，静态金苹果条目仍然可用；同时安装 `examples/stardewcraft-addon` 后，Provider 会追加每日苹果库存。

主要定义路径：

```text
data/example_stardew_addon/shops/apple_stand.json
data/example_stardew_addon/shop_bindings/apple_stand_block.json
data/example_stardew_addon/mail/apple_club.json
data/example_stardew_addon/cutscene_events/apple_club_intro.json
data/example_stardew_addon/special_orders/apple_hunt.json
data/example_stardew_addon/artisan/apple_keg.json
data/example_stardew_addon/cooking/recipes/apple_snack.json
data/example_stardew_addon/player/crafting_recipes/apple_crate.json
data/example_stardew_addon/player/unlock_sources/apple_club.json
data/example_stardew_addon/fishing/treasure_pools/apple_bonus.json
data/example_stardew_addon/fishpond/pond_data/apple_fish.json
data/example_stardew_addon/adventurers_guild/monster_slayer_goals/apple_hunter.json
data/example_stardew_addon/museum_rewards/rewards/apple_collection.json
data/example_stardew_addon/museum/lost_books/apple_archive.json
data/example_stardew_addon/geode/drops/apple_crystal.json
data/example_stardew_addon/prize_ticket/rewards/golden_apple.json
data/example_stardew_addon/mine_chest/rewards/floor_30.json
data/example_stardew_addon/festivals/apple_day.json
data/example_stardew_addon/festivals/conditional_active_enabled.json
data/example_stardew_addon/festivals/conditional_active_disabled.json
data/example_stardew_addon/world_loot/apple_artifact.json
data/example_stardew_addon/forage_zones/apple_grove.json
data/example_stardew_addon/mine_themes/apple_floor.json
data/example_stardew_addon/mine_monster_spawns/orchard_floor.json
data/example_stardew_addon/npc/taste_patches/abigail_apples.json
data/example_stardew_addon/locations/apple_shed.json
data/example_stardew_addon/interior_portals/apple_shed_exit.json
data/example_stardew_addon/mastery_rewards/apple_farming.json
data/example_stardew_addon/stardewcraft/farm_animals/goose.json
data/stardewcraft/professions/tiller.json
data/stardewcraft/data_maps/block/stardew_crop_data.json
data/stardewcraft/data_maps/block/stardew_tree_data.json
data/stardewcraft/data_maps/block/stardew_building_data.json
data/stardewcraft/data_maps/entity_type/stardew_animal_data.json
data/stardewcraft/data_maps/item/stardew_equipment_data.json
data/stardewcraft/data_maps/item/stardew_food_effects.json
```

`apple_grove` 和 `apple_shed` 使用约 `10000,10000` 的远端示例坐标，避免覆盖本体固定区域。地点和 portal 文件不会创建建筑、入口或传送触发器；实际附属必须自行放置结构并调用对应目标。

`orchard_floor` 只引用本体已有怪物 Profile，因此独立安装数据包也能 reload。示例
Java 附属还在自己的 JAR 中注册 `orchard_silverfish` Profile 与更高优先级刷怪表，
用于演示“代码注册新能力、数据决定楼层分布”的组合方式。

`abigail_apples` 只增删指定分类中的精确物品；其它数据包对 Abigail 的喜好修改、
本体礼物消息、对话和日程都保持不变。

只安装数据包时，`tiller.json` 的未知示例 Handler 会保持原倍率；同时安装示例附属后，出售苹果时会在原 Tiller 加成后再乘 `1.02`，用于证明职业数据到 Java Handler 的真实调用链。

## 示例任务链

执行下面的指令发放第一环：

```text
/stardew quest grant example_stardew_addon:sample_chain/start
```

获得苹果后，第一环会设置 Flag，并通过 `stardewcraft:start_quest` 自动发放第二环。任务定义路径中的文件名就是命名空间 ID，例如：

```text
data/example_stardew_addon/quests/sample_chain/start.json
-> example_stardew_addon:sample_chain/start
```
