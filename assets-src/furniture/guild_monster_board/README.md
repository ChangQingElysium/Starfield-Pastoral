# 公会怪物讨伐告示板

- 原版参考：`AdventureGuild.tmx` 壁炉左侧两列告示、Buildings tile 1306 的 `showMonsterKillList` 交互。参考只用于构造与配色关系；模型、图集重新绘制，未裁切原版贴图。
- 结构：窄木框、三块拼接背板的材质表现、六张钉住的浅色告示。木纹沿板方向，接缝与边框偏暗，纸面保留空白和短灰蓝记录线；中间一张用褐色印章区分。
- 原生 `java_block`，11 个方块/平面，14×28×2 模型单位；64×64 图集，每单位 1 像素。纸张为单面薄片，背面贴在木板上。没有 GeckoLib 或自定义渲染器。
- 可编辑源：`guild_monster_board.bbmodel`（内嵌 PNG）、同名独立 PNG。实际 Blockbench 预览保存在本地 `preview/`，已重新打开源模型并检查正、侧、背面。
- 注册 ID：`stardewcraft:guild_monster_board`。主模型在 `models/decor/wall_decor/common/`，上半部使用空模型并共享有效的不透明 `minecraft:block/spruce_planks` 粒子绑定。导出时保留原生模型 JSON 中的 particle 和 display 字段。
- 已直接写入 `pregen/stardew_valley/region/r.0.-1.mca`，同步 region_manifest 文件大小和 SHA-256；`structures/interior/adventurer_guild.schem` 同步为朝西的源朝向。两份地图均只修改对应三个方块，其余 NBT 和区块内容已比对不变。
- 公会主方块坐标 `(109,61,-150)`，朝南，上半部 `(109,62,-150)`。进入公会后仅在下方为空、上方仍是原版占位清单且墙面有效时迁移；原 `(108,61,-150)` 清单仅在状态仍匹配时移除。其他纸张和玩家家具不动。
- 点击上下半部均使用原进度交互；领取奖励仍由 Gil 负责。地图入口见 `data/stardewcraft/map_interactions/adventure_guild_board.json`。
- 验证包括像素密度、模型/图集绑定、四朝向主/扩展状态、编译、12 语言一致性及定向服务端测试。尚未进行游戏客户端视觉验收。
