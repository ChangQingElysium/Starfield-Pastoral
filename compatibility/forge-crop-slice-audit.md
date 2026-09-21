# Forge 1.20.1 作物切片审计

## 结论

本轮没有把 `parsnip_crop` 注册到 Forge bootstrap。当前 Forge 的 `main` source set 只编译
`src/forge-bootstrap/java` 和 `src/forge-common/java`；完整作物链仍在 NeoForge 参考树
`src/main/java`。直接复制一个作物类会引入未迁移的运行时依赖，因此本轮收口为一个不改变
运行时、可独立验证的纯农业静态契约闭包：

本轮只落下源契约审计，不新增未被 Forge 运行时调用的状态机或替代实现。审计锁定防风草的
春季门禁、四个各 1 天的生长阶段、非再生长、GRAB 收获方式、种子/产物 ID 和 8 点
Farming XP，供后续完整方块/物品闭包直接消费。

## 为什么暂不注册方块/物品

NeoForge 的 `ParsnipCropBlock` 继承 `StardewCropBlock`，而后者的实际运行路径还依赖：

- `CropGrowthManager`（SavedData、每日调度、离线追赶和农田扫描）；
- `QualityHelper` 及 1.21 `DataComponents`（品质 NBT、品质模型）；
- `SeasonLocationRules`、`TerrainSoils`（地点/土壤与季节门禁）；
- `PlayerStardewDataAPI`、职业/技能和收获经验；
- `ModBlocks`、`ModItems` 的 NeoForge `DeferredBlock`/`DeferredItem` 互相引用。

这些类在 Forge 1.20.1 闭包中尚未迁移，且含 1.21 API；只迁移注册、模型或掉落会产生
“能启动但行为不等价”的假闭包。作物资源仍保留在 NeoForge 参考树，并由 checker 锁定，
但没有把它们宣称为 Forge 已交付资源。

## 交付与边界

- 源契约检查：`compatibility/verify_forge_parsnip_crop_rules.py`
- checker 会验证 NeoForge 类/物品的阶段、季节、再生长、XP、品质数值、ID、14 个资源锚点
  和全部 12 个语言文件；同时拒绝 Forge source set 出现不完整的 crop runtime 文件。
- 下一条真正的方块/物品垂直切片必须先迁移上述依赖闭包，再同时加入 Forge 注册、资源、
  掉落、标签、语言和运行时收获测试；本轮不使用占位实现。
