# Forge 1.20.1 方块切片对照账

本账记录已经进入 Forge source set 的三批纯方块、生成树核心方块和完整木制建筑件切片。目标不是注册“看起来像”的替代物，而是逐项保留 1.21.1 NeoForge 源树的稳定 ID、方块类型、基础属性、方块物品属性、掉落表、配方、标签和资源键。

## 建筑材质切片：12 个

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

所有方块物品都保持原注册构造的可观察契约：类型键、售价、堆叠上限和普通 `BlockItem` 放置行为一致。当前 `StardewSimpleBlockItem` 只服务于没有地毯实体交互或描述文本的普通方块；地毯放置分支必须和实体系统一起迁移，不能用这个类替代。

## 矿井背景切片：16 个

这批方块全部是源码中的原生 `Block`，没有 `MineSoilBlock`、矿石节点或矿井服务依赖，因此可以在不削弱行为的前提下独立迁移：

| 方块组 | 数量 | Forge 1.20.1 属性 | 方块物品类型键 | 工具标签 |
| --- | ---: | --- | --- | --- |
| `*_loose_soil` | 8 | `BlockBehaviour.Properties.copy(Blocks.DIRT)`，按原值追加 `MapColor` | `stardewcraft.type.natural_ground` | shovel |
| `*_wall` | 8 | `stoneProps(color, SoundType.STONE, 5.0F)`，爆炸强度保持 `6.0F` | `stardewcraft.type.natural_rock` | pickaxe |

具体 ID 为 `mine_earth_*`、`mine_frost_*`、`mine_lava_*`、`mine_desert_*` 的 earth/dark 变体，颜色逐项从源注册读取；没有把同名但带自定义行为的 `*_soil` 方块混进本批。

墙体 blockstate 的随机外观也完整保留：基础模型权重 2、`_broad` 权重 2、`_fractured` 权重 1。对应的 16 个变体模型和 40 张纹理均进入 Forge 资源源集，不能只迁移基础模型。

这批方块物品使用 `StardewSimpleBlockItem` 的可配置类型键，保留 `-1` 售价和 `stacksTo(999)`；它没有替换或简化源 `StardewBlockItem` 的任何本批可观察逻辑，因为这些 16 个源方块没有额外放置钩子。

## 特殊纯方块切片：2 个

- `pale_blue_window_glass`：源类型是 1.21.1 `TransparentBlock`，Forge 1.20.1 没有这个类，使用继承 `AbstractGlassBlock` 的 `GlassBlock`，保留透视、天空光传播和透明渲染属性；`ofFullCopy(Blocks.GLASS)` 只做 `Properties.copy(Blocks.GLASS)` 的 API 适配。
- `mine_barrier`：保留黑色地图颜色、石头声音、`strength(-1.0F, 3600000.0F)` 和 `PushReaction.BLOCK`，并恢复 pickaxe、dragon immune、wither immune 三组标签。它仍是普通 `Block`，没有把尚未迁移的矿井入口行为混进来。

两者的方块物品都保持建筑类型、`-1` 售价和 `stacksTo(999)`；矿井屏障纹理的 `.mcmeta` 动画侧车文件也一并保留。

## 生成树核心切片：15 个

五种树 `oak`、`maple`、`pine`、`mahogany`、`mystic_tree` 各迁移 `root`、`log`、`branch` 三个核心部件。root/branch 仍使用 `NewTreePartBlock(newTreeWoodProps().noOcclusion(), true)`，保留放置前检查水平八邻格、完整碰撞/轮廓、被替换时清除本部件生成树 marker 的行为；log 仍是带方块实体的 `NewTreeLogBlock`，不是 vanilla 原木替代物。所有部件保持 `MapColor.WOOD`、`SoundType.WOOD` 和 `strength(2.0F, 3.0F)`。

`new_tree_part` 方块实体的 15 个 valid block、UUID/species/root 三字段、NBT key、无效参数处理和 `setChanged()` 时机均与源合同一致。1.20.1 只把 `saveAdditional(CompoundTag, HolderLookup.Provider)` / `loadAdditional(...)` 回退为 `saveAdditional(CompoundTag)` / `load(CompoundTag)`，并使用该版本的 `NbtUtils.readBlockPos(CompoundTag)`；没有新增扫描整棵树或清除其它部件 marker 的逻辑。

