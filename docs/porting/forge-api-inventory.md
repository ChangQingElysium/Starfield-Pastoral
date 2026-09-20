# Forge 1.20.1 API 迁移清单

审计基准：`1.20.1-forge` / `2492f0300`（1.21.1 NeoForge 基线）

这份清单只描述迁移边界，不把“字符串替换成功”当作适配完成。源树仍保留在 `src/main/java`，直到对应切片完成并进入 Forge source set。

## 当前规模

| 范围 | 数量 | 处理方式 |
| --- | ---: | --- |
| 直接导入 `net.neoforged.*` 的 Java 文件 | 1,478 | 按 API 家族分批迁移 |
| 含 NeoForge 引用的 Java 文件 | 1,521 | 包括全限定名和注解 |
| 网络 payload / StreamCodec 相关文件 | 439 | 单独建立 Forge 1.20.1 packet bridge |
| Data Components 相关文件 | 163 | 不能机械替换，改为 NBT/能力或兼容数据层 |
| `HolderLookup.Provider` 相关文件 | 161 | 先核对 1.20.1 签名，再保留数据接口 |
| Mixin 文件 | 126 | 先隔离，最后按 Forge 兼容加载器重接 |

## 机械替换组

### Loader / event bus

| NeoForge 1.21.1 | Forge 1.20.1 |
| --- | --- |
| `net.neoforged.bus.api.IEventBus` | `net.minecraftforge.eventbus.api.IEventBus` |
| `net.neoforged.bus.api.SubscribeEvent` | `net.minecraftforge.eventbus.api.SubscribeEvent` |
| `net.neoforged.bus.api.EventPriority` | `net.minecraftforge.eventbus.api.EventPriority` |
| `net.neoforged.fml.common.Mod` | `net.minecraftforge.fml.common.Mod` |
| `net.neoforged.api.distmarker.Dist` | `net.minecraftforge.api.distmarker.Dist` |
| `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| `net.neoforged.neoforge.common.NeoForge` | `net.minecraftforge.common.MinecraftForge` |

这些替换必须和构造器注入、自动订阅、客户端隔离一起编译验证，不能只做包名替换。

### 注册表

Forge 1.20.1 的稳定公共形态是：

```java
DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
RegistryObject<Item> ITEM = ITEMS.register("item", () -> new Item(new Item.Properties()));
```

当前 NeoForge 源树大量使用 `DeferredRegister.Items`、`DeferredRegister.Blocks`、`DeferredItem`、`DeferredBlock` 和 `DeferredHolder`。迁移策略是先在 Forge slice 中建立本地别名/工厂，逐类把注册器改为 `RegistryObject`，避免一边迁移一边修改全部业务调用点。

### ResourceLocation

1.20.1 没有 1.21 风格的 `ResourceLocation.fromNamespaceAndPath`。需要集中提供兼容工厂，再逐步替换 `fromNamespaceAndPath`、`withDefaultNamespace` 和新构造器调用，防止字符串解析规则散落在业务代码中。

## 语义重写组

### Data Components

1.20.1 的物品状态不能直接使用 1.20.5+ Data Components。涉及 `DataComponents`、`DataComponentType`、`DataComponentMap` 的内容必须先按用途分类：

1. 物品持久化数据：迁移为 `ItemStack` NBT 或 Forge capability。
2. 仅运行时缓存：迁移为服务端/客户端状态表，不写入物品。
3. 网络同步数据：放入 Forge packet 的显式字段。
4. Tooltip / 显示数据：从 NBT 或服务查询，不让客户端直接依赖新组件 API。

### 网络

NeoForge 的 `CustomPacketPayload`、`StreamCodec`、payload registrar 与 Forge 1.20.1 的 `SimpleChannel`/`SimpleImpl` 不是同一抽象。网络迁移按“一个 payload 一个测试”进行：

- 建立 `ForgeNetworkBridge`，固定协议版本和注册顺序。
- 每个 C2S/S2C 包定义编码、解码、处理线程和方向。
- 先迁移建筑阻挡提示、蓝图同步等小 payload，再迁移主系统。
- 不把 NeoForge payload 类直接复制到 Forge 分支后继续共用。

### Mixin / Sodium / Iris

Mixin 不是 Forge 核心 API。Town Door、Sodium、Iris 相关代码最后迁移，并分别记录：无优化模组、Embeddium、Oculus 的行为边界。任何 Forge 兼容实现都必须有独立配置和失败降级路径，不能复用 NeoForge Mixin 目标名。

## 第一批实际切片

顺序固定为：

1. `StardewCraft` 入口、配置和公共事件总线。
2. `ModBlocks` / `ModItems` 的最小注册子集，以及对应 blockstate/item 资源。
3. 其余注册器：方块实体、实体、菜单、声音、粒子、流体。
4. 纯 Java 数据模型和服务。
5. 网络、持久化和 GameTest。
6. 客户端模型、渲染、GUI 和 Mixin。

当前已完成的真实实现是：170 个 `SimpleStardewItem`、1 个 `ButterflyPowderItem`、12 个无自定义运行时依赖的建筑材质方块、16 个矿井背景纯 `Block`、2 个特殊纯方块、15 个生成树 root/log/branch 核心方块、完整 85 个生成树木制建筑件、稳定维度键，以及树预制登记基础 `PrefabTreeInstance` / `PrefabTreeRegistry`。树核心额外迁入 `NewTreePartBlock`、`NewTreeLogBlock`、`NewTreePartBlockEntity` 和 `new_tree_part` 注册；只做 1.20.1 方法签名适配，水平八邻格放置限制、拆除 marker、NBT 字段和 valid-block 顺序保持不变。木制建筑件按源顺序注册普通/棋盘/鱼鳞板材及 stairs、slab、fence、fence gate，并追加五种原木 stairs/slab；只把 `RegistryObject`、`FenceGateBlock` 构造参数顺序、资源目录复数形式和配方 `result.id` 字段回退到 Forge 1.20.1 API。维度键只回退 `ResourceLocation` 构造器，不注册动态维度；预制登记保留双索引、砍伐/炸弹状态与 NBT 键，只把 `SavedData` 回退为 `save(CompoundTag)` 和 `computeIfAbsent(loader, supplier, id)`；树叶、树苗、树生成/砍伐服务及其他 prefab manager 不在本切片。方块切片均单独验证 blockstate、掉落表、配方、标签、模型、纹理和语言资源；详见 [`forge-building-block-parity.md`](forge-building-block-parity.md)。

每个切片都要同时更新三项证据：编译结果、专用服务器/资源加载结果、以及该切片的 API 差异记录。
