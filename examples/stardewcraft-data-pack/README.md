# StardewCraft 0.5 数据包示例

这个独立数据包同时演示：

- 把原版苹果和甜浆果标记成星露谷物品；
- 用 `data/<namespace>/quests/*.json` 定义一条两段式任务链；
- 在任务定义里调用公共 Condition 和 Action；
- 新增静态商店和地点交互绑定；
- 新增带附件和阅读 Action 的邮件；
- 新增只使用本体命令的剧情；
- 新增特殊订单。
- 用一个现代单配方文件扩展本体小桶。
- 新增一条结构化食材的烹饪配方。
- 新增合成配方，并通过邮件 Action 调用解锁来源授予两种配方。
- 追加钓鱼宝箱池、鱼塘规则、杀怪目标、博物馆奖励和一本可交互藏书。
- 把紫水晶簇声明成可由克林特和晶球破碎机处理的自定义晶球。
- 为兑奖序号 5 添加金苹果，并为普通矿井 30 层添加金镐宝箱奖励。
- 新增一个无需 Java 玩法 Handler 的秋季“苹果日”。
- 给原版对象演示作物、树、动物、建筑和装备 Data Map。
- 追加蚯蚓点奖励、远端采集区、13 层矿井主题、地点和传送目标。
- 给农业精通追加奖励，并把 Tiller 的效果绑定到示例附属 Handler。

## 安装

把整个 `stardewcraft-data-pack` 目录放进存档的 `datapacks/`，然后执行 `/reload`。

Data Map 的固定路径是：

```text
data/stardewcraft/data_maps/item/stardew_item_data.json
```

`values` 可以直接使用物品 ID，也可以像本例一样使用 `#namespace:tag`。标签仍然放在数据包自己的命名空间下。

## 字段

| 字段 | 默认值 | 含义 |
| --- | ---: | --- |
| `category` | `stardewcraft:unknown` | 星露谷分类 ID |
| `base_sell_price` | `-1` | 基础售价；`-1` 表示不可出售 |
| `edibility` | `-300` | 原版式可食用值 |
| `energy` | `0` | 恢复能量 |
| `health` | `0` | 恢复生命 |
| `hidden` | `false` | 是否属于隐藏内容 |

数据包只能给已经注册的物品添加元数据，不能创建新物品。

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
data/example_stardew_addon/world_loot/apple_artifact.json
data/example_stardew_addon/forage_zones/apple_grove.json
data/example_stardew_addon/mine_themes/apple_floor.json
data/example_stardew_addon/locations/apple_shed.json
data/example_stardew_addon/interior_portals/apple_shed_exit.json
data/example_stardew_addon/mastery_rewards/apple_farming.json
data/stardewcraft/professions/tiller.json
data/stardewcraft/data_maps/block/stardew_crop_data.json
data/stardewcraft/data_maps/block/stardew_tree_data.json
data/stardewcraft/data_maps/block/stardew_building_data.json
data/stardewcraft/data_maps/entity_type/stardew_animal_data.json
data/stardewcraft/data_maps/item/stardew_equipment_data.json
```

`apple_grove` 和 `apple_shed` 使用约 `10000,10000` 的远端示例坐标，避免覆盖本体固定区域。地点和 portal 文件不会创建建筑、入口或传送触发器；实际附属必须自行放置结构并调用对应目标。

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
