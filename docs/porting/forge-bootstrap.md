# Forge 1.20.1 引导阶段

分支：`1.20.1-forge`

## 当前状态

这一阶段完成 Forge 1.20.1 的构建、加载骨架，以及首批真实物品和方块注册切片；不宣称其他玩法已经迁移。

- ForgeGradle 6.x + Forge `47.4.10`。
- Java 17 工具链，匹配 Minecraft 1.20.1 的运行时。
- `src/forge-bootstrap/java` 保存 Forge 入口、注册桥和客户端加载器适配；当前树核心的 root/branch cutout 注册也在这里。
- `src/forge-common/java` 保存已经验证可在 1.20.1 使用的公共逻辑；当前切片包括 107 个严格纯源码/API 类、12 个纯战斗规则类、6 个经济/天气/农场域模型、32 个纯规则/枚举/几何模型类、只做 `ResourceLocation` API 适配的 `ModDimensions`、原样迁移的树核心方块与方块实体、只做 SavedData 签名适配的 `PrefabTreeInstance` / `PrefabTreeRegistry`、只读预制树 NBT 的 `ForgeTreeStructureReader`、农场出生布局 run 解码规则、保持字段和 NBT 键完全一致的 `MiningPlayerData` 纯玩家状态模型，以及道路三件套和游乐区沙地的 1.20.1 方块行为、`BlockStateTag` 变体适配和各自邻域拓扑。道路/沙地客户端模型位于 Forge bootstrap，季节选择器暂由后续时间/网络桥调用；当前默认春季，不把未迁移的时间同步伪装成完成。作物当前只有源契约审计，没有注册半套运行时。
- `src/forge-bootstrap/resources` 是当前唯一打包的资源目录，包含 Forge 元数据、305 个物品（道路三件套和游乐区沙地在既有注册清单上新增 4 个）、道路四季模型/28 张纹理/3 个掉落表、游乐区沙地 16 个模型/10 张纹理/1 个掉落表、130 个既有方块的 blockstate/模型/纹理/掉落表、当前木制建筑件的 85 个配方和 12 个语言文件。
- 原 `src/main/java` NeoForge 源树保留为迁移参考，暂不参与 Forge 编译。
- `mods.toml` 已切换到 Forge 元数据；NeoForge 模板和 Mixin manifest 暂不加载。

## 通过标准

```bash
./gradlew classes check --no-daemon --console=plain
./gradlew runServer -PforgeRunDir=/tmp/stardewcraft-forge-bootstrap-run --no-daemon --console=plain
```

第一条命令证明构建链、资源处理、语言资源、纯规则闭包和 1.21.1 对照账可用；第二条命令使用全新临时目录，证明 Forge 能够创建开发服务器并加载 `stardewcraft`，完成当前注册对象和 `new_tree_part` 方块实体类型的生命周期。不要复用包含旧 NeoForge 世界的 `run/` 目录做空壳验收。当前不要求旧 NeoForge GameTest 在这个阶段运行，因为生产 Java 源尚未整体进入 Forge source set。

方块切片的逐项合同和资源路径转换见 [`forge-building-block-parity.md`](forge-building-block-parity.md)；`./gradlew check` 会运行普通物品、建筑材质、矿井背景、特殊纯方块、装饰数据与旧存档闭包、树核心/树叶/树规则、树建筑、维度、预制登记、结构读取、纯源码/API 闭包、纯战斗规则、域模型、农场布局、作物边界审计和矿井玩家状态对照任务，并检查 12 个语言文件。维度检查锁定稳定 key 且明确不注册动态维度；预制登记检查锁定双索引、NBT 字段、炸弹/砍伐状态和不迁移其他 prefab manager 的边界；结构读取检查锁定 25 个 NBT 原字节、palette/尺寸/非空气过滤行为以及不迁移完整 StructureLoader 的边界；农场布局检查锁定原 run 的异常顺序、边界和权威掩码计数；作物审计明确依赖 `CropGrowthManager`、品质/数据组件和注册闭包尚未迁移；矿井玩家状态检查锁定 `currentFloor`、`maxFloorReached`、`receivedMineTotem` 三个字段的默认值、单调最高层更新和 NBT 往返合同，暂不把尚未闭合的矿井维度、菜单或网络桥伪装成已迁移。

本切片还记录了一个不能靠机械替换解决的 Forge 差异：Forge 1.20.1 的 `@Mod` 类需要无参构造器，再从 `FMLJavaModLoadingContext.get().getModEventBus()` 取得模组事件总线；不能直接照搬 NeoForge 的构造器注入写法。

## 后续切片顺序

1. 继续按完整行为边界迁移原生方块和方块物品，逐批补上方块实体、交互和掉落逻辑；道路切片的季节时间/网络桥接是下一条依赖链。
2. 把注册表、配置和模组入口抽成 Forge/NeoForge 各自的 loader 适配层。
3. 迁移公共数据模型和纯 Java 服务，不碰事件总线。
4. 迁移注册、事件、网络 payload 和持久化。
5. 最后迁移客户端模型、渲染、Mixin，以及 Sodium/Iris 对应的 Forge 兼容实现。

每一步都要把实际加入 Forge source set 的代码范围写进提交说明，并保持 `classes`、语言检查和最小专用服务器启动可复现。
