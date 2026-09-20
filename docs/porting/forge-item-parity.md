# Forge 1.20.1 物品逻辑对照账

本账只记录已经进入 Forge source set 的物品。表中的“原版注册”以 `src/main/java/com/stardew/craft/item/ModItems.java` 为准；Forge 侧必须保留相同的注册 ID、构造参数、物品属性和资源键，不能用占位物品代替。

| 注册 ID | Forge 实现 | 类型键 | 售价 | 属性 | 状态 |
| --- | --- | --- | ---: | --- | --- |
| `butterfly_powder` | `ButterflyPowderItem` | `stardewcraft.type.misc` | 不可出售（基础价 0） | `stacksTo(999)` | 已对照 |
| `ectoplasm` | `SimpleStardewItem` | `stardewcraft.type.quest` | 不可出售 | `stacksTo(999)` | 已对照 |
| `prismatic_jelly` | `SimpleStardewItem` | `stardewcraft.type.quest` | 不可出售 | `stacksTo(999)` | 已对照 |
| `explosive_ammo` | `SimpleStardewItem` | `stardewcraft.type.resource` | 20 | `stacksTo(999)` | 已对照 |
| `clay` | `SimpleStardewItem` | `stardewcraft.type.resource` | 20 | `stacksTo(999)` | 已对照 |
| `fiber` | `SimpleStardewItem` | `stardewcraft.type.resource` | 1 | `stacksTo(999)` | 已对照 |
| `hay` | `SimpleStardewItem` | `stardewcraft.type.resource` | 0（不可出售） | `stacksTo(999)` | 已对照 |
| `wood_normal` | `SimpleStardewItem` | `stardewcraft.type.resource` | 2 | `stacksTo(999)` | 已对照 |
| `wood_hard` | `SimpleStardewItem` | `stardewcraft.type.resource` | 15 | `stacksTo(999)` | 已对照 |

## 迁移规则

- 物品行为类先原样进入 `src/forge-common/java`，只在编译器明确要求时替换 1.20.1 API；不得顺手重构逻辑。
- Forge 注册桥只负责注册，不复制售卖价、类型键等业务判断。
- 新增条目必须同时补齐模型、纹理和全部 12 个语言文件。
- 每批迁移完成后运行 `classes`、`checkTranslations` 和全新临时目录的 `runServer`；服务器能启动不等于客户端模型已经验收。
