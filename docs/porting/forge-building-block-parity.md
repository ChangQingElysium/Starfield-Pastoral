# Forge 1.20.1 建筑材质方块对照账

本账记录已经进入 Forge source set 的第一批方块。目标不是注册“看起来像”的替代物，而是逐项保留 1.21.1 NeoForge 源树的稳定 ID、方块类型、基础属性、方块物品属性、掉落表、挖掘标签和资源键。

## 当前切片

已恢复 12 个纯建筑材质方块和对应的 12 个方块物品：

| 注册 ID | 原始方块 | Forge 1.20.1 适配 | 属性来源 | 工具标签 |
| --- | --- | --- | --- | --- |
| `pale_cyan_plaster` | `Block` | `Block` | `copy(TERRACOTTA) + COLOR_LIGHT_BLUE` | pickaxe |
| `teal_painted_timber` | `RotatedPillarBlock` | `RotatedPillarBlock` | `copy(OAK_PLANKS) + COLOR_CYAN` | axe |
| `cream_siding` | `Block` | `Block` | `copy(OAK_PLANKS) + SAND` | axe |
| `terracotta_roof_tiles` | `Block` | `Block` | `copy(BRICKS) + TERRACOTTA_ORANGE` | pickaxe |
| `dark_brown_roof_tiles` | `Block` | `Block` | `copy(BRICKS) + TERRACOTTA_BROWN` | pickaxe |
| `ivory_siding` | `Block` | `Block` | `copy(OAK_PLANKS) + SAND` | axe |
| `gray_green_masonry` | `Block` | `Block` | `copy(STONE_BRICKS) + TERRACOTTA_LIGHT_GREEN` | pickaxe |
| `gray_violet_roof_tiles` | `Block` | `Block` | `copy(BRICKS) + TERRACOTTA_PURPLE` | pickaxe |
| `brick_red_roof_tiles` | `Block` | `Block` | `copy(BRICKS) + TERRACOTTA_RED` | pickaxe |
| `blue_gray_timber` | `RotatedPillarBlock` | `RotatedPillarBlock` | `copy(OAK_PLANKS) + COLOR_CYAN` | axe |
| `pale_blue_siding` | `Block` | `Block` | `copy(OAK_PLANKS) + COLOR_LIGHT_BLUE` | axe |
| `blue_painted_planks` | `Block` | `Block` | `copy(OAK_PLANKS) + COLOR_BLUE` | axe |

1.20.1 没有 1.21.1 的 `Block.Properties.ofFullCopy`，Forge 侧使用 `BlockBehaviour.Properties.copy`。这是同一组基础属性的 API 适配，不是重新选择硬度、声音、碰撞或地图颜色。

所有方块物品都保持原注册构造：`StardewBlockItem(block, "stardewcraft.type.building", -1, new Item.Properties().stacksTo(999))` 的可观察契约。当前 `StardewSimpleBlockItem` 只服务于这批没有地毯实体交互或描述文本的普通建筑材质；地毯放置分支必须和实体系统一起迁移，不能用这个类替代。

## 资源路径适配

- `blockstates/`、`models/block/`、`models/item/` 和全部被引用的建筑纹理已进入 Forge 资源源集。
- 1.21.1 的 `data/stardewcraft/loot_table/` 在 Forge 1.20.1 改为 `data/stardewcraft/loot_tables/`；JSON 内容保持一致。
- 1.21.1 的 `data/minecraft/tags/block/` 在 Forge 1.20.1 改为 `data/minecraft/tags/blocks/`；本批只加入原来属于这 12 个方块的 axe/pickaxe 条目，不把尚未注册的方块提前塞进标签。
- 两种 timber 的 `axis=x/y/z` blockstate 保持原样。
- 没有为原本无配方的建筑材质补造 recipe。

## 自动对照

```bash
python3 compatibility/verify_forge_building_blocks.py
```

该脚本从 1.21.1 `ModBlocks.java`/`ModItems.java` 读取本批合同，并验证 Forge 注册、兼容别名、模型与纹理引用、blockstate、掉落表、挖掘标签和全部 12 个语言文件。它已挂入 `./gradlew check` 的 `checkForgeBuildingBlockParity`。

## 尚未宣称的范围

本切片不注册其它自定义方块类，也不把 NeoForge 的 `src/main/java` 整体编进 Forge。方块实体、作物、地形行为、交互家具、矿井方块和地毯实体仍按各自完整行为边界迁移；在这些类和依赖服务进入 Forge source set 之前，不得用同名空壳方块掩盖缺失逻辑。