资源包括 15 个 blockstate、15 个 item model、15 个原始 Blockbench 方块模型、28 张被引用纹理和 15 个掉落表。`logs`、`mineable/axe`、`wild_tree_parts`、`crafting_logs` 与 `crafting_hardwood_logs` 只加入当前已注册成员。源 `wild_tree_parts` 中尚未注册的叶子和树苗不会提前写入 Forge 标签；它们必须随各自完整逻辑切片补回。root/branch 的 10 个客户端 cutout 注册已通过 Forge `FMLClientSetupEvent.enqueueWork` 恢复；这只覆盖当前已迁入的透明树部件，不替代后续叶子渲染和客户端视觉验收。

## 生成树木制建筑件切片：85 个

五种树材质各有三种板材纹样（普通、checkerboard、fishscale），每种纹样注册 planks、stairs、slab、fence、fence_gate 五件，再追加对应原木 stairs/slab，共 85 个方块和 85 个方块物品。Forge 注册保持源的 species → pattern → 五件 → log stairs/slab 顺序；`StairBlock` 以对应 base block 的 default state 构造，`FenceGateBlock` 只把 1.21.1 的参数顺序机械回退为 `FenceGateBlock(props, WoodType.OAK)`，没有改变属性或连接逻辑。

本切片带回 85 个 blockstate、85 个 item model、85 个掉落表、85 个配方，以及完整 220 个 `models/block/wood` 模型、5 个原木模型和 15 张木板纹理；原木 10 张纹理复用生成树核心资源。配方目录从 1.21.1 的 `data/stardewcraft/recipe` 回退为 1.20.1 的 `data/stardewcraft/recipes`，每个 `result.id` 只机械改名为 Forge 读取的 `result.item`。

标签只写入当前 Forge 已注册的 85 个建筑件和已存在的 21 个 axe 成员，避免把尚未迁移的 `town_paving`、terrain stairs/slabs 或 `material_template_fence` 提前写入而触发 TagLoader 错误。`minecraft:fences` 显式指向 `#minecraft:wooden_fences`；未迁移的旧 generic item stairs/slabs 标签暂不创建，待对应方块切片进入 Forge 后再合入。

## 资源路径适配

- `blockstates/`、`models/block/`、`models/item/` 和全部被引用的建筑/矿井纹理已进入 Forge 资源源集。
- 1.21.1 的 `data/stardewcraft/loot_table/` 在 Forge 1.20.1 改为 `data/stardewcraft/loot_tables/`；JSON 内容保持一致。
- 1.21.1 的 `data/stardewcraft/recipe/` 在 Forge 1.20.1 改为 `data/stardewcraft/recipes/`；只把 `result.id` 改为 `result.item`。
- 1.21.1 的 `data/minecraft/tags/block/` 在 Forge 1.20.1 改为 `data/minecraft/tags/blocks/`；Forge 标签只加入已经迁入的 ID，未注册的源方块不会被提前塞进标签。
- 两种 timber 的 `axis=x/y/z` blockstate 保持原样。
- 没有为原本无配方的建筑材质补造 recipe。

## 自动对照

```bash
python3 compatibility/verify_forge_building_blocks.py
python3 compatibility/verify_forge_mine_blocks.py
python3 compatibility/verify_forge_special_blocks.py
python3 compatibility/verify_forge_tree_core.py
python3 compatibility/verify_forge_tree_building.py
```

五个脚本从 1.21.1 源树读取对应批次合同，并验证 Forge 注册、兼容别名、模型与纹理引用（含 220 个木制模型闭包）、blockstate、掉落表、配方、标签和全部 12 个语言文件。树核心检查还覆盖方块实体 valid-block 顺序、放置/拆除行为、marker NBT 合同和 root/branch 客户端 cutout 注册；木制建筑件检查额外锁定 85 个 ID 的注册顺序和 1.20.1 配方字段回退。它们已挂入 `./gradlew check` 的 `checkForgeBuildingBlockParity`、`checkForgeMineBlockParity`、`checkForgeSpecialBlockParity`、`checkForgeTreeCoreParity` 与 `checkForgeTreeBuildingParity`。

## 尚未宣称的范围

本切片不把 NeoForge 的 `src/main/java` 整体编进 Forge。除 `new_tree_part` 和本批 85 个纯木制建筑件外的方块实体、作物、地形行为、交互家具、矿井方块和地毯实体仍按各自完整行为边界迁移；树叶、树苗、树生成/砍伐运行时仍未迁入。在这些类和依赖服务进入 Forge source set 之前，不得用同名空壳方块掩盖缺失逻辑。
