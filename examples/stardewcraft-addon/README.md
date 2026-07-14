# StardewCraft 0.5 附属 Mod 示例

这个小工程展示十五种 Java 扩展入口：

- 按 `ItemStack` 动态提供星露谷物品元数据；
- 注册自定义 Condition；
- 注册自定义 Item Query；
- 注册服务端 Action；
- 注册带独立进度状态的任务目标类型；
- 注册动态商店库存 Provider；
- 注册服务端剧情触发器；
- 在纯客户端类中注册视觉演出命令；
- 注册特殊订单目标类型；
- 注册特殊订单奖励类型。
- 注册有序 NPC 交互 Provider；
- 注册动态作物、果树、动物和建筑 Provider；
- 注册动态装备元数据和武器技能 Handler；
- 注册矿井怪物 Provider；
- 注册职业效果 Handler，并由示例数据包绑定到 `Tiller` 的售价操作。

先在仓库根目录构建本体 JAR，再编译示例：

```bash
./gradlew jar
./gradlew -p examples/stardewcraft-addon build
```

真实附属工程应把本地 `fileTree` 依赖替换为 StardewCraft 发布后的 Maven 坐标，并保留 `neoforge.mods.toml` 中对 `stardewcraft` 的 `required + AFTER` 依赖。

注册 ID 必须使用附属自己的命名空间。重复 ID 会直接抛出异常，不按加载顺序覆盖。

客户端剧情命令位于独立的 `client` 包，并通过 `Dist.CLIENT` 订阅客户端初始化事件。服务端入口不能直接引用该实现，否则附属会破坏专用服务器启动。

## 0.5 API 验收锦标

这些 Provider 故意使用可重复的条件，避免安装示例后改写整个世界：

| 路径 | 触发条件 | 可观察结果 |
| --- | --- | --- |
| 作物 Provider | Y > 80 的 `stardewcraft:parsnip_crop` | 本体收获链使用 12 点农业经验 |
| 果树 Provider | Y > 80 的 `stardewcraft:apple_tree` | 果树存储链使用金苹果、每次 2 个、最多 4 个 |
| 动物 Provider | Y > 80 的已管理奶牛 | 生产链每 2 天产出蜂蜜瓶 |
| 建筑 Provider | Y > 80 的 coop/barn manager | 建筑容量为 2，且只接受奶牛 |
| 装备 Provider | 任意带附魔光效的钻石剑 | 按公共 `weapon` 槽保存完整物品栈，主技能键（默认右键）调用 `apple_dash` |

静态钻石剑 Data Map 在示例数据包中；带附魔光效时，Java Provider 以更高优先级覆盖它。这同时验收了完整 `ItemStack` 解析、装备后保存/重连和服务端技能 Handler 三条路径。
