# Fabric 1.21.1 安装产物审计

2026-10-15，直接读取 `fabric/build/libs/stardewcraft-fabric-0.6.3fix1-fabric-dev.jar`，未使用开发 class 输出替代安装包检查。此记录证明安装包内容和静态重映射；真实生产服务端、客户端完整资源烘焙及游玩验收分别记录。

- 版本：`0.6.3fix1-fabric-dev`；260,931,189 bytes。
- SHA256：`00eaf7d4e3d7cf2c9a13e83ef8af7d6ee3227e2f536c1c8f8f0d5555d25b1b52`。
- Manifest：`Fabric-Mapping-Namespace: intermediary`、静态 Mixin 重映射；FMJ 已展开版本及名称，三个入口类均存在。

| 检查 | 实际产物结果 |
| --- | --- |
| Mixin | 245 个平台类与 125 个原有类全部存在，两个 JSON 及原插件类齐全。 |
| 原生注解目标 | 559 条方法选择器中，557 条匹配原生类；另外两条为既有 `require=0` 钩子（Inventory 的 `getMaxStackSize`、FishingHook 的 `lavaHurt`），目标类未声明对应方法。467 条原生 `@At` 成员 / `@Accessor` / `@Invoker` / `@Shadow` 引用全部匹配。核对使用官方原生类、tiny 映射及继承映射。 |
| Access widener | intermediary 命名空间，27 条规则，包括 PL 真实元素烘焙所需的 `BlockModel.method_3447`。 |
| 索引 | schema 2：403 个 subscriber 类 / 816 个静态 handler，其中 MOD 168、GAME 648；212 个生产 GameTest 类 / 908 个测试索引；类文件全部存在。 |
| 资源闭包 | 40,016 个 processed resources 全部存在且内容一致（正常重映射的 FMJ/AW 除外）。共享源 39,907 文件中，39,879 文件逐字节一致；排除项仅 27 个 `.DS_Store` 与 Neo AT，零意外缺失。 |
| 原生资产 | NPC manifest 52 项及对应模型、家具生成资源 6 项齐全；51 个 composite JSON 与真实 `CompositeGeometry` 类同时存在。 |
| 内嵌依赖 | PL `gametest/core/render_types/model_loader/model_data` 固定 `3.1.0-beta.91+1.21.1`，及 Maven artifact `3.8.5`；六个 JAR 的 ZIP、FMJ、声明的 AW/Mixin 资源与 PL 模块依赖闭包均合法；许可证文件保留。FAPI 与 FCAP 仍为声明的外部必需依赖。 |
| 泄漏检查 | 无重复 ZIP 条目、平台单元测试类、本机绝对路径、NeoForge loader 类或 Neo loader metadata。FCAP 使用的 `net.neoforged` 配置 API 引用属于真实外部 FCAP 接口。 |

本次包内 subscriber 索引已按 FML 4.0.42 的真实事件参数层次分类，`TemplateClientEvents` 的模型与两类颜色处理函数、`StardewSkyEffects$Registration` 的维度效果处理函数均位于 MOD；手工订阅仍使用显式选择的总线。事件闭包及原 22 个非索引 handler 的去向见 [事件审计](fabric-event-closure-audit.md)。

`<init>` 重映射歧义日志可定位到唯一的多目标构造器注入 `NativeSpawnerOwnersMixin`。实际包仍保留无描述符的 `<init>` 选择器；SpawnerBlockEntity 的一个构造器及 MinecartSpawner 的两个构造器均存在，回调仅接收 `CallbackInfo`。静态检查未发现构造器描述符被错误转换；实际转换覆盖由运行验证记录。

## 可选集成与核实边界

AE2 两条注入原先在 `remap=false` 下保留 named `ItemStack` 描述符，已由严格 SHA 锁定的单文件 overlay 改成唯一外部方法名。正式包中选择器为 `createFacadeForItem` / `createFacadeForItemUnchecked`，回调参数真实转为 `class_1799`。两方法在 [AE2 官方 1.21.1 源码](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1.21.1/src/main/java/appeng/items/parts/FacadeItem.java) 和 [官方 Fabric 1.20.1 源码](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/fabric/1.20.1/src/main/java/appeng/items/parts/FacadeItem.java) 均唯一；[官方下载页](https://appliedenergistics.org/download) 没有 1.21.1 Fabric 产物，因此不将此注解修复称为该组合的运行验收。

Gecko 的可选注入已由单文件严格 SHA overlay 修复。直接下载[官方 Maven 的 GeckoLib 4.8.2 Fabric JAR](https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-fabric-1.21.1/4.8.2/geckolib-fabric-1.21.1-4.8.2.jar)，SHA256 为 `a70c334830decbb9d7cb88478a49d19d6ce823ba3356ca62034a445fef3c2d04`；真实 GeoArmorRenderer 声明 `method_2828(class_4587,class_4588,int,int,int)`，通过 HumanoidModel → AgeableListModel → EntityModel → Model 的官方映射继承链对应开发名 `renderToBuffer`。新候选精确保留这两个已核实名称的 selector，开发和生产环境各匹配一个声明；回调参数实际重映射、两 Shadow 字段及原 `require=0` 均保持。全部 370 个 Mixin 的注解中已无应重映射而残留的 named Minecraft 类引用。

这项 Gecko 验证是实际发布字节码、继承映射和候选注解合同审计；未执行安装 Gecko 后的实际 Mixin 转换或画面渲染，不将它描述为完整运行验收。未新增生产依赖，也未放宽初始化 guard。

原 Neo CTM `onModelBake(...ModelEvent$BakingCompleted)` 字面量属于已有可选目标描述符，未换名伪装为 Fabric CTM 支持；它不构成 Neo loader 类依赖。

完整机器记录在 `fabric/build/research/candidate-artifact-audit/summary.json`，详细条目包括 `artifact-audit.json`、`remapped-mixin-annotations.json`、`native-selectors.json`、`native-members.json`、`annotation-remap-review.json` 与 `shared-resource-closure.json`，以及 `gecko-4.8.2/gecko-selector-audit.json`。这些是本地审计输出，不是发布构建或维护测试的输入依赖。本审计未启动 Gradle、游戏或服务器。
