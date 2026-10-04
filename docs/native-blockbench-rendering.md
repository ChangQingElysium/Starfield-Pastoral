# Blockbench 原生渲染与 GeckoLib 移除

2026-09-29：StardewCraft 自身模型的编译、加载和播放不再依赖 GeckoLib。

## 当前实现

- 已移除 `build.gradle` 中的 GeckoLib Maven 仓库、`implementation`、`jarJar`，以及版本属性。
- 原 `GeoEntity` / `GeoBlockEntity`、控制器和实例缓存改为项目自己的 `AnimatedModel` / `ModelAnimation` 状态描述。普通实体与方块实体的公共代码不引用客户端或第三方动画类型。
- `client/model/nativebb/BlockbenchDecoder` 将已交付的数值型 Blockbench / Bedrock 导出数据转换为现有 `NativeNpcModel`，由 `NativeNpcPose` 计算姿态。支持骨骼层级、立方体局部旋转、膨胀、盒式及逐面 UV、UV 旋转、镜像，以及位移、旋转、缩放轨道。Catmull–Rom 曲线在加载时以每秒至少 120 段采样，关键帧边界及 pre/post 跳变保留。
- 已使用原生 NPC、怪物和家具路径的资产保持原实现；剩余动物、祝尼魔、乌鸦、节庆实体、商人、旧 NPC 资源、过场演员和方块实体改用原生提交顶点。
- 已保留方块朝向、染色箱纹理、开闭盖、出货箱紧凑尺寸及投货挂点、果树挂果数量、坩埚蒸汽/发光火焰、祝尼魔染色/淡出/持物、水母全亮与透明度、NPC 头顶提示。每个对象独立保存播放时钟与过渡姿态，资源重载后重建对应姿态。

`geo/*.geo.json` 和 `animations/*.animation.json` 继续作为现有的编辑器导出数据使用，不需要安装任何库。这次不批量重做或重命名资产；已有 `GeoModel` / `GeoRenderer` 类名中的 Geo 仍表示几何资源，不再继承第三方类。新资产优先走项目已有的 Blockbench 编译流程。家具和灯具仍遵循 `AGENTS.md` 的原生方块/平面要求。

## 可选第三方盔甲兼容

钓鱼时隐藏其他模组的动画盔甲仍由 `OptionalAnimatedArmorFishingMixin` 处理。它仅以字符串指定目标，并由 Mixin 插件在外部已安装、版本为 4.8.2 的 GeckoLib 上启用；没有导入、继承或方法签名依赖。未安装时跳过。StardewCraft 不下载、不打包该库，也不通过此钩子渲染自己的任何模型。

## 验证

```sh
./gradlew classes
./gradlew -I compatibility/native-blockbench-tests.gradle classes test
./gradlew dependencyInsight --dependency geckolib --configuration runtimeClasspath
```

本次 6 项定向测试通过，覆盖 80 个几何文件、22,958 个四边形、58 个动画文件，以及坐标/UV、Catmull–Rom/pre-post、实例隔离、循环/单次/保持/停止/过渡、初始闭盖和关键挂点。NeoForge 无窗口测试进程中也确认 GeckoLib 类不存在；运行时依赖查询无匹配，生产字节码无第三方动画类型描述符。

测试位于受版本管理的 `compatibility/native-blockbench`，只读取本仓库正式资源，不依赖 `src/test`、编辑器源目录或本机缓存。

## 尚未覆盖的边界

没有启动游戏客户端，也没有构建 JAR。本次证据确认代码和资源数据路径已独立；游戏内光照、透明排序、实际朝向、持物位置和切换观感，以及外部盔甲兼容钩子的实机效果仍需在现有客户端验收，不能由编译或无窗口测试代替。
