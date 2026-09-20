# Forge 1.20.1 引导阶段

分支：`1.20.1-forge`

## 当前状态

这一阶段完成 Forge 1.20.1 的构建、加载骨架，以及第一个真实物品注册切片；不宣称其他玩法已经迁移。

- ForgeGradle 6.x + Forge `47.4.10`。
- Java 17 工具链，匹配 Minecraft 1.20.1 的运行时。
- `src/forge-bootstrap/java` 保存 Forge 入口和注册桥。
- `src/forge-common/java` 保存已经验证可在 1.20.1 使用的公共物品逻辑；当前切片包括 `IStardewItem`、`ButterflyPowderItem` 和 `SimpleStardewItem`。
- `src/forge-bootstrap/resources` 是当前唯一打包的资源目录，包含 Forge 元数据、`butterfly_powder` 模型、纹理和 12 个语言文件。
- 原 `src/main/java` NeoForge 源树保留为迁移参考，暂不参与 Forge 编译。
- `mods.toml` 已切换到 Forge 元数据；NeoForge 模板和 Mixin manifest 暂不加载。

## 通过标准

```bash
./gradlew classes checkTranslations --no-daemon --console=plain
./gradlew runServer -PforgeRunDir=/tmp/stardewcraft-forge-bootstrap-run --no-daemon --console=plain
```

第一条命令证明构建链、资源处理和语言资源可用；第二条命令使用全新临时目录，证明 Forge 能够创建开发服务器并加载 `stardewcraft`，同时完成当前 9 个物品的注册生命周期：`butterfly_powder`、`ectoplasm`、`prismatic_jelly`、`explosive_ammo`、`clay`、`fiber`、`hay`、`wood_normal`、`wood_hard`。不要复用包含旧 NeoForge 世界的 `run/` 目录做空壳验收。当前不要求旧 NeoForge GameTest 在这个阶段运行，因为生产 Java 源尚未整体进入 Forge source set。

本切片还记录了一个不能靠机械替换解决的 Forge 差异：Forge 1.20.1 的 `@Mod` 类需要无参构造器，再从 `FMLJavaModLoadingContext.get().getModEventBus()` 取得模组事件总线；不能直接照搬 NeoForge 的构造器注入写法。

## 后续切片顺序

1. 把注册表、配置和模组入口抽成 Forge/NeoForge 各自的 loader 适配层。
2. 迁移公共数据模型和纯 Java 服务，不碰事件总线。
3. 迁移注册、事件、网络 payload 和持久化。
4. 最后迁移客户端模型、渲染、Mixin，以及 Sodium/Iris 对应的 Forge 兼容实现。

每一步都要把实际加入 Forge source set 的代码范围写进提交说明，并保持 `classes`、语言检查和最小专用服务器启动可复现。
