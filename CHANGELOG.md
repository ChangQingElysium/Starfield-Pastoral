# Changelog

## 0.5.3 - 2026-07-27

### Update Log (English)

#### Addon And Data-Pack Platform

- Added a generic, namespaced extension platform for future addons instead of special-casing any existing addon. Stable content IDs, typed references, aliases, deterministic ordering, conflict diagnostics, lifecycle freezing and atomic reload snapshots now form a shared foundation across the major game systems.
- Added compatibility-managed world, farm, player, NPC, animal, building, machine, location, festival-session and long-term progress state. Unknown platform-owned namespaces survive save/load while their addon is absent, and migration or cleanup remains explicit and previewable.
- Added public API maturity classification and binary compatibility baselines. New surfaces remain experimental until their documentation, independent consumers, runtime behavior, multiplayer semantics and migration evidence justify promotion.

#### Maps, Content And Production

- Added extensible locations, regions, map slots, world anchors, farm layouts, portals and building blueprints so new maps can be referenced by NPC schedules, festivals, fishing, forage, quests, music and travel without adding core coordinate branches.
- Added typed extension paths for crops, trees, managed animals, animal buildings, machines, production plans, fishing, fish ponds, mine monster profiles, combat events and transactional world events.
- Added synchronized Data Map food effects for arbitrary registered Minecraft or addon foods. Existing food items keep their own consumption behavior while Stardew energy, health, buffs and effects are applied by data.
- Replaced duplicated per-flavor juice and wine assets with shared tinted drink models while preserving legacy flavored output identities and quality presentation.

#### NPCs, Shops, Festivals And Progress

- Added namespaced NPC profiles, displays, entity resolution, lifecycle events, schedules, dialogue, gift tastes, friendship rewards, social rules and shop bindings.
- Fixed the Social tab crashing when a dateable core NPC was projected through the new display API. Unified profiles and legacy core capability data now keep fallback and addon-provided display metadata consistent, and the Russian tab label now reads “Relationships” instead of the ambiguous “Secular”.
- Added extensible currencies, compound costs, stock policies, products, server-authoritative purchase preparation and idempotent transaction handling.
- Added festival map overlays, participants, mechanics, activities, rewards, persistent sessions and epoch/revision synchronization, together with extensible progress domains, requirements, rewards and production events.

#### Multiplayer, Client Fallbacks And Diagnostics

- Added optional and required network-capability negotiation, bounded synchronized content snapshots, stale-revision rejection, disconnect cache cleanup and server-authoritative client views.
- Missing translations now fall back to readable stable IDs, and missing portraits or display resources no longer hide gameplay entries. A failed client snapshot refresh keeps the previous complete revision.
- Added extension diagnostics, state inspection and maintenance commands, failure isolation, deterministic random streams, bounded spatial indexes and performance budgets for high-frequency systems.

#### Examples, Compatibility And Verification

- Expanded the independent Java addon and data-pack examples into vertical samples covering maps, NPCs, shops, festivals, progress, animals, farming, machines, trees, fishing, mining, combat and world events.
- Added public API compatibility checks, per-type maturity review, atomic reload and legacy-state tests, fixed-commit external addon canaries and a stricter runtime smoke verifier.
- Verified a real graphical client joining a copied existing world on a dedicated server, forcibly disconnecting, reconnecting, re-negotiating capabilities, reloading player data and completing an orderly five-dimension save.
- Updated the public mod version to `0.5.3`.

### 更新日志（中文）

#### 附属模组与数据包平台

- 新增面向未来未知附属的通用命名空间扩展平台，不为任何现有附属增加专用判断。稳定内容 ID、强类型引用、别名、确定性排序、冲突诊断、生命周期冻结和原子 reload 快照现在共同服务于主要游戏系统。
- 新增由平台管理的世界、农场、玩家、NPC、动物、建筑、机器、地点、节日会话和长期进度状态。附属暂时缺席时，平台管理的未知命名空间数据仍可跨读写保存；迁移和清理必须显式预览后执行。
- 新增公共 API 成熟度分类与二进制兼容基线。新入口在文档、独立消费者、运行时、多人语义和迁移证据充分前继续保持实验状态，不因版本发布批量稳定化。

#### 地图、内容与生产

- 新增可扩展地点、区域、地图槽位、世界锚点、农场布局、传送和建筑蓝图。新地图可被 NPC 日程、节日、钓鱼、采集、任务、音乐和旅行共同引用，不再要求本体增加专用坐标分支。
- 为作物、树木、托管动物、动物住所、机器、生产计划、钓鱼、鱼塘、矿井怪物、战斗事件和事务化世界事件补充强类型扩展路径。
- 任意已注册的 Minecraft 原版或附属食物均可通过同步 Data Map 获得星露谷能量、生命、增益和效果，同时保留物品原有的进食行为。
- 将重复的逐口味果汁与果酒资源收敛为可着色的通用饮品模型，并保留旧口味产物身份和品质显示兼容。

#### NPC、商店、节日与进度

- 新增命名空间 NPC Profile、展示、实体解析、生命周期、日程、对话、礼物喜好、友情奖励、社交规则和商店绑定。
- 修复可约会核心 NPC 经新展示 API 投影后导致社交页崩溃的问题。统一 Profile 与旧核心 capability 现在会统一 fallback 和附属提供的 Display 元数据，并将容易被误解为“世俗”的俄语页签改为“关系”。
- 新增可扩展货币、复合成本、库存策略、商品、服务端权威购买准备和幂等事务处理。
- 新增节日地图覆盖、参与者、玩法、活动、奖励、持久会话与 epoch/revision 同步，并补充可扩展进度领域、条件、奖励和生产事件。

#### 多人、客户端降级与诊断

- 新增可选/必需网络能力协商、有界内容快照、旧 revision 拒绝、断线缓存清理和服务端权威客户端视图。
- 缺少翻译时显示可读稳定 ID，缺少肖像或展示资源时不再隐藏玩法条目；客户端快照刷新失败时保留上一份完整 revision。
- 新增扩展诊断、状态检查与维护命令、失败隔离、确定性随机流、有界空间索引和高频系统性能预算。

#### 样例、兼容与验证

- 将独立 Java 附属和数据包样例扩展为贯穿地图、NPC、商店、节日、进度、动物、农场、机器、树木、钓鱼、矿井、战斗和世界事件的纵向样例。
- 新增公共 API 兼容检查、逐类型成熟度复核、原子 reload 与旧状态测试、固定提交的外部附属 canary，以及更严格的运行时 smoke 验证器。
- 使用真实图形客户端连接用户旧档副本专服，完成强制断线、重新连接、能力重新协商、玩家数据重载和五维度正常保存。
- 项目公开版本号更新为 `0.5.3`。

## 0.5.2 - 2026-07-22

### Update Log (English)

#### Dark Talisman And Witch Questline

- Implemented the Wizard's late-game quest chain from the Dark Talisman opening event through Krobus, the Mutant Bug Lair, the Witch's Swamp, the Henchman, Magic Ink and the final magic-building catalogue unlock.
- Added the Mutant Bug Lair and Witch's Swamp as dedicated gameplay areas with their own biome treatment, music, fishing pools, portals, access seals and monster rules. Dark Talisman, Henchman and Magic Ink progression is tracked independently for every player in multiplayer.
- Added the supplied Henchman, magic catalogue and related world assets, source-style quest feedback, per-player one-time rewards and cutscene/camera support for the new events.

#### Wizard Buildings

- Added the Wizard's source-style magical catalogue for obtaining placeable Junimo Hut, Earth Obelisk, Water Obelisk, Desert Obelisk, Island Obelisk and Gold Clock items. Purchases grant the selected item directly instead of entering Robin's construction-placement workflow.
- Implemented model-sized placement, rotation, collision and farm ownership checks for the magical buildings. Buildings can be reclaimed with the appropriate tool, drop exactly one item from any occupied cell and now use building-icon hit and destruction particles across their full footprint.
- Implemented functional obelisk warps and restored the Return Scepter to its original single-purpose farm-return behavior while preserving its animated name, enchantment glint and special tooltip styling.
- Implemented Junimo Hut crop harvesting with worker spawning, source-style movement and return behavior, held-item animations, sounds, item storage and raisin support. Improved three-dimensional navigation and harvesting arrival handling around the hut and crops.
- Implemented the Gold Clock's active/inactive states, live hour and minute hands, source-aligned farm protection and a solid full-box collision volume. Corrected model orientation, UV handling, textures and hand pivots.

#### Farm, Fishing And Runtime Parity

- Reworked daily farm weeds, stones, existing fallen-log spreading, seasonal grass behavior and fence decay toward the original game rules. Active Gold Clocks suppress the relevant debris and decay processes without introducing unsupported debris blocks.
- Hardened farm lifecycle and offline catch-up with ownership-aware occupancy tracking, targeted temporary chunk leases and deterministic crop, tree and sprinkler plans. Added safe cleanup and instrumentation for expensive farm operations.
- Reworked fishing location resolution and source data for regular areas, festivals, mine floors, the Mutant Bug Lair and Witch's Swamp. Fixed stacked duplicate pools, first-catch festival locking, mine rare-fish chances, random catch groups, year/luck conditions and several multiplayer session edge cases.
- Added Data Map compatibility for external Minecraft and third-party foods so configured items retain their original food behavior while applying Stardew energy and health effects inside Stardew dimensions.

#### Administration, Compatibility And Verification

- Added `/stardew perf start|stop|status|reset` for opt-in server performance diagnostics and a persistent action for dismissing the login community announcement. Routine development logging now defaults to INFO.
- Improved synchronized client-content snapshot handling, NPC and cutscene state cleanup, festival/fishing isolation, area protection and several portal, interaction and time-flow edge cases uncovered during the new quest implementation.
- Completed the new Wizard quest and building text across all 12 shipped languages and expanded regression coverage for cutscenes, quest resources, areas, fishing, farm catch-up, performance tracking, external food compatibility, navigation and Gold Clock time display.
- Updated the public mod version to `0.5.2`.

### 更新日志（中文）

#### 黑暗护身符与女巫任务线

- 实装法师后期任务链：从“黑暗护身符”开场事件、科罗布斯与突变虫穴，一直到女巫沼泽、哥布林、魔法墨水和最终解锁魔法建筑目录。
- 将突变虫穴与女巫沼泽接入正式游戏区域，补全专属群系、音乐、钓鱼池、传送、封印与怪物规则。黑暗护身符、哥布林和魔法墨水进度在多人游戏中按玩家独立记录。
- 接入哥布林、魔法目录及相关场景资源，补全原版风格任务反馈、每玩家一次性奖励，以及新事件所需的过场和摄像机支持。

#### 法师建筑

- 新增原版风格的法师魔法建筑目录，可获得并放置祝尼魔屋、土之图腾柱、水之图腾柱、沙漠图腾柱、姜岛图腾柱和黄金时钟。购买后直接获得对应物品，不进入罗宾建造定位流程。
- 为魔法建筑实装按模型尺寸的放置、旋转、碰撞与农场权限校验。现在可用对应工具敲掉并回收，从任意占地格破坏都只掉落一份建筑物品，命中与破坏碎片也会按整座建筑的占地范围和物品图标生成。
- 实装各图腾柱传送，并将传送法杖恢复为原版“返回权杖”的单一返回农场功能，同时保留流光名称、附魔光效与特殊提示框。
- 实装祝尼魔屋自动收割：工作者生成、原版风格行走与返回、手持物动画、音效、物品存储与葡萄干效果均已接入；同时改进小屋和作物周围的三维寻路与到达判定。
- 实装黄金时钟开启/关闭状态、实时时针与分针、原版规则的农场保护，以及一体化箱式碰撞。修正模型朝向、UV、开关贴图与指针枢轴。

#### 农场、钓鱼与运行逻辑对齐

- 按原版规则重做农场每日杂草、石头、现有倒地原木扩散、季节牧草及篱笆腐烂逻辑。开启的黄金时钟会正确拦截对应杂物与腐烂流程，不再引入项目中不存在的杂物方块。
- 通过按农场归属的占用跟踪、定向临时区块租约，以及确定性作物/树木/洒水器计划，加固农场生命周期与离线追赶；同时增加安全清理与高开销农场操作计时。
- 重做普通地点、节日、矿井、突变虫穴和女巫沼泽的钓鱼区域解析与原版数据。修复钓鱼池重复叠加、节日首鱼锁死、矿井稀有鱼概率、随机捕获组、年份/幸运条件及若干多人会话边界问题。
- 新增 Data Map 外部食物兼容：配置后的 Minecraft 原版或第三方食物会保留原有进食逻辑，并在星露谷维度内结算对应能量与生命效果。

#### 管理、兼容与验证

- 新增 `/stardew perf start|stop|status|reset` 指令，用于按需开启服务端性能诊断；登录社区公告增加持久化关闭操作。日常开发日志默认级别改为 INFO。
- 改进客户端内容快照同步、NPC 与过场状态清理、节日/钓鱼隔离、区域保护，以及新任务实装中暴露的若干传送、交互与时间流边界问题。
- 为全部 12 种已发布语言补齐新增法师任务与建筑文本，并扩展过场、任务资源、区域、钓鱼、农场追赶、性能统计、外部食物、寻路与黄金时钟显时的回归验证。
- 项目公开版本号更新为 `0.5.2`。

## 0.5.1fix4 - 2026-07-18

### Update Log (English)

#### Photography Actor Commands

- Added a dedicated, temporary actor system for screenshots and video production. Use `/stardew actor spawn <npc>` (for example, `/stardew actor spawn abigail`) or `/stardew actor list` to discover supported character IDs.
- Added commands to place actors at coordinates, face the camera, rotate them, switch idle/walk animations, select the nearest actor or all actors, and remove or clear actors after filming.
- Added deterministic point-to-point movement with `/stardew actor walk`: actors can walk from A to B at a chosen speed, keep their animation and facing synchronized, stop on command, and finish exactly at the requested destination.
- Photography actors are isolated from real NPCs and do not participate in schedules, dialogue, friendship, shops, saves, collision, or normal AI.

#### Shop Transactions

- Restored the original purchase flow: bought items attach to the cursor and must be placed into an inventory slot manually instead of being inserted automatically.
- Added persistent server-side pending-pickup storage so purchased items remain recoverable across menu closure, reconnects, or a temporarily full inventory without duplicating or losing the transaction.
- Fixed selling so the server validates and removes the exact selected stack and quantity before paying the player. Sell sounds now play only after a confirmed successful sale, and transaction replies are scoped to the correct shop.

#### Menus And JEI

- Fixed JEI overlapping and squeezing the Skills, Social, Collections, Powers, Options, Exit, and other non-item tabs. These pages now reserve the full screen, while JEI remains available in the Inventory and Crafting tabs.
- Updated JEI exclusion bounds dynamically when switching tabs and included the Crafting trash can in the protected layout area.

#### Rewards And World Interactions

- Strengthened the Secret Note 23 reward flow: Bear's Knowledge is now represented by a permanent special item, stays synchronized with the player's reward state, displays its own tooltip and effects, and continues to apply the berry sell-price bonus.
- Added the Old Master Cannoli interaction: each player can offer a Sweet Gem Berry once to receive the Stardrop reward, with persistent claim tracking, feedback effects, and a portal hint.
- Added synchronized physical keepsakes and item tooltips for permanent player powers, including automatic backfilling for existing saves.
- Corrected locked-area rollback at the Secret Woods boundary so players are returned to a safe position without creative or spectator bypasses accidentally weakening the progression gate.

#### Compatibility And Verification

- Updated the affected item resources and all 12 shipped language files.
- Added regression coverage for actor spawning and movement, shop escrow and selling, JEI tab bounds, special-item synchronization, Secret Note 23, Old Master Cannoli, and area access rollback.
- Updated the public mod version to `0.5.1fix4`.

### 更新日志（中文）

#### 摄影演员指令

- 新增专用于截图和视频拍摄的临时演员系统。可使用 `/stardew actor spawn <npc>`（例如 `/stardew actor spawn abigail`）生成角色，也可用 `/stardew actor list` 查看支持的角色 ID。
- 新增坐标放置、面向镜头、旋转、待机/行走动画、选择最近或全部演员，以及拍摄结束后移除或清空演员等指令。
- 新增 `/stardew actor walk` 定点移动：演员可按指定速度从 A 点走到 B 点，自动同步朝向和动画，支持中途停止，并精确停在目标位置。
- 摄影演员与真实 NPC 完全隔离，不参与日程、对话、好感、商店、存档、碰撞或普通 AI 行为。

#### 商店交易

- 恢复原有购买流程：购买后的物品会附着在鼠标指针上，需要玩家手动放入物品栏槽位，不再自动塞入背包。
- 新增服务端持久化的待领取物品暂存；即使关闭商店、重新连接或背包暂时已满，已付款物品仍可安全取回，不会复制或丢失。
- 修复出售逻辑：服务端会先校验并移除被点击槽位中的准确物品与数量，再向玩家付款。出售声音只会在服务端确认成功后播放，交易结果也会限定到正确的商店。

#### 菜单与 JEI

- 修复 JEI 与技能、社交、收藏、能力、选项、退出等非物品页面互相挤压和重叠的问题。这些页面现在会完整占用屏幕，JEI 仅在物品栏与制作页面继续显示。
- 切换标签页时会动态刷新 JEI 排除区域，并将制作页面的垃圾桶纳入布局保护范围。

#### 奖励与世界交互

- 加固秘密纸条 23 的奖励流程：“熊的知识”现在拥有永久特殊物品，会与玩家奖励状态同步，显示专属提示和效果，并继续正确应用浆果售价加成。
- 实装老坎诺利交互：每名玩家可献上一颗宝石甜莓并领取一次星之果实，领取状态会持久保存，同时提供反馈效果与传送门提示。
- 为永久玩家能力补充可同步的实体纪念物品和物品提示，并为已有存档自动补发缺失物品。
- 修正秘密森林边界的未解锁区域回退逻辑，玩家会被送回安全位置，创造或旁观模式也不会意外削弱该进度门槛。

#### 兼容性与验证

- 更新相关物品资源与当前发布的全部 12 种语言。
- 新增演员生成与移动、商店暂存与出售、JEI 标签页边界、特殊物品同步、秘密纸条 23、老坎诺利及区域访问回退的回归测试。
- 项目公开版本号更新为 `0.5.1fix4`。

## 0.5.1fix3 - 2026-07-17

### Update Log (English)

#### Animal Purchase And Management Interfaces

- Rebuilt the animal purchase flow around the original Stardew Valley `PurchaseAnimalsMenu`: extracted standalone source textures, source-proportioned animal sprites, centered scroll labels, responsive 1.5x presentation, original-style hover information, sounds and transitions now replace the previous development UI.
- Added a dedicated Stardew-style building list for selecting a coop or barn. Animal naming now uses a compact, readable prompt with random-name support, while purchase submission and failure results remain server-authoritative.
- Reworked the animal information screen with correctly scaled text, hearts and controls. Moving an animal now opens the same building-list presentation instead of the old world-selection overlay, and all three screens fit consistently across GUI scales.

#### Time, Cutscenes And Daily Settlement

- Moved StardewCraft to its own persistent shared day-time clock, independent from Overworld sleeping, commands and daylight rules. Fractional time multipliers now accumulate precisely, and old saves migrate from their stored Stardew date and time.
- Hardened pause rebasing around sleep, festivals and scripted time jumps. Cutscene skipping can close blocking cutscene dialogue correctly, scripted actors retain their animations, and moving NPCs stop, turn toward the player quickly, then open dialogue.
- Daily settlement now loads only the farm chunks required by crops, trees, sprinklers, animals and buildings, releases only tickets created by StardewCraft, and isolates individual manager failures so one broken subsystem cannot cancel the rest of the new day.
- Fixed farms failing to grow when their owner passes out away from the farm or when relevant chunks are unloaded. Pass-out results are tied to the correct settlement day, and collapsing near a bed on the player’s own farm no longer applies rescue fees or mail.

#### World Data, Furniture And Interaction Safety

- Replaced the prebuilt-region installer with a versioned, hashed manifest and staged atomic replacement. Public-map updates verify every bundled region, delete retired managed regions without touching player farm regions, preserve already-modified installed chunks during normal startup, and include a reproducible setup script.
- Restored dedicated Stardew bed placement and lying behavior while allowing valid wall, bedside-table and chest adjacency. Cushion seating now uses a managed seat entity and matching renderer instead of relying on the previous block-only behavior.
- Disabled the Wizard Tower exit while a farm is still being generated and ensured the completion warp is not blocked by a stale portal cooldown. Several portal bounds, ladder orientation and public interaction checks were tightened.

#### Festivals, Shops And Recovery

- Completed the Secret Note 23 bear-knowledge branch: finding the note starts the source quest, bringing Maple Syrup to the Secret Woods plays the dedicated bear event, unlocks the Bear's Knowledge power, and triples Salmonberry and Blackberry sell prices.
- Unified Fair Star Tokens and Calico Eggs under the festival currency HUD state, with automatic visibility updates when entering or leaving the relevant festival area. Festival winner text is now synchronized as localized components for multiplayer clients.
- Added a server-authoritative Lewis lost-and-found inventory. Uncollected Fair grange-display items return on the following day, notify the farm team and can be reclaimed safely through the existing shop interface.
- Fixed Fair fishing sessions carrying into the minigame, protected festival scenery and non-display tables, improved shop transaction validation and pickup handling, and corrected several menu, quest, notification and festival layout issues.

#### Compatibility And Verification

- Updated all 12 shipped language files for the new animal, lost-and-found, festival and interaction text, and refreshed the affected extracted GUI and entity assets.
- Added regression coverage for the independent clock, pause rebasing, pass-out settlement, temporary farm chunk leases, Community Center client state and the hashed prebuilt-region manifest.
- Updated the public mod version to `0.5.1fix3`.

### 更新日志（中文）

#### 动物购买与管理界面

- 依照星露谷原版 `PurchaseAnimalsMenu` 重做动物购买流程：使用从原版大图截出的独立贴图、原比例动物图标、严格居中的卷轴文字、响应式 1.5 倍显示，以及原版风格的悬浮信息、声音和过渡，替换此前的开发中界面。
- 新增星露谷风格的鸡舍/畜棚列表选择界面。动物命名改为紧凑、清晰的输入提示并支持随机名称；购买提交与失败结果继续由服务端权威校验。
- 重做右键动物的信息界面，统一文字、爱心和按钮比例。改变住所现在打开同一套建筑列表，而不是旧的世界选择覆盖层；三个界面在不同 GUI 缩放下保持一致布局。

#### 时间、过场与每日结算

- 为 StardewCraft 建立独立持久化的共享时间，不再受主世界睡眠、命令和昼夜规则影响；小数时间倍率会精确累计，旧存档会依据已有星露谷日期与时间自动迁移。
- 加固睡眠、节日和脚本跳时期间的暂停重基准。跳过过场会正确关闭阻塞中的过场对话，脚本角色保持动画；移动中的 NPC 会先停止、快速转向玩家，再打开对话。
- 每日结算只按需加载作物、树木、洒水器、动物和建筑所在的农场区块，并且只释放 StardewCraft 自己添加的票据；单个管理器异常不会再中断整个新一天的其余结算。
- 修复玩家在农场外晕倒或相关区块未加载时，农场作物和动物不成长的问题。晕倒结果会绑定到正确的结算日；在自己农场的床附近晕倒不再收取救援费用或发送救援信件。

#### 世界数据、家具与交互安全

- 使用带版本、大小和 SHA-256 的清单重做预生成地图安装器，并通过临时目录进行校验后替换。公共地图更新会验证每个内置区域、删除退役的受管区域且不触碰玩家农场；正常启动不会覆盖已经游玩修改过的区块，同时加入可复现的预生成资源脚本。
- 恢复专用星露谷床的放置与躺卧逻辑，并允许床与有效的墙、床头柜和箱子相邻。坐垫改用受管理的座位实体与专用渲染器，不再只依赖方块本身。
- 农场生成期间禁止通过法师塔出口离开，生成完成后的传送也不会被旧传送门冷却拦截；同时收紧若干传送范围、梯子朝向和公共区域交互检查。

#### 节日、商店与物品找回

- 完成秘密纸条 23 的熊知识流程：发现纸条后会接受原版任务，携带枫糖浆进入秘密森林会播放专用熊事件、解锁“熊的知识”能力，并使美洲大树莓与黑莓售价变为三倍。
- 将星露谷展览会星星币与沙漠节花蛋统一接入节日货币 HUD，并根据玩家是否进入对应节日区域自动显示或隐藏；多人节日获胜文本改为同步可本地化组件。
- 新增服务端权威的刘易斯失物招领。展览会结束后未取回的展台物品会在次日返还、通知整个农场成员，并可通过现有商店界面安全领取。
- 修复普通钓鱼会话残留到展览会小游戏、节日场景和非展台桌子可被误操作的问题；同时加固商店交易与拾取校验，并修正若干菜单、任务、通知和节日布局。

#### 兼容性与验证

- 为新增动物、失物招领、节日和交互文本更新当前发布的全部 12 种语言，并刷新相关独立 GUI 与角色资源。
- 新增独立时钟、暂停重基准、晕倒结算、临时农场区块租约、社区中心客户端状态和带哈希预生成地图清单的回归测试。
- 项目公开版本号更新为 `0.5.1fix3`。

## 0.5.1fix2 - 2026-07-17

### Update Log (English)

#### HUD, Menus And Time Flow

- Rebuilt the V-menu Options entry and HUD editor around live in-game renderers. The main money/time box, energy and health bars, item pickup and text notifications, weapon skills and skill experience can now be dragged and scaled with grid snapping, screen-edge anchoring, persistent positions and one global reset action.
- Repositioned the affected HUD and festival overlays through shared per-element bounds so small windows, high GUI scales and fullscreen layouts keep sensible margins instead of reusing one oversized canvas. Pickup notices and corner text boxes now size themselves from their actual content.
- Improved the Stardew game menu, inventory and crafting interaction paths, including responsive canvas fitting, safer slot mapping and trash behavior, real crafting previews and corrected big-craftable presentation.
- Added synchronized Stardew simulation pause state for menu/non-gameplay screens and multiplayer-aware time progression, including the original-style blinking frozen clock without interfering with black screen fades.
- Fixed cutscene actors sliding or floating by separating clock freezes from world simulation and removing the client-level Minecraft pause override. Scripted fake players now keep their movement animations while the Stardew clock remains frozen.
- Moved delayed gameplay actions onto the pause-aware Stardew simulation timeline and repaired farm creation/loading transitions so progress labels, hand animation and player control remain responsive.
- Kept live auctions, the Night Market Mermaid Show, the warp wheel, fishing and festival minigames outside collective menu pause, and stopped server container state from overriding explicit realtime-screen behavior.

#### JEI And Item Information Overhaul

- Rebuilt JEI integration around the real synchronized data sources for all 13 supported machines, Stardew crafting, cooking, shops, geodes, fish ponds and fishing information. Inputs, fuel, catalysts, trades and outputs are now real JEI slots, including multi-output and dynamic machine behavior.
- Added source-aligned processing information for kegs, preserves jars, fish smokers, dehydrators, seed makers, crystalariums and the remaining supported machines; fixed missing wine, jelly, preserve, smoked-fish and related `R`/`U` navigation.
- Fishing information is now exactly one page per catchable item. Locations, time windows, seasons and weather are merged into that page, duplicate rules no longer create repeated pages, and the opaque “special conditions apply” placeholder was removed.
- Quality and flower-colour variants now share the same recipe lookup identity. Normal, silver, gold and iridium stacks open the same `R`/`U` results, while source-flavoured products such as jelly and roe still retain their meaningful ingredient identity.
- Reworked category visuals with existing Stardew GUI assets, centered partial ingredient grids, compact shop portraits and measured text rows; removed the Minecraft crafting-table identity and decorative red selection frames.
- Expanded server-to-client JEI catalog synchronization and completed the JEI key set across all 12 shipped languages, including special shops, owner-derived shop names, machine labels, conditions and fishing locations.

#### Community Center, Bus And World Parity

- Community Center restoration no longer drops internal multi-block extension pieces such as Broken Safe Boxes or Broken Boilers. Completed Junimo Notes disappear immediately, and finishing every room now restores the remaining central damage and decoration state.
- Bus travel now follows Pam’s selected schedule and physical bus-stop position. A Vault completion by any player unlocks the shared bus schedule, while self-driving is only allowed on valid days when Pam’s chosen schedule does not visit the bus stop.
- Expanded Pam’s bundled schedule data toward the original key and route structure and added an NPC schedule parity audit documenting the remaining project-wide gaps.
- Rotated generated minecart stations to the intended north-south orientation without overwriting player-edited rail layouts during migration.
- Migrated the mountain system totem to `(52, 88, -128)`, made it face south, removed both historical duplicate positions and repaired stale tracker entries.
- Changed the Special Charm reward to the shared object-notification flow instead of presenting it as dialogue spoken by Lewis.

#### Fishing Accuracy

- Replaced cast-power-based fish quality with the original-style clear-water-distance calculation around the bobber, including diagonal shore checks, open-water depth caps and no deep-water credit while casting from water or boats.
- Reworked fish size and quality ordering for targeted bait, initial quality thresholds, stacked Quality Bobbers, perfect catches and the Training Rod’s normal-quality restriction.
- Hardened server-authoritative fishing session and minigame state so water depth, selected catch, completion and quality remain consistent across client/server transitions.

#### Stability, Multiplayer And Interaction Fixes

- Rebuilt managed-animal runtime indexing and chunk recovery so duplicate, invalid or excess managed entities are sanitized without deleting unrelated entities; improved acquisition, synchronization and load recovery around the same stable managed IDs.
- Fixed failed placement beside integrated tree roots, branches and similar multi-block decorations so cancellation preserves the player’s item instead of restoring snapshots after the item has already been consumed.
- Added shared character-name resolution so multiplayer messages prefer the Stardew character name and only fall back to the Minecraft account name for incomplete legacy profiles.
- Tightened payload validation, farm administration, equipment actions, auction flows, player selection and several debug/runtime command paths against stale or invalid client state.
- Added a Scarecrow range preview using the same inclusive circular coverage calculation as crow protection, and expanded shared sprinkler/range rendering support.
- Hardened public-map interaction and farm-area boundaries, including client-visible protection for fixed flower pots and safer handling of decorative/public areas.

#### Compatibility, Data And Verification

- Removed the optional Curios integration and its data/dependency surface; rings and boots continue through StardewCraft’s own complete `ItemStack` equipment storage and migration path.
- Updated JEI to `19.27.0.340`, repaired machine/mining/shape tag coverage, added current item/block shape tags and strengthened automated tag-integrity checks.
- Added broad regression coverage for HUD anchoring, time pause behavior, Pam schedules, fishing depth and quality, JEI recipe semantics, animal recovery, equipment persistence, menu slots, display names, tags and scarecrow coverage.
- Updated the public mod version to `0.5.1fix2`.

### 更新日志（中文）

#### HUD、菜单与时间流逝

- 使用游戏内真实渲染器重做 V 菜单的“选项”入口与 HUD 编辑器。右上角金钱/时间框、能量与生命条、物品拾取与文字提示、武器技能和技能经验现在可以拖动、缩放，并支持网格吸附、屏幕边缘锚定、位置持久化和统一重置。
- 将相关 HUD 与节日覆盖层接入独立组件边界；小窗口、高 GUI 缩放和全屏状态不再共用一个过大的画布。拾取提示与左下角文字框会根据实际内容计算尺寸。
- 改进星露谷游戏菜单、背包和制作交互，包含响应式画布适配、更安全的槽位映射与丢弃逻辑、真实制作预览，以及正确的大型制作物显示。
- 加入服务端同步的星露谷模拟暂停状态，使菜单/非游戏界面与多人时间流逝保持一致；暂停时钟按原版方式闪烁，同时不会干扰黑屏淡入淡出。
- 将时钟冻结与世界模拟分离，并移除客户端层面的 Minecraft 暂停覆盖，修复过场角色直接平移或漂浮的问题；脚本假玩家现在会正常播放移动动画，同时星露谷时钟仍保持冻结。
- 将延迟游戏逻辑迁移到支持暂停的星露谷模拟时间线，并修复农场创建/加载过渡，使创建提示、手部动画与玩家操作不再被时停卡住。
- 实时拍卖、夜市美人鱼演出、传送轮盘、钓鱼与节日小游戏不再触发集体菜单暂停；服务端容器状态也不会再覆盖界面明确声明的实时行为。

#### JEI 与物品信息全面重整

- 使用真实同步数据重建 JEI：覆盖 13 种已支持机器、星露谷制作、烹饪、商店、晶球、鱼塘和钓鱼信息。材料、燃料、催化物、交换物与产物均为真正的 JEI 槽位，并支持多产物和动态机器逻辑。
- 补齐酒桶、腌菜罐、熏鱼机、脱水机、种子制造机、水晶复制机及其余机器的真实加工信息，修复葡萄酒、果酱、腌制品、熏鱼等内容缺少 `R`/`U` 查询的问题。
- 钓鱼信息严格改为每种可钓物品只有一页；地点、时间、季节与天气在同一页合并，重复规则不再生成大量页面，并删除无法说明内容的“需满足特殊条件”。
- 品质与花朵颜色不再拆分配方查询身份。普通、银星、金星和铱星物品会打开相同的 `R`/`U` 结果；果酱、鱼籽等带原材料来源的产物仍保留有意义的来源区分。
- 使用项目已有的星露谷 GUI 资源重排分类界面，按材料数量居中槽位，恢复紧凑商店头像与按行测量的文字布局；移除 MC 工作台语义和无意义的红色选中框。
- 扩展服务端到客户端的 JEI 目录同步，并为当前发布的 12 种语言补齐特殊商店、店主商店名、机器、条件与钓鱼地点等 JEI 文本。

#### 社区中心、巴士与世界一致性

- 社区中心翻新不再掉落 Broken Safe Box、Broken Boiler 等内部多格扩展方块；完成房间后祝尼魔纸条立即消失，完成全部房间后会继续修复中央区域剩余的破损和装饰状态。
- 巴士出行现在检查 Pam 实际选中的日程和她是否已站到巴士站点。任意玩家完成金库都会解锁全局巴士日程；只有 Pam 当天选中的日程根本不经过巴士站时，玩家才可在有效日期自行驾驶。
- 将 Pam 的内置日程补充到更接近原版的 key 与路线结构，并加入 NPC 日程一致性审查文档，记录整个项目仍需处理的差异。
- 将系统生成的矿车站旋转为正确的南北方向；版本迁移不会覆盖玩家之后自行修改的轨道布局。
- 将山区系统图腾迁移至 `(52, 88, -128)` 并朝南，清理两个历史重复点位，同时修复过期的追踪数据。
- 特殊魅力奖励改用共享物品提示，不再伪装成刘易斯对玩家说话。

#### 钓鱼准确性

- 使用浮漂周围的原版式净水距离替代抛竿蓄力计算鱼的品质，包含斜向岸边检测、开放水域深度上限，以及玩家在水中或船上抛竿时不获得深水品质加成。
- 按正确顺序重做鱼尺寸与品质计算，覆盖针对性鱼饵、初始品质阈值、多个品质浮标、完美捕获与训练用鱼竿强制普通品质。
- 加固服务端权威的钓鱼会话和小游戏状态，使水深、选中鱼获、完成状态与品质在客户端/服务端切换中保持一致。

#### 稳定性、多人游戏与交互修复

- 重建受管动物运行时索引与区块恢复流程：重复、无效或超量的受管动物会被安全清理，同时不会误删同区块的其它实体；购买、同步与加载恢复统一使用稳定的受管 ID。
- 修复在树根、树枝等整体多格装饰旁放置失败时物品被吞的问题；现在会在物品消耗前直接取消放置，不再事后恢复方块快照。
- 加入共享角色名解析，多人消息优先显示星露谷角色名；只有资料不完整的旧存档才回退 Minecraft 账号名。
- 加固载荷校验、农场管理、装备操作、拍卖、玩家选择和多项调试/运行时指令，避免旧客户端状态或非法请求影响服务端。
- 为稻草人加入范围预览，并与乌鸦保护使用完全相同的包含边界圆形判定；同时扩展共享洒水器/范围渲染支持。
- 加固公共地图交互与农场边界，包含客户端可见的固定花盆保护，以及更安全的装饰区/公共区域处理。

#### 兼容性、数据与验证

- 移除可选 Curios 集成及其数据和依赖；戒指与靴子继续使用 StardewCraft 自己的完整 `ItemStack` 装备存储与旧存档迁移。
- 将 JEI 更新至 `19.27.0.340`，修复机器、挖掘和形状标签覆盖，加入当前物品/方块形状标签并加强标签完整性自动检查。
- 新增大量回归测试，覆盖 HUD 锚定、时间暂停、Pam 日程、钓鱼深度与品质、JEI 配方语义、动物恢复、装备持久化、菜单槽位、角色名、标签和稻草人范围。
- 项目公开版本号更新为 `0.5.1fix2`。

## 0.5.1fix1 - 2026-07-15

### Update Log (English)

#### Secret Notes And Special Progression

- Completed another source-aligned Secret Note pass: restored natural acquisition for Notes 10, 13, 14 and 20; implemented the temporary Skull Cavern floor-100 Iridium Milk reward, Note 20's Special Charm truck exchange, Note 21's midnight bush event, and the authored buried-treasure and Ornate Necklace interactions.
- Completed the multiplayer-safe Winter Mystery flow with the bus-stop shadow event, player-specific footprints, bush cutscene and Magnifying Glass unlock; Secret Note discovery remains player-specific.
- Fixed Secret Note enumeration in creative/JEI and the player collection page so the registered variants are visible instead of collapsing to a single Note 27 entry.
- Kept intentionally unfinished notes out of natural acquisition and retained continuous in-mod numbering after the intentionally omitted vanilla Note 19.

#### Special Furniture

- Added the dedicated Special Furniture item type with its own tooltip color and decoration-category sorting while excluding it from the Furniture Catalogue.
- Added the Upright Piano, Junimo Plush and Stone Junimo from the authored `tmp` models, with source-aligned names, descriptions, prices, block hardness and appropriate tools.
- Added the Upright Piano to all three Night Market magic-boat inventories for 100,000g.
- Added the source-aligned Note 13 Junimo Plush bush reward during the full displayed 12:00 time slot on day 28, and a multiplayer-safe per-player Stone Junimo reward at the authored map position.

#### Interface, Localization And Rendering

- Improved player-menu profile, skill, mastery, collection and letter layouts, including a smaller Minecraft-skin face portrait, shared text fitting and safe parsing of embedded letter commands.
- Reworked interaction hints into a compact hover-only text treatment with time-based fades while preserving the existing distance-faded world outline.
- Fixed Stardew tool type labels, duplicate tooltip descriptions, several GUI text overflows and multilingual font sizing across the shipped interfaces.
- Updated every language currently shipped by the project: English, German, Spanish, French, Hungarian, Italian, Japanese, Korean, Brazilian Portuguese, Russian, Turkish and Simplified Chinese.

#### Runtime And Asset Fixes

- Improved cutscene cancellation, message commands, precondition handling, event NPC playback and debug tooling used by the newly implemented Secret Note scenes.
- Reworked bookshelf and carpet decoration rendering/placement paths and refreshed affected NPC, festival and GUI assets.
- Restored the Iridium Milk special-item behavior and preserved its special name/type/tooltip presentation.

#### Release

- Updated the public mod version to `0.5.1fix1`.

### 更新日志（中文）

#### 秘密纸条与特殊进度

- 继续按原版源码实装秘密纸条：恢复 10、13、14、20 号纸条自然获取，加入骷髅矿井 100 层的临时铱奶奖励、20 号纸条的特殊魅力货车交换、21 号纸条的午夜灌木事件，以及已确定点位的锄地宝藏和华丽项链流程。
- 完成多人兼容的“冬日谜影”流程，包含巴士站小黑事件、玩家独立脚印、灌木剧情与放大镜解锁；秘密纸条发现状态继续按玩家独立保存。
- 修复创造物品栏、JEI 与玩家收集页的秘密纸条枚举，不再只显示单一的 27 号纸条。
- 尚未实装的纸条继续不会进入自然获取池；原版 19 号纸条按计划省略，Mod 内展示编号保持连续。

#### 特殊家具

- 新增独立的“特殊家具”物品类型、专属 tooltip 颜色与装饰分类排序，同时明确排除在家具目录之外。
- 使用 `tmp` 中的正式模型加入立式钢琴、祝尼魔毛绒玩具和石祝尼魔，并按原版补齐名称、描述、价格、硬度与适用工具。
- 立式钢琴加入夜市三天的魔法船商店，原版价格为 100,000g。
- 加入 13 号纸条对应的祝尼魔毛绒玩具灌木奖励：任意季节 28 日、HUD 显示 12:00 的完整时段可领取；石祝尼魔则在指定地图点位按玩家独立领取，兼容多人游戏。

#### 界面、多语言与渲染

- 改进玩家菜单的档案、技能、精通、收集和信件布局，包含缩小的 Minecraft 皮肤正脸头像、共享文字适配与信件内嵌命令的安全解析。
- 将交互提示调整为简洁的准星悬停文字与基于时间的渐显渐隐，同时保留原有按距离渐显的世界方块选框。
- 修复星露谷农具类型文字、重复描述、多个 GUI 文字溢出与多语言字号问题。
- 更新项目当前发布的全部语言：英语、德语、西班牙语、法语、匈牙利语、意大利语、日语、韩语、巴西葡萄牙语、俄语、土耳其语与简体中文。

#### 运行时与资源修复

- 改进剧情中止、消息命令、前置条件、事件 NPC 播放与调试工具，为本轮秘密纸条剧情提供稳定运行支持。
- 调整书架与地毯装饰的渲染/放置路径，并刷新相关 NPC、节日与 GUI 资源。
- 恢复铱奶特殊物品逻辑，同时保留其特殊名称、类型与 tooltip 表现。

#### 发布

- 项目公开版本号更新为 `0.5.1fix1`。

## 0.5.1 - 2026-07-15

### Update Log (English)

#### Secret Notes And Magnifying Glass

- Added the Magnifying Glass special item and unlock flow, including the source-aligned Winter Mystery bus-stop event, player-specific shadow footprints, bush interaction cutscene, and collection-page integration.
- Added data-driven Secret Notes with continuous in-mod display numbering, creative-inventory variants, source-style text/image presentation, per-player discovery state, and synchronized server/client registries.
- Implemented the currently supported note-linked gameplay: gift-preference reveals, four named trash-can searches for Note 12, the Mermaid Show sequence for Note 15, buried treasure for Notes 16-18, and the Ornate Necklace fishing and delivery flow for Note 25.
- Kept unfinished Notes 10, 13, 14, 20-24 and 26 out of natural acquisition until their gameplay is implemented; original Note 19 remains intentionally omitted and later display numbers close the gap.

#### Player Interface And Localization

- Reworked the player menu collection and skill layouts toward the original game's structure, including a smaller Minecraft-skin face portrait, text fitting fixes, Secret Note entries, read-letter entries, and safe rendering of letter command syntax.
- Rebuilt interaction hints as a compact hover-only overlay with time-based fade animation while retaining the existing distance-faded world outline.
- Fixed Stardew tool type tooltips so translated tool categories no longer expose internal registry IDs or duplicate description lines.
- Expanded language and localized GUI-asset coverage to every language currently shipped by the project: English, German, Spanish, French, Hungarian, Italian, Japanese, Korean, Brazilian Portuguese, Russian, Turkish and Simplified Chinese.

#### 0.5 API Stabilization

- Fixed namespaced addon recipe IDs across shops, unlocks, crafting, cooking and save data, while retaining migration support for existing StardewCraft IDs.
- Unified login and `/reload` client-content synchronization, including replacement of deleted definitions, revision ordering, payload size limits, mail/festival/JEI display snapshots and cache refresh hooks.
- Connected public agriculture and equipment providers to runtime gameplay, preserved full `ItemStack` data in equipment saves and synchronization, and added legacy equipment-save migration.
- Applied active-festival availability conditions consistently, isolated failing dynamic providers, and expanded example addon/data-pack validation and runtime contract tests.

#### Gameplay And Runtime Fixes

- Fixed several shared gameplay consumers exposed by the 0.5 API migration, including crop, fruit-tree, animal, building, equipment, profession, mining, shop, mail and quest paths.
- Improved farm-area resolution, model voxel-shape caching, wild weed models, fishing location data and Spring music resources.

#### Release

- Updated the public mod version to `0.5.1`.

### 更新日志（中文）

#### 秘密纸条与放大镜

- 加入放大镜特殊物品与解锁流程，包含对照原版的“冬日谜团”巴士站事件、玩家独立可见的小黑脚印、灌木互动剧情与收集页接入。
- 加入数据化秘密纸条，支持 Mod 内连续显示编号、创造物品栏全部变体、接近原版的文字/图像展示、玩家独立发现状态与服务端/客户端注册表同步。
- 实装当前支持的纸条关联玩法：喜好揭示、12 号纸条的四个指定垃圾桶、15 号纸条的美人鱼演出顺序、16-18 号纸条的锄地宝藏，以及 25 号纸条的华丽项链钓取与交付。
- 未完成的 10、13、14、20-24 和 26 号纸条在对应玩法实装前不会进入自然获取池；原版 19 号纸条按计划永久省略，后续展示编号自动补位。

#### 玩家界面与多语言

- 按原版结构调整玩家菜单的收集页与技能页，包含缩小的 Minecraft 皮肤正脸头像、文字适配、秘密纸条条目、已读信件条目，以及信件命令语法的安全渲染。
- 将交互提示改为仅准星指向时显示的简洁界面提示，并加入基于时间的渐显渐隐；方块世界选框仍保留原有的距离渐显。
- 修复星露谷工具的类型 tooltip，翻译后不再暴露内部注册 ID，也不再重复显示物品描述。
- 将语言文本与本地化 GUI 资源扩展到项目当前的全部语言：英语、德语、西班牙语、法语、匈牙利语、意大利语、日语、韩语、巴西葡萄牙语、俄语、土耳其语与简体中文。

#### 0.5 API 稳定性修复

- 修复附属 Mod 带命名空间的配方 ID 在商店、解锁、合成、烹饪与存档中丢失命名空间的问题，同时保留现有本体 ID 的迁移兼容。
- 统一登录与 `/reload` 的客户端内容同步，包含已删定义替换、revision 顺序、载荷大小限制、邮件/节日/JEI 展示快照与缓存刷新。
- 将公开农业与装备 Provider 接入实际玩法，在装备存档和同步中保留完整 `ItemStack` 数据，并加入旧装备存档迁移。
- 统一主动节日的可用条件判定，隔离异常的动态 Provider，并扩展示例附属/数据包验证与运行时契约测试。

#### 玩法与运行时修复

- 修复 0.5 API 迁移后暴露的多个共享玩法消费端，涉及作物、果树、动物、建筑、装备、职业、挖矿、商店、邮件与任务。
- 改进农场区域解析、模型体素形状缓存、野草模型、钓鱼地点数据与春季音乐资源。

#### 发布

- 项目公开版本号更新为 `0.5.1`。

## 0.5.0 - 2026-07-14

### Update Log (English)

#### Extensibility Platform

- Added the stable `com.stardew.craft.api.v1` surface and shared namespaced Condition, Action, and Item Query systems so data packs and addon mods can extend existing StardewCraft behavior without patching internal classes.
- Added immutable, versioned and atomic reload snapshots with Codec validation, diagnostics, content hashes and server-to-client synchronization. Invalid candidate data now keeps the last valid runtime snapshot.
- Added synchronized Data Maps and ordered dynamic providers for Stardew item metadata, crops, trees, animals, buildings and equipment, including public handlers for weapon skills, profession effects, mine monsters, festival mechanics and NPC interactions.
- Hardened cutscene state changes with server-authorized event sessions, command manifests, branch locking and replay protection.

#### Quests And Gameplay Progression

- Rebuilt quest loading around Minecraft's `ResourceManager`, namespaced definition IDs and data-pack priority, while retaining migration support for legacy numeric quest IDs and old save data.
- Separated quest definitions from per-player progress, added extensible objective runtimes, shared Condition/Action hooks, data-driven daily quest pools and complete `validate`, `grant`, `complete`, `reset` and `inspect` debug commands.
- Expanded the early quest flow and connected supported deliveries, item removal, rewards, mail, locations and cutscenes through the shared runtime.
- Added the Marnie cave-carrot delivery, Jodi fish-casserole dinner and museum archaeology introduction event flows, plus immediate enter-area trigger evaluation and improved event NPC movement, doors, gravity, cameras and music handling.
- Added the museum lost-book system with world-shared discovery progress, per-player reading state, source-aligned recovery routes and data-driven book text and interaction points.

#### Data-Driven Game Systems

- Migrated 57 built-in static shops and 16 built-in special orders from Java definition tables into the same reloadable public formats available to data packs. Dynamic daily inventories remain runtime providers.
- Opened data-driven definitions for mail, cutscenes, festivals, machines, cooking, crafting, recipe unlocks, fishing treasure, fish ponds, museum rewards, monster-slayer goals, geodes, prize tickets and mine chests.
- Added structured world-loot, artifact-spot, forage-zone, quarry, Skull Cavern reward, mine-theme, location and interior-portal definitions while preserving terrain generation and prebuilt-map movement as explicit Java boundaries.
- Migrated the five mastery reward sets and metadata for all 30 built-in professions, including synchronized client snapshots and public profession effect handlers.
- Routed general selling, shipping, gifting, museum, fish-pond, machine, catalogue, festival-display and tooltip classification through the public item metadata API and extensible item/block tags.

#### Addon Development And Localization

- Added a runnable example data pack and independently buildable example addon covering quests, shops, mail, events, special orders, production, collection, world content and 15 public Provider/Handler APIs.
- Added the complete 0.5 addon and data-pack API guide, source audits, quest ledger and extensibility roadmap.
- Added Russian language support and retained credits for community translation sources; expanded translation auditing to keep supported language files aligned.
- Verified the release with 29 JUnit tests, full classes/JAR builds, the independent addon build, JSON validation, and dedicated-server startup both with and without the example data pack.

#### Release

- Updated the public mod version to `0.5.0`.

### 更新日志（中文）

#### 开放平台与兼容性

- 加入稳定的 `com.stardew.craft.api.v1` 公开接口，以及共享的命名空间 Condition、Action 和 Item Query 系统，让数据包与附属 Mod 无需修改本体内部类即可扩展现有玩法。
- 加入不可变、带版本且原子替换的 reload 快照，包含 Codec 校验、诊断信息、内容哈希与服务端到客户端同步；无效候选数据不再污染当前运行快照。
- 为星露谷物品元数据、作物、树木、动物、建筑与装备加入同步 Data Map 和有序动态 Provider，并开放武器技能、职业效果、矿井怪物、节日机制与 NPC 交互 Handler。
- 通过服务端授权的剧情会话、命令清单、分支锁定与重放保护，改进剧情状态修改的安全性。

#### 任务与游戏流程

- 使用 Minecraft `ResourceManager`、命名空间定义 ID 与数据包优先级重建任务加载，同时保留旧数字任务 ID 和旧存档的迁移兼容。
- 分离任务定义与玩家进度，加入可扩展目标运行时、共享 Condition/Action 钩子、数据化日常委托池，以及完整的 `validate`、`grant`、`complete`、`reset` 和 `inspect` 调试指令。
- 扩展前期任务流程，并将已支持的交付、物品移除、奖励、邮件、地点与剧情接入共享运行时。
- 加入玛妮洞窟萝卜交付、乔迪鱼肉砂锅晚餐与博物馆考古引导剧情，并改为玩家进入区域时立即检查，同时改进剧情 NPC 行走、开门、重力、摄像机与音乐处理。
- 加入博物馆遗失藏书系统，包含世界共享发现进度、玩家独立阅读状态、对照原版的找回途径，以及数据化的书籍正文与交互点。

#### 游戏系统数据化

- 将 57 个本体静态商店与 16 条本体特殊订单从 Java 定义表迁入与数据包共用的可 reload 公开格式；每日动态库存继续由运行时 Provider 负责。
- 开放邮件、剧情、节日、机器、烹饪、合成、配方解锁、钓鱼宝箱、鱼塘、博物馆奖励、杀怪目标、晶球、兑奖券与矿井宝箱的数据化定义。
- 加入结构化世界掉落、蚯蚓点、采集区、采石场、骷髅矿井奖励、矿层主题、地点与室内传送定义；地形生成和预生成地图移动保留为明确的 Java 边界。
- 迁移五系精通奖励和全部 30 个本体职业元数据，包含客户端快照同步和公开职业效果 Handler。
- 将通用的出售、出货、送礼、博物馆、鱼塘、机器、目录、节日展示与 tooltip 分类转入公共物品元数据 API 和可扩展物品/方块标签。

#### 附属开发与本地化

- 加入可直接运行的示例数据包和可独立构建的示例附属，覆盖任务、商店、邮件、剧情、特殊订单、生产、收集、世界内容与 15 类公开 Provider/Handler API。
- 加入完整的 0.5 附属/数据包 API 指南、源码调研、任务总表与开放接口路线图。
- 加入俄语支持并保留社区翻译来源致谢，扩展翻译审计工具以保持已支持语言文件一致。
- 通过 29 项 JUnit 测试、完整 classes/JAR 构建、示例附属独立构建、JSON 校验，以及不带/带示例数据包的专用服务器启动验证本次更新。

#### 发布

- 项目公开版本号更新为 `0.5.0`。

## 0.4.13 - 2026-07-14

### Update Log (English)

#### Night Market

- Added the Winter 15-17 Night Market as a multi-day passive Beach festival, including the confirmed Beach overlay, normal time progression, debug apply support, Beach ocean ambience before opening, Night Market music after 5 PM, and cleanup after the final night.
- Added Famous Painter Lupini with the source-aligned nine-painting three-year rotation, painting furniture assets and icons, 1200g pricing, and the vanilla world/party-shared daily sold state: only one painting is available to the farm each night.
- Added the free daily coffee merchant, 250g farm warper, decoration boat, supported daily magic boat stock, and the Night Market traveling cart with the Year 1 red-cabbage guarantee visit counter updated across all three nights.
- Added the direct-entry 1000g submarine fishing tour using the confirmed entrance, exit and submarine bounds, with the source deep-sea fishing pool and submarine music.
- Added the full 2D Mermaid Show using the original visual and timing sequence, music and effects, clickable clam notes, source password order, delayed pearl reward, and per-player `gotPearl` state.
- Added source-timed Night Market schedule overrides for the 24 currently supported visiting NPCs, using the confirmed Minecraft positions and facings for each night, automatic return to normal schedules, and their original English and Simplified Chinese schedule dialogue.
- Restored vanilla calendar festival markers, animation, lock state, and combined hover text for active festivals, passive festivals, and fishing derbies.

#### Night Market Resources And Runtime

- Added the Night Market Beach schematic, painting models and textures, Mermaid Show screen resources, supported plant/decor shop assets, and the verified Night Market, submarine and Mermaid audio resources.
- Extended shared passive-festival, interaction hint, shop, NPC schedule, fishing-location, music, wall-decoration and farm-warper infrastructure used by the Night Market.
- Fixed passive Night Market time progression, Beach/Night Market music transitions, farm-warper interaction cleanup, deep-sea fishing-area selection, and duplicate managed-animal loading encountered during integration.
- Extended the festival actor-map renderer with source-schedule Night Market point maps for Winter 15, 16 and 17.

#### Release

- Updated the public mod version to `0.4.13`.

### 更新日志（中文）

#### 夜市

- 加入冬 15-17 日多日被动型沙滩夜市，包含已确认的 Beach overlay、正常时间流逝、debug apply、开市前的沙滩海洋环境音、下午 5 点后的夜市音乐，以及最终一夜后的清理。
- 加入著名画家卢皮尼，包含对照源码的九幅画三年轮换、画作家具资源与图标、1200 金价格，以及原版的每日世界/队伍共享售罄状态：每晚整个农场只有一幅可购买画作。
- 加入每日免费咖啡商人、250 金农场传送图腾、饰品商船、已支持的每日魔法商船库存，以及夜市旅行货车；第一年红叶卷心菜保底访问计数会在夜市三天正确递减。
- 使用已确认的入口、出口与潜艇范围加入 1000 金直接入场潜艇垂钓之旅，包含原版深海钓鱼池与潜艇音乐。
- 使用原版画面、时序、音乐与特效加入完整 2D 美人鱼秀，包含可点击的贝壳音符、原版密码顺序、延迟珍珠奖励与玩家独立 `gotPearl` 状态。
- 为当前已支持的 24 名夜市访客加入对照源码到场/离场时间的日程覆盖，使用每晚已确认的 Minecraft 坐标与朝向，支持离场后自动恢复普通日程，并接入原版英文与简体中文日程对话。
- 恢复原版日历中的普通节日、被动节日与钓鱼赛事标记、动画、锁定状态及组合悬停文本。

#### 夜市资源与运行时

- 加入夜市沙滩 schematic、画作模型与贴图、美人鱼秀界面资源、已支持的盆栽/装饰商店资源，以及已核对的夜市、潜艇与美人鱼音频资源。
- 扩展夜市复用的共享被动节日、交互提示、商店、NPC 日程、钓鱼地点、音乐、壁挂装饰与农场传送图腾架构。
- 修复接入期间发现的夜市被动时间流逝、沙滩/夜市音乐切换、农场图腾交互清理、深海钓鱼区域选择，以及受管动物重复加载问题。
- 扩展节日 NPC 点位图工具，可根据原版日程生成冬 15、16、17 日夜市点位图。

#### 发布

- 项目公开版本号更新为 `0.4.13`。

## 0.4.12fix1 - 2026-07-11

### Update Log (English)

- Fixed a dedicated-server startup crash caused by Feast of the Winter Star client UI payloads loading client-only screen classes during network registration.

### 更新日志（中文）

- 修复冬日星盛宴客户端 UI 网络包在注册阶段加载客户端专属界面类，导致专用服务器无法启动的问题。

## 0.4.12 - 2026-07-11

### Update Log (English)

#### Feast of the Winter Star

- Added Winter 25 Feast of the Winter Star as a Year 1 active Town festival, including the confirmed Town overlay, entry/exit boundary flow, time handling, Christmas theme music, debug setup, and restoration of the normal map and NPC schedules.
- Added the Year 1 festival NPC lineup and dialogue routing, Pierre's portrait-free festival shop zone, the supported decoration stock, MoreWalls 19 wallpaper, Carpet 17, and the three vanilla-style daily random stock groups.
- Added deterministic per-player secret-friend and gift-giver assignment, the Winter 18 invitation and Winter 24 reminder letters, year-specific letter-open flags, and the Skills page secret-friend portrait reminder.
- Added the full secret gift exchange flow: direct inventory gift selection, vanilla-aligned gift eligibility, five-times friendship effects without consuming normal weekly gift limits, recipient dialogue, per-giver return gift pools, full-inventory recovery, and multiplayer-isolated state.
- Added the return-gift cutscene using the confirmed player, gift, giver route, and camera points, including source-aligned facing changes, pauses, reactions, gift-box timing, `stoneStep` and `cut` cues, gift reveal, and immediate server-side reward delivery.
- Persisted each player's year-specific gift-given and return-gift-received states through the existing player data system, preventing duplicate gifts/rewards and allowing interrupted exchanges to recover after disconnects or server restarts.

#### Winter Star Resources

- Added the Winter Star Town schematic and registered the Winter Star gift boxes and candy-cane decorations with their blockstates, item models, textures, collision/render counterparts, furniture tags, localization, and creative inventory entries.
- Added the supported Wine and Tea Set reward items and assets, and connected the verified `christmasTheme` and `cut` Wavebank audio resources to the formal sound registry.
- Extended the festival actor-map renderer with a source-map Winter Star Secret Santa point sheet for placement verification.

#### Gameplay and Runtime Fixes

- Added source-aligned shop-door hours, Wednesday closure handling, festival closures, Town Key bypasses, Green Rain exceptions, and extended-hour conditions for the supported Town, Desert, and Joja interiors.
- Made shipping-bin pending contents and overnight settlement ledgers persistent, preserving shipped stacks, profession/book-adjusted prices, availability dates, and offline settlement delivery across logout and server restart.
- Added vanilla-style nearby-NPC reactions to searching trash cans, including dialogue, emotes, chat feedback, friendship loss, and Linus' positive exception.
- Fixed watering-can targeting through crop blocks and multi-block crops, and prevented right-click crop harvesting from consuming watering-can interactions.
- Adjusted the mining-floor HUD around the vanilla offhand slot and reduced routine Joja NPC spawn logging noise.
- Hardened shared active-festival NPC control, overlay restoration, interaction routing, player login/logout handling, and cutscene completion behavior used by the Winter Star integration.

#### Release

- Updated the public mod version to `0.4.12`.

### 更新日志（中文）

#### 冬日星盛宴

- 加入冬 25 日第一年冬日星盛宴主动节日，包含已确认的小镇 overlay、进出包围盒流程、时间处理、圣诞主题音乐、debug 布置，以及节日结束后的地图与 NPC 日程恢复。
- 加入第一年节日 NPC 阵容与对话路由、无头像的皮埃尔节日商店区域、已支持的装饰商品、MoreWalls 19 壁纸、17 号地毯，以及三组接近原版的每日随机商品。
- 加入每名玩家独立且可确定重现的神秘朋友/回礼 NPC 分配，包含冬 18 日邀请信、冬 24 日提醒信、按年份记录的读信 flag，以及技能页右下角的神秘朋友头像提醒。
- 加入完整的神秘礼物交换流程：直接从背包选择礼物、接近原版的礼物合法性、5 倍友谊变化且不占普通每周送礼次数、收礼对话、按回礼 NPC 区分的奖励池、满背包恢复界面，以及多人状态隔离。
- 使用已确认的玩家、礼盒、NPC 路线和摄像机点位加入回礼剧情，包含对照源码的转向、停顿、玩家反应、礼盒出现时机、`stoneStep` / `cut` 音效、礼物揭示，以及同一拍的服务端奖励发放。
- 将每名玩家按年份区分的已送礼/已领取回礼状态写入现有玩家持久化体系，防止重复送礼或重复领奖，并支持断线或服务器重启后继续被中断的交换流程。

#### 冬日星资源

- 加入冬日星小镇 schematic，并注册冬日星礼盒与糖果杖装饰的 blockstate、物品模型、贴图、碰撞/渲染对应模型、家具标签、本地化与创造模式物品栏入口。
- 加入回礼池需要的葡萄酒与茶具物品/资源，并将已核对 Wavebank 的 `christmasTheme` 与 `cut` 音频接入正式声音注册。
- 扩展节日 NPC 点位图生成器，加入基于原版地图的冬日星神秘礼物剧情点位图。

#### 玩法与运行时修复

- 加入对照原版的商店门营业时间、周三休息、节日闭店、小镇钥匙通行、绿雨例外与延长营业时间条件，覆盖已支持的小镇、沙漠和 Joja 室内入口。
- 将出货箱待结算物品与过夜结算账本改为持久化保存，保留出货物品、职业/书籍加成后的价格、可结算日期，并支持退出或服务器重启后补发离线结算。
- 加入搜索垃圾桶时附近 NPC 的原版风格反应，包含对话、表情、聊天反馈、友谊扣除，以及莱纳斯的正向例外。
- 修复水壶透过作物与多格作物时的目标定位，并防止右键作物收获抢占水壶交互。
- 调整矿井楼层 HUD 与原版副手栏的间距，并降低 Joja NPC 常规生成日志噪音。
- 改进冬日星接入使用的共享主动节日 NPC 控制、overlay 恢复、交互路由、玩家登录/退出处理与 cutscene 完成逻辑。

#### 发布

- 项目公开版本号更新为 `0.4.12`。

## 0.4.11 - 2026-07-08

### Update Log (English)

#### SquidFest

- Added Winter 12-13 SquidFest as a passive Beach festival, including date registration, passive start messaging, debug apply support, Beach overlay placement, and Willy's festival schedule override.
- Added SquidFest squid tracking on normal fishing catches, with per-player daily score stats, action-bar progress feedback, reward query flow, and day-specific target thresholds for Winter 12 and Winter 13.
- Added SquidFest reward claiming through Willy's booth, including vanilla-aligned day-specific reward tables, Crabbing Book fallback behavior, per-player daily claim flags, hold-up/item pickup feedback, and multiplayer-safe player state handling.
- Added SquidFest Beach fishing data so normal fishing remains available while festival catches can count toward SquidFest goals.

#### Decorations and Integration

- Added SquidFest promotional poster, SquidFest requirement poster, and Squid Kid painting resources, items, blocks, tags, localization, and catalog integration.
- Added reusable Geo festival decoration block support based on model-derived collision bounds and extension placement, while preserving the Luau totem pole behavior on the shared path.
- Added SquidFest letter background asset support and letter markup parsing for custom letter backgrounds and text color directives.
- Updated the public mod version to `0.4.11`.

### 更新日志（中文）

#### 鱿鱼节

- 加入冬 12-13 鱿鱼节作为沙滩被动节日，覆盖日期注册、被动节日开始提示、调试 apply 支持、沙滩 overlay 放置，以及 Willy 的节日日程覆盖。
- 加入普通钓鱼中的鱿鱼节鱿鱼计数，包含玩家独立的每日分数、action-bar 进度反馈、奖励查询流程，以及冬 12 / 冬 13 不同的每日目标。
- 加入通过 Willy 摊位领取鱿鱼节奖励的流程，包含按原版拆分的两日奖励表、螃蟹秘籍 fallback、玩家独立每日领取 flag、举起物品/拾取提示反馈，以及多人状态隔离。
- 加入鱿鱼节沙滩钓鱼数据，让普通钓鱼保持可用，同时节日期间额外计入鱿鱼节目标。

#### 装饰与集成

- 加入鱿鱼节宣传海报、鱿鱼节需求海报和鱿鱼娃画作资源，接入物品、方块、标签、本地化和目录。
- 加入可复用的 Geo 节日装饰方块支持，基于模型尺寸计算碰撞箱与 extension 放置，同时保留夏威夷宴会图腾柱在共享路径上的行为。
- 加入鱿鱼节信纸背景资源支持，以及自定义信纸背景与文字颜色指令的 letter markup 解析。
- 项目公开版本号更新为 `0.4.11`。

## 0.4.10 - 2026-07-08

### Update Log (English)

#### Festival of Ice

- Added Winter 8 Festival of Ice as an active Cindersap Forest festival, including entry/exit flow, time freeze behavior, forest overlay placement, boundary return handling, festival music, and debug setup hooks.
- Added Festival of Ice NPC placements and dialogue flow, with the shared active-festival NPC actor runtime reused across existing stable festivals.
- Added the Festival of Ice traveling merchant shop, including the available stock supported by current StardewCraft items, decorations, wallpaper, lights, food, and rarecrow resources.
- Added the ice fishing contest flow with Lewis sign-up dialogue, intro/result cutscenes, temporary festival fishing rod handling, fish-only contest catches, HUD timer/fish count, multiplayer scoreboard display, winner handling, repeat-year reward behavior, and cleanup on contest end.
- Added Festival of Ice music/audio wiring, overlay structure resources, and the Winter Star Tree decoration model/item/block integration.

#### Debugging, Cosmetics, and Integration

- Added an in-game point-plan debugging tool for festival placement work, including shift-right-click GUI editing, point capture from player block position/facing, NPC name autocomplete, export/copy, deletion, and unified debug command routing.
- Expanded hat/cosmetic resources and registered Sailor's Cap through the existing hat system for the Festival of Ice fishing reward path.
- Improved active-festival map overlay handling around festival bounds, nearby tree cleanup/restoration, interaction locking, HUD/music state sync, and fishing session compatibility for festival minigames.
- Renamed festival schematic resources to stable English asset names and updated the public mod version to `0.4.10`.

### 更新日志（中文）

#### 冰雪节

- 加入冬 8 冰雪节作为煤矿森林主动节日，覆盖入场/退场、时间冻结、森林 overlay 放置、边界返回、节日音乐和调试布置流程。
- 加入冰雪节 NPC 落点与对话流程，并把现有稳定主动节日统一到共享的节日 NPC actor runtime。
- 加入冰雪节旅行商店，接入当前 StardewCraft 已支持的商品、装饰、墙纸、壁灯、食物和珍奇乌鸦资源。
- 加入冰钓比赛流程：刘易斯报名对话、开场/结算 cutscene、临时节日钓竿、只计数不入包的鱼类捕获、HUD 倒计时/鱼数量、多人 scoreboard、胜负判定、重复年度奖励和赛后清理。
- 加入冰雪节音乐/音效、会场结构资源，以及冬日星树装饰模型/物品/方块接入。

#### 调试、装扮与集成

- 加入用于节日点位制作的游戏内点位方案调试工具，支持 Shift+右键 GUI 编辑、读取玩家整数格坐标和朝向、NPC 名称自动补全、复制导出、删除，并归并到统一调试命令入口。
- 扩展帽子/装扮资源，并通过现有帽子系统注册水手帽，用于冰钓比赛奖励路径。
- 改进主动节日 overlay 边界附近树木清理/复原、交互锁、HUD/音乐同步，以及节日小游戏对钓鱼 session 的兼容。
- 将节日 schematic 资源重命名为稳定英文资源名，并将项目公开版本号更新为 `0.4.10`。

## 0.4.9 - 2026-07-07

### Update Log (English)

#### Spirit's Eve

- Added Fall 27 Spirit's Eve as an active Town festival, including festival entry/exit flow, time freeze behavior, map overlay placement, boundary return handling, festival music, HUD hiding support, and debug setup hooks.
- Added the Spirit's Eve venue layout, Pierre festival shop, NPC placements/routes, festival dialogue access, temporary festival monsters, and festival-only protection/interactions for the maze area.
- Added Spirit's Eve maze reward support with an independent per-player chest claim, Golden Pumpkin reward handling, reward messaging, and a minecart shortcut back from the maze exit.
- Added Spirit's Eve decorations and resources, including the spider statue, jack-o-lantern, passable oak leaves marker block, overlay structure, music, and related sound assets.

#### Items, Cosmetics, and Route Guidance

- Added Golden Pumpkin and Magic Rock Candy as special items with Stardew-style tooltip presentation and vanilla gift taste data.
- Added the Wisp Trail Elixir as a Spirit's Eve-only route guidance consumable that reveals the maze path for a short time, clears near the maze exit, and is sold by Pierre for one Prismatic Shard.
- Added reusable route guidance runtime/client rendering infrastructure and a temporary route editor tool for authoring guided paths.
- Added initial cosmetic hat infrastructure, appearance sync payloads, and starter hat assets for straw hat, earmuffs, and top hat.

#### Fixes and Integration

- Fixed duplicated festival decoration descriptions by relying on the shared Stardew tooltip description handling instead of item-local duplicate desc injection.
- Improved active festival client/HUD behavior so active festival flows can consistently hide the top-right game menu UI.
- Tightened festival boundary return logic so cancelled exit prompts return players to an in-bounds safe point instead of leaving them outside the festival volume.
- Updated the public mod version to `0.4.9`.

### 更新日志（中文）

#### 万灵节

- 加入秋 27 万灵节作为小镇主动节日，覆盖入场/退场、时间冻结、地图 overlay 放置、边界返回、节日音乐、HUD 隐藏支持和调试布置流程。
- 加入万灵节会场布局、皮埃尔节日商店、NPC 落点/路线、节日对话、临时节日怪物，以及迷宫区域的节日限定保护与交互。
- 加入万灵节迷宫奖励流程：每位玩家独立领取宝箱、黄金南瓜奖励、领取提示，以及迷宫终点矿车捷径返回。
- 加入万灵节蜘蛛雕像、南瓜灯、可穿过的橡树树叶？、会场结构、音乐和相关音效资源。

#### 物品、装扮与路径指引

- 加入黄金南瓜和魔法糖冰棍作为特殊物品，包含星露谷式特殊 tooltip 表现和原版礼物喜好数据。
- 加入幽径药水：仅万灵节期间可饮用，可短暂显现万灵节迷宫路径，到达终点附近后自动消失，并在皮埃尔处用 1 个五彩碎片换购。
- 加入可复用的路径指引运行时/客户端渲染基础设施，以及用于绘制路线的临时路径编辑工具。
- 加入初版装扮帽子基础设施、外观同步网络包，以及草帽、护耳、大礼帽等起始帽子资源。

#### 修复与集成

- 修复节日装饰 tooltip 重复描述问题，改为统一依赖通用星露谷描述处理，不再由物品本地重复注入 desc。
- 改进主动节日客户端/HUD 行为，让主动节日流程可以一致隐藏右上角游戏菜单 UI。
- 收紧节日边界返回逻辑，取消离场提示时会把玩家送回包围盒内安全落点，避免停留在节日区域外反复触发提示。
- 项目公开版本号更新为 `0.4.9`。

## 0.4.8 - 2026-07-07

### Update Log (English)

#### Stardew Valley Fair

- Added the Fall 16 Stardew Valley Fair as a full active festival in Town, including festival entry/exit flow, time freeze behavior, music/HUD sync, overlay placement, interaction blocking, and debug-friendly setup hooks.
- Added fairground interaction points for the star token shop, star token purchase stand, fortune teller, fishing game, slingshot game, strength tester, wheel of chance, grill, and grange display judging.
- Added Stardew-style star token handling as festival-only player currency with HUD presentation, purchase flow, shop spending, game rewards, and grange display rewards.
- Added the player grange display flow with a 3x3 display container, per-player displayed items on the fair tables, item eligibility/scoring, Minecraft item scoring extensions, purple shorts handling, Lewis judging flow, and reward/result dialogue.
- Added Lewis' grange judging route across the four display stands, including source-matched stall timing and the return-to-Lewis result handoff.
- Added fair NPC placement/routes and temporary fair animals for the Town venue, with per-festival actor dialogue state for judging and post-judging results.
- Added the fair shop, grill Survival Burger claim behavior, fortune teller dialogue flow, star token purchase flow, and initial fair minigame integrations for fishing, slingshot, strength testing, and wheel betting.

#### Models, Blocks, Items, and Visual Resources

- Added and registered new fair blocks and assets such as the strength tester, wheel of chance, grave stone, grill, and fair overlay structure resources.
- Added new model/resource coverage for furniture, wardrobes, dressers, bedside cabinets, sofas, bushes, weeds, fruit trees, saplings, and additional placed-food displays.
- Expanded cooking and placed-food presentation with more item textures, block models, loot tables, translations, and catalog entries.
- Added fair GUI and HUD assets for star tokens, fair minigames, grange display interaction, fishing results, and Stardew-style object dialogue cleanup.

#### Gameplay, Systems, and Integration

- Added fruit tree runtime support, rendering resources, sapling items, Jade integration, growth management, and related loot/resource data.
- Improved table display rendering and fair table interaction rules so player display tables remain usable while NPC display tables stay protected during the festival.
- Expanded fishing location data and fair fishing session support, including temporary rod handling, actionbar timer/score feedback, and festival-only restrictions.
- Added or updated packet payloads, menu types, client caches, HUD sync, shop flow, dialogue handling, sound registrations, and active festival service hooks needed by the fair.
- Updated the public mod version to `0.4.8`.

### 更新日志（中文）

#### 星露谷展览会

- 加入秋 16 星露谷展览会作为完整主动节日，覆盖小镇入场/退场、时间冻结、音乐/HUD 同步、会场 overlay 放置、交互保护和调试布置流程。
- 加入展览会场交互点：星星币商店、金币购买星星币摊位、占卜、钓鱼游戏、弹弓游戏、测力计、幸运转盘、烤架，以及农庄展览评审。
- 加入展览会限定星星币经济，支持节日期间 HUD 显示、金币购买、商店消费、小游戏奖励和农庄展览奖励。
- 加入玩家农庄展览流程：3x3 展示容器、每个玩家独立的桌面展示物、展示物合法性/算分、Minecraft 物品扩展算分、紫色短裤分支、刘易斯评审流程和奖励/结果对话。
- 加入刘易斯评审四个展示台的移动路线，包含接近原版的停顿时间和回到刘易斯后领取结果的流程。
- 加入展览会 NPC 落点/路线和临时会场动物，并支持评审中与评审后的节日 NPC 对话状态。
- 加入展览会商店、烤架领取救生汉堡、占卜对话、购买星星币，以及钓鱼、弹弓、测力、转盘等初版小游戏接入。

#### 模型、方块、物品与视觉资源

- 加入并注册测力计、幸运转盘、墓石、烤架和展览会会场结构等新节日方块与资源。
- 补充家具、衣柜、梳妆柜、床头柜、沙发、灌木、杂草、果树、树苗和更多可摆放食物的模型/资源覆盖。
- 扩展料理与可摆放食物表现，加入更多物品贴图、方块模型、掉落表、翻译和目录入口。
- 加入星星币、展览会小游戏、农庄展览交互、钓鱼结果和星露谷式物体对话清理相关的 GUI/HUD 资源。

#### 玩法、系统与集成

- 加入果树运行时支持、渲染资源、树苗物品、Jade 集成、生长管理和相关掉落/资源数据。
- 改进桌面展示渲染和展览会桌子交互规则，保证玩家展台可用，同时节日期间保护 NPC 展台不被拿取。
- 扩展钓鱼地点数据和展览会钓鱼会话，支持临时钓竿、actionbar 时间/得分反馈和节日限定使用规则。
- 补充或更新展览会所需的网络包、菜单类型、客户端缓存、HUD 同步、商店流程、对话处理、音效注册和主动节日服务钩子。
- 项目公开版本号更新为 `0.4.8`。

## 0.4.7 - 2026-06-11

### Update Log (English)

#### UI, Dialogue, and Story Flow

- Added a hold-to-skip cutscene control with a top-right HUD indicator, configurable key display, and smooth charge behavior.
- Added a Stardew-style object dialogue/report panel and migrated lightweight prompts such as farm computer output and blocked interaction feedback away from abrupt actionbar-only messages.
- Improved chest and inventory organization support so Stardew-style sorting actions can be wired into the existing menus.
- Expanded special order board UI, quest presentation, related event/cutscene handling, and reward/turn-in flows.
- Added Lewis civic interactions and money-contract flows for shared-money and transfer-style multiplayer utility.

#### Content, Items, and Presentation

- Reworked Stardew item catalog presentation with multi-tab creative inventory organization and shared display ordering for JEI-facing generated stacks.
- Added Popsicle and Fried Chicken & Fries as new cooking dishes, including recipes, unlock paths, translations, item assets, and placeable food presentations.
- Added placeable 3D cooked-food models for the new display pass, covering existing foods such as soups, fish dishes, breakfast foods, pizza, cake, burgers, coffee, and other prepared meals.
- Added rare seed and sweet gem berry content, including crop block/resources, item models, translations, and tags.
- Added treasure totem presentation updates, real icon/sound resources, and related utility sound registrations.

#### Trees, World, and Gameplay Fixes

- Added prefab Stardew tree support with structure resources, placement/registry handling, falling-tree presentation, debug tools, and natural-tree protection logic.
- Restored chopping rewards for natural modern trees, including foraging experience and sap drop behavior while preventing player-placed logs from becoming repeatable XP or sap sources.
- Updated desert galaxy pillar bootstrap behavior so missing pillars can be restored directly instead of relying on brittle version gates.
- Improved forage block/Jade naming, crop model rendering, and Stardew leaf cutout handling while keeping vanilla leaves on the vanilla/optimization-mod path.

#### Systems and Compatibility

- Added auction service, auction UI screens, bidding/join/create payloads, and auction board client state.
- Added farm computer report plumbing and reusable HUD/object dialogue messaging support.
- Expanded packet registration, NPC/event runtime hooks, special-order unlock sync, and interior/portal interaction support.
- Updated prebuilt Stardew Valley region resources and fixed several runtime integration points touched by the new systems.

#### Changes

- Updated the public mod version to `0.4.7`.

### 更新日志（中文）

#### UI、对话与剧情流程

- 加入按住蓄力跳过剧情的控制方式，右上角显示蓄力 HUD，并支持按键显示与平滑蓄力/回退行为。
- 加入接近星露谷风格的物体对话/报告框，并将农场电脑报告、好感门拒绝等轻提示从生硬 actionbar 迁移到可复用面板。
- 改进箱子与背包整理入口，让星露谷式整理功能可以接入现有菜单。
- 扩展特别订单板 UI、任务展示、相关事件/过场处理，以及奖励领取和投递流程。
- 加入刘易斯市政交互与金钱契约流程，支持多人共享金币和金币转让类工具。

#### 内容、物品与展示

- 重做星露谷物品目录展示，加入多标签创造栏组织，并让 JEI 展示栈共用稳定排序逻辑。
- 加入冰棍和炸鸡薯条两道新料理，包含配方、解锁路径、翻译、物品资源和可摆放展示。
- 为本轮料理展示补充大量可摆放 3D 食物模型，覆盖汤、鱼料理、早餐、披萨、蛋糕、汉堡、咖啡等已有料理。
- 加入稀有种子与宝石甜莓内容，包括作物方块/资源、物品模型、翻译和标签。
- 更新宝藏图腾表现，接入真实图标、声音资源和相关工具音效注册。

#### 树木、世界与玩法修复

- 加入预制星露谷树支持，包含结构资源、放置/登记、倒树表现、调试工具和自然树保护逻辑。
- 恢复自然生成现代树砍伐收益，包括采集经验与树液掉落，同时避免玩家放置的原木成为可重复刷经验或树液来源。
- 调整沙漠银河剑柱子恢复逻辑：缺失时直接补放，不再依赖脆弱的版本号门槛。
- 改进采集方块/Jade 命名、作物模型渲染和 Stardew 自定义树叶 cutout 处理，同时让原版树叶保持原版/优化模组路径。

#### 系统与兼容

- 加入竞拍服务、竞拍 UI、出价/加入/创建相关网络包，以及竞拍板客户端状态。
- 加入农场电脑报告链路和可复用 HUD/物体对话提示支持。
- 扩展网络包注册、NPC/事件运行时钩子、特别订单解锁同步和室内/传送门交互支持。
- 更新预生成星露谷区域资源，并修复新系统触及的若干运行时集成点。

#### 变更

- 项目公开版本号更新为 `0.4.7`。

## 0.4.6-fix2 - 2026-06-08

### Update Log (English)

#### Tree and Wood Fixes

- Added Stardew-style stage 0 tree sapling recovery: breaking a newly planted wild sapling with an axe, hoe, or pickaxe now returns the matching tree seed.
- Updated tree tapper validation so modern Stardew trees must keep their generated-tree marker on root, log, and branch parts; rebuilt fake trees no longer count as live generated trees.
- Added migration support so old-save generated modern trees tracked by the wild tree manager can regain live-tree markers when the world advances or a tapper checks the tree.

#### Rendering and Compatibility

- Restored vanilla leaf render handling for Minecraft leaves by removing the global leaves render-type mixin and no longer forcing vanilla leaves into the Stardew cutout layer.
- Kept Stardew tree leaves on their intended cutout presentation without overriding all `#minecraft:leaves` blocks.
- Added missing Jade config localization for the generic utility machine provider, fixing the resource reload failure on game startup.

#### Changes

- Updated the public mod version to `0.4.6-fix2`.

### 更新日志（中文）

#### 树木与木材修复

- 补上星露谷式第 0 阶段树苗回收：刚种下的野树苗被斧头、锄头或十字镐破坏时，会返还对应树种。
- 调整树液采集器判定：现代星露谷树必须保留生成时写入树根、原木和树枝的活树标记；拆掉后再手动摆回的假树不再算作活树。
- 加入旧存档迁移支持：旧存档中已被野树管理器记录的现代生成树，会在日期推进或树液采集器检查时补回活树标记。

#### 渲染与兼容

- 移除全局树叶渲染 mixin，并不再强制原版树叶进入 Stardew cutout 层，让 Minecraft 原版树叶回到原版/优化模组自己的渲染路径。
- 保留 Stardew 自定义树叶所需的 cutout 表现，但不再覆盖所有 `#minecraft:leaves` 方块。
- 补充通用生产设备 Jade provider 的配置翻译，修复进游戏时资源重载失败的问题。

#### 变更

- 项目公开版本号更新为 `0.4.6-fix2`。

## 0.4.6-fix1 - 2026-06-08

### Update Log (English)

#### Tree and Wood Update

- Added the new Stardew tree block set for oak, maple, pine, mahogany, and mystic trees, including root, log, leaves, branch, item model, loot table, and tag resources.
- Replaced the old static tree-preset data path with the new procedural Stardew tree generation flow, including improved footprint handling and legacy tree migration support.
- Updated mystic tree wood textures with the selected muted gray-purple G2 treatment so mystic roots, logs, and branches no longer share the plain mahogany look.

#### Wood Building Blocks

- Added 15 plank variants across the five tree species: normal planks, checkerboard planks, and fish-scale planks.
- Added stairs, slabs, fences, and fence gates for every plank variant, plus log-side stairs and slabs for each tree species.
- Added the new wood building blocks to the Stardew creative tab and Minecraft-compatible plank, wooden stairs, wooden slabs, fence, fence gate, and axe-mining tags.
- Fixed the generated stair blockstates to match vanilla stair rotation data, covering straight, inner, outer, top, and bottom stair shapes correctly.

#### Crafting and Compatibility

- Added vanilla crafting recipes so each new log crafts into four matching planks, and the new planks craft into their building variants.
- Moved generic log-to-wood and stone-to-stone conversion into the Stardew crafting menu with “Any Log” and “Any Stone” ingredient display support.
- Improved Stardew crafting data so tagged ingredients can be consumed correctly and shown with custom display names in the crafting UI and JEI.
- Updated tree and leaf rendering compatibility so Stardew tree leaves use the intended cutout presentation.

#### Changes

- Updated the public mod version to `0.4.6-fix1`.

### 更新日志（中文）

#### 树木与木材更新

- 加入新的星露谷树木方块套件，覆盖橡树、枫树、松树、桃花心木和神秘树的树根、原木、树叶、树枝、物品模型、掉落表和标签资源。
- 将旧的静态树 preset 数据路径替换为新的程序化星露谷树木生成流程，并补上占地处理与旧树迁移支持。
- 按已选的 G2 灰紫方案更新神秘树木质贴图，让神秘树树根、原木和树枝不再直接沿用桃花心木观感。

#### 木制建筑方块

- 加入五个树种共 15 种木板变体：普通木板、棋盘格木板和鱼鳞木板。
- 为每种木板补齐楼梯、台阶、栅栏和栅栏门，并为每个树种加入使用原木侧面贴图的原木楼梯与原木台阶。
- 将新木制建筑方块加入星露谷创造物品栏，并接入 Minecraft 兼容的木板、木楼梯、木台阶、栅栏、栅栏门和斧头挖掘标签。
- 修复生成楼梯方块状态时的旋转表错误，现在直楼梯、内角、外角、上半砖和下半砖形态都对齐原版楼梯数据。

#### 制作与兼容

- 加入原版合成配方：每种新原木可以合成 4 个对应普通木板，新木板可以继续合成对应建筑变体。
- 将通用原木转木材、石头转石头迁移到星露谷制作菜单，并支持“任意原木”和“任意石头”的材料显示。
- 改进星露谷制作数据，让标签材料可以被正确消耗，并能在制作界面和 JEI 中显示自定义材料名称。
- 更新树木和树叶渲染兼容处理，让星露谷树叶按预期使用 cutout 透明表现。

#### 变更

- 项目公开版本号更新为 `0.4.6-fix1`。

## 0.4.6 - 2026-06-02

### Update Log (English)

#### Dance of the Moonlight Jellies

- Added the first playable Dance of the Moonlight Jellies implementation for Summer 28, including Beach-Jellies overlay support, active festival entry and exit handling, time freeze behavior, festival NPC routing, Lewis start confirmation, Pierre's festival shop, and the 24:00 return flow.
- Added the Moonlight Jellies main event presentation with the candle-boat release, synchronized boat and lantern movement, moonlight jelly entities, fade-in jelly visuals, event cleanup, ocean ambience, and the source-verified `moonlightJellies` music cue.
- Added the Summer 28 Demetrius reminder mail and the original-style ending message shown before the event fades out.

#### Content and Presentation

- Added Moonlight Jelly entity resources, renderer support, event music registration, the water lantern decoration, and related models, textures, sounds, cutscene data, language entries, and structure resources.
- Added placeable cooked dish presentation support and supporting block entity/rendering resources for the current food display pass.
- Improved presentation details touched by this pass, including lightning rod rendering, museum/debug display behavior, map decor attachment handling, and related Jade/debug integration polish.

#### Changes

- Updated the public mod version to `0.4.6`.

### 更新日志（中文）

#### 月光水母起舞

- 加入第一轮可玩的夏 28「月光水母起舞」实现，覆盖 `Beach-Jellies` 会场 overlay、主动节日进入与离开、时间冻结、节日 NPC 路由、刘易斯开始确认、皮埃尔节日商店，以及结束后 24:00 回家流程。
- 加入月光水母主事件表现，包括放出烛灯船、船与灯笼同步移动、月光水母实体、渐显式水母视觉、事件清理、海浪环境音乐，以及已按源码索引确认的 `moonlightJellies` 音乐资源。
- 补充夏 28 德米特里厄斯当天提醒邮件，并在主事件 fade out 前显示接近原版语义的结束文案。

#### 内容与表现

- 加入月光水母实体资源、渲染支持、事件音乐注册、水灯笼装饰，以及相关模型、贴图、音频、cutscene、语言和结构资源。
- 加入当前食物展示批次所需的可放置料理表现支持，以及对应方块实体和渲染资源。
- 改进本轮触及的表现细节，包括避雷针渲染、博物馆/调试展示行为、地图装饰贴附处理，以及相关 Jade/调试集成打磨。

#### 变更

- 项目公开版本号更新为 `0.4.6`。

## 0.4.5-fix1 - 2026-05-28

### Update Log (English)

#### Tree Growth and Wild Trees

- Reworked wild tree growth around Stardew-style internal stages and daily growth chances instead of the previous fixed 28-day timer, while keeping the existing two visible sapling blocks as the Minecraft presentation layer.
- Updated Tree Fertilizer so it marks eligible wild saplings as fertilized and boosts their daily growth chance instead of instantly advancing or maturing the tree, with proper feedback for already-fertilized, mature, or invalid targets.
- Improved blocked-tree handling so wild saplings keep their saved stage and can retry growth or maturation when the surrounding space becomes valid again.
- Moved wild-tree seed drops to the tree definition data, including chop, shake, and spread chances, and removed the extra leaf-decay seed drop path so scheduled leaf cleanup does not double-dip rewards.

#### Tooltips, Items, and Parity Fixes

- Expanded Jade wild tree support to show sapling stage, daily growth chance, fertilized growth chance, blocked status, mature tree state, and seed probabilities for mature wild trees.
- Made Maple Syrup, Mystic Syrup, and Sap edible/drinkable with Stardew-style energy and health effects, including Sap's negative energy behavior.
- Fixed Desert Trader parity by removing the non-source Crab Cakes Thursday trade; Thursday now matches the original Magic Rock Candy trade.
- Added English and Simplified Chinese text for the new tree fertilizer feedback and Jade tree tooltip lines.

### 更新日志（中文）

#### 树木生长与野树

- 重做野树生长逻辑：内部改为更接近星露谷的阶段与每日概率推进，不再使用固定 28 天计时；外观仍沿用现有两阶段树苗方块作为 Minecraft 表现层。
- 调整树肥行为：现在会给符合条件的野树苗打上已施肥状态，提高每日成长概率，而不是立即推进阶段或直接催熟；已施肥、已成熟和无效目标都会给出对应提示。
- 改进被阻挡树苗的处理：野树苗会保留已保存的阶段，等周围空间恢复有效后继续尝试成长或成熟。
- 将野树掉种概率改为树种数据驱动，覆盖砍树、摇树和自然扩散概率，并移除树叶定时清理时额外掉种的路径，避免奖励重复结算。

#### 信息显示、物品与对齐修复

- 扩展 Jade 野树信息：现在可显示树苗阶段、每日成长概率、施肥后成长概率、阻挡状态、成熟野树状态，以及成熟树的掉种/扩散概率。
- 枫糖浆、神秘糖浆和树液现在可食用/饮用，并接入星露谷式体力和生命效果，包括树液的负体力效果。
- 修正沙漠骆驼商人对齐问题：移除非源码的周四蟹黄糕兑换，周四兑换现在只保留原版魔法糖冰棍。
- 补充树肥反馈和 Jade 树木提示所需的英文与简体中文文本。

## 0.4.5 - 2026-05-28

### Update Log (English)

#### Headline Features

- Added the first playable Luau implementation for Summer 11, including beach festival entry, active festival session handling, time freeze behavior, festival music, Pierre's festival shop, and end-of-event return flow.
- Added the Luau potluck soup system with held-item contribution prompts, Stardew-style ingredient validation and scoring, multiplayer contribution tracking, Governor reaction branches, and final friendship/reaction feedback.
- Added the Luau main event cutscene flow with Lewis, Marnie, the Governor, festival crowd actors, camera movement, reaction-specific dialogue, music transitions, and event cleanup.

#### Festival Content and Presentation

- Added the Luau beach overlay, soup pot, torches, speaker, totem decor, Governor NPC assets, event music resources, block/item models, textures, blockstates, and localization.
- Added protected Luau map replacement behavior so festival decor is applied and restored without dropping replaced blocks or allowing the unique soup pot to be broken or picked up.
- Added Luau NPC participation, dialogue routing, festival shop registration, Governor display name support, and cutscene actor placement polish.

#### Stability and Shared Festival Fixes

- Refactored active festival confirmations into shared state used by Egg Festival, Flower Dance, and Luau so entry, exit, and start votes behave consistently across active festivals.
- Fixed active festival entry timing and same-day terminal states so players are not offered setup prompts after the venue is actually open or already finished.
- Added festival-day no-rain behavior based on Stardew Valley source behavior and hardened Luau soup interactions so adding food to the pot no longer also starts eating the held item.

#### Changes

- Updated the public mod version to `0.4.5`.

### 更新日志（中文）

#### 重点内容

- 加入第一轮可玩的夏威夷宴会实现，覆盖夏 11 海滩入场、主动节日会话、时间冻结、节日音乐、皮埃尔节日商店，以及节日结束后的回家流程。
- 加入夏威夷宴会百乐餐大锅系统，包括手持物投汤确认、星露谷风格食材判定与评分、多人投料记录、州长反应分支，以及最终好感/结果反馈。
- 加入夏威夷宴会主事件 cutscene 流程，包括刘易斯、玛妮、州长、节日人群演员、镜头移动、分支对白、音乐切换和事件清理。

#### 节日内容与表现

- 加入夏威夷宴会海滩 overlay、大锅、火炬、音响、图腾装饰、州长 NPC 资源、事件音乐、方块/物品模型、贴图、方块状态和本地化。
- 加入受保护的 Luau 地图替换流程，让节日装饰应用和恢复时不会掉落被替换方块，并保证全服唯一的大锅不可破坏、不可拾取。
- 加入 Luau NPC 参与、对白路由、节日商店注册、州长显示名，以及 cutscene 演员站位打磨。

#### 稳定性与通用节日修复

- 将主动节日确认状态抽成通用逻辑，复活节、花舞节和夏威夷宴会共用入场、离场和开始投票状态，减少各节日行为漂移。
- 修复主动节日入场时间和当日终止状态处理，避免会场已经开放或结束时仍弹出错误的搭建中提示。
- 按星露谷源码行为加入节日当天无雨处理，并加固 Luau 投汤交互，避免向汤里投食物时同时触发吃掉手持物。

#### 变更

- 项目公开版本号更新为 `0.4.5`。

## 0.4.4 - 2026-05-28

### Update Log (English)

#### Headline Features

- Added the first playable Trout Derby implementation for the Summer 20-21 passive festival window, including Forest overlay support, Willy schedule/interaction handling, Golden Tag content, Rainbow Trout catch rolls, treasure integration, and booth reward exchange.
- Added Trout Derby presentation support with the project-owned Forest schematic overlay, synchronized item display entities, debug-safe apply/restore behavior, and no-drop bulk block replacement during overlay swaps.
- Added the Lucky Purple Shorts feature pass, including the quest item, special presentation hooks, placeable shorts block, Lewis/Marnie interaction paths, basement handling, and the purple-shorts fishing bobber renderer path.

#### Farming, UI, and Integration

- Added rice shoot crop content and related crop tags, models, textures, and item registration.
- Improved fertilizer client synchronization and Jade integration so crop/farmland fertilizer state can be inspected more consistently.
- Updated NPC model and animation resources touched by the current presentation pass.

#### Festival and Stability Fixes

- Hardened passive festival overlay lifecycle callbacks so passive handlers can respond when overlays start applying, finish applying, start restoring, and finish restoring.
- Fixed Trout Derby Golden Tag eligibility so debug/forced passive festival sessions use the passive festival open state instead of being blocked by the in-game calendar date alone.
- Added missing Jade config localization keys and fixed several festival/debug interaction paths touched by Flower Dance, Desert Festival, and Trout Derby work.

#### Changes

- Updated the public mod version to `0.4.4`.

### 更新日志（中文）

#### 重点内容

- 加入第一轮可玩的鳟鱼大赛实现，覆盖夏 20-21 被动节日窗口、森林 overlay、威利日程与交互、黄金标签物品、虹鳟鱼捕获掉落、宝箱整合，以及摊位兑换奖励。
- 加入鳟鱼大赛表现层支持，包括项目内正式保存的森林 schematic overlay、三个展示实体同步安装/清理、调试 apply/restore 兼容，以及 overlay 批量替换时禁止掉落物品。
- 加入刘易斯紫色短裤功能批次，包括任务物品、特殊表现钩子、可放置短裤方块、刘易斯/玛妮相关交互、地窖处理，以及紫色短裤鱼漂渲染路径。

#### 农业、界面与集成

- 加入稻苗作物内容，以及对应作物标签、模型、贴图和物品注册。
- 改进肥料客户端同步与 Jade 集成，让作物/耕地肥料状态能更稳定地被查看。
- 更新本轮表现层工作触及的 NPC 模型和动画资源。

#### 节日与稳定性修复

- 加固被动节日 overlay 生命周期回调，让 passive handler 能响应 overlay 开始应用、应用完成、开始恢复和恢复完成。
- 修复鳟鱼大赛黄金标签判定：调试/强制开启的被动节日会使用节日 open 状态，不再被游戏内日期单独挡掉。
- 补齐 Jade 配置本地化键，并修复花舞节、沙漠节和鳟鱼大赛本轮触及的若干节日/调试交互路径。

#### 变更

- 项目公开版本号更新为 `0.4.4`。

## 0.4.3 - 2026-05-26

### Update Log (English)

#### Headline Features

- Added the first active Flower Dance implementation pass for Spring 24, including festival entry, time freeze behavior, HUD hiding, the Forest-FlowerFestival overlay, festival music, Pierre's event shop, and end-of-event return handling.
- Added Flower Dance NPC participation systems with confirmed venue positions, festival dialogue routing, dance partner invitations, friendship threshold checks, successful invitation friendship rewards, player-player dance invitations, and occupied-partner rejection handling.
- Added the first Flower Dance main dance cutscene flow, including Lewis start confirmation, selected dance partners, client-side dancer/audience actors, camera setup, hidden real-NPC suppression, and festival-specific cutscene assets.

#### Festival Architecture

- Added a shared active festival handler layer so active festivals can centralize entry, main-event state, NPC interaction locks, Pierre festival shops, and future free-stage exit behavior.
- Added festival NPC control hooks so central NPC movement yields to active festival actors instead of overwriting festival positions and routes.
- Expanded festival network payload coverage for Flower Dance NPC invites, player invites, player ask prompts, and cutscene synchronization.

#### Content, Polish, and Planning

- Added Flower Dance decor blocks, block entities, models, textures, structure data, music resources, and the main cutscene event definition.
- Improved Egg Festival runtime support touched by the shared active-festival flow, including festival lifecycle handling and interaction consistency.
- Added and updated planning/source-ledger documents for Flower Dance, active festival architecture, festival requirements, story/event migration, mastery, prize ticket, and related implementation tracks.

#### Changes

- Updated the public mod version to `0.4.3`.

### 更新日志（中文）

#### 重点内容

- 加入第一轮主动花舞节实现，覆盖春 24 入场、时间冻结、HUD 隐藏、`Forest-FlowerFestival` 会场覆盖、节日音乐、皮埃尔节日商店，以及节日结束后的回家流程。
- 加入花舞节 NPC 参与系统，包括已确认会场站位、节日对白路由、NPC 舞伴邀请、好感阈值判断、邀请成功后的好感奖励、玩家互邀，以及舞伴已被占用时的拒绝处理。
- 加入花舞节主舞 cutscene 首版流程，包括刘易斯开始确认、已选择舞伴入场、客户端舞者/观众演员、镜头设置、真实 NPC 隐藏与阴影抑制，以及花舞节专用 cutscene 资源。

#### 节日架构

- 新增主动节日通用 handler 层，用于集中处理节日入场、主事件状态、NPC 交互锁、皮埃尔节日商店，以及后续自由阶段离场逻辑。
- 新增节日 NPC 控制钩子，让中央 NPC 移动系统在主动节日期间让位给节日演员，避免覆盖节日站位和路线。
- 扩展花舞节网络包，支持 NPC 邀舞、玩家互邀、玩家邀请确认和 cutscene 状态同步。

#### 内容、打磨与规划

- 新增花舞节装饰方块、方块实体、模型、贴图、结构数据、音乐资源和主舞 cutscene 事件定义。
- 改进被主动节日流程触及的复活节运行逻辑，包括节日生命周期和交互一致性。
- 新增并更新花舞节、主动节日架构、节日需求、剧情/事件迁移、精通、兑奖券等实现规划和源码对照文档。

#### 变更

- 项目公开版本号更新为 `0.4.3`。

## 0.4.2 - 2026-05-25

### Update Log (English)

#### Headline Features

- Added the first broad Desert Festival implementation pass for the Spring 15-17 festival window, including passive festival activation, desert map makeover behavior, Calico Egg economy support, NPC visit handling, and festival-specific shops/interactions.
- Added Desert Festival race systems and Stardew-style race UI flow, including room lists, live race screens, snapshot/watch views, single-bet handling, state synchronization, and festival race network payloads.
- Added Desert Festival Skull Cavern support with Calico Egg stone content, mine HUD synchronization, Marlon challenge/rating menus, challenge progress persistence, egg reward handling, and festival-specific pass-out penalties.

#### Desert Festival Content

- Added Desert Festival vendor coverage, including the Calico Egg shop, rotating villager vendor shops, Shady Guy UI, festival food/dish registration, desert cook dish items, and related item models/localization.
- Added new festival items and blocks such as Calico Eggs, Calico Egg stones, Calico statues, star plaques, prize tickets, the prize ticket machine, and desert festival reward/utility models.
- Added festival-specific NPC and route data used by the desert venue, along with portal trigger/Jade support for inspecting location triggers during map interaction work.

#### Gameplay and Stability Fixes

- Reworked farm join approval so farm owners receive an in-game confirmation dialog instead of relying on permission-sensitive chat commands, with queued handling for multiple pending requests.
- Fixed flower crop growth/Jade behavior by separating decorative placed flowers from planted crops, so planted flowers can grow and show progress while decorative mature flowers stay static.
- Tuned bomb mining behavior for the 3D Minecraft environment, including smaller effective bomb ranges, reduced bomb-ladder odds from destroyed rocks, and a higher normal-bomb crafting cost.
- Improved mine ladder highlighting, mine exit/menu behavior, NPC leash protection, Joja Community Center lock messaging, lightning rod model presentation, Bookseller trade direction, and several multiplayer/client presentation paths touched during this pass.

#### Changes

- Updated the public mod version to `0.4.2`.

### 更新日志（中文）

#### 重点内容

- 加入第一轮较完整的沙漠节实现，覆盖春 15-17 的被动节日开启、沙漠地图改造、印花蛋经济、NPC 到访处理，以及节日期间专用商店与交互。
- 加入沙漠节赛跑系统和星露谷风格赛跑 UI 流程，包括房间列表、实时比赛界面、快照/观赛界面、单次下注、状态同步和对应网络包。
- 加入沙漠节骷髅洞支持，包括印花蛋石头、矿洞 HUD 同步、马龙挑战/评分菜单、挑战进度持久化、印花蛋奖励和节日期间昏倒惩罚。

#### 沙漠节内容

- 补充沙漠节商店覆盖：印花蛋商店、轮换村民摊位、神秘商人界面、节日料理/菜品注册、沙漠节料理物品，以及相关物品模型与本地化。
- 新增印花蛋、印花蛋石头、印花雕像、星星牌、兑奖券、兑奖机等节日物品/方块和奖励/功能模型。
- 加入沙漠会场所需的 NPC 与路线数据，并补充传送触发器/Jade 支持，方便地图交互调试和位置触发查看。

#### 玩法与稳定性修复

- 将加入农场审批改为农场主收到游戏内确认弹窗，不再依赖受权限组影响的聊天命令，并支持多个待处理申请排队显示。
- 修复花类作物生长和 Jade 信息：区分“装饰用成熟花”和“播种后的花苗”，让花苗能正常生长并显示进度，装饰花保持静态。
- 针对 Minecraft 3D 环境调整炸弹挖矿：缩小有效爆炸半径、降低炸掉石头出梯子的概率，并上调普通炸弹制作成本。
- 改进矿井梯子高亮、矿井离开/菜单行为、NPC 防拴绳、Joja 路线社区中心上锁提示、避雷针模型显示、书摊兑换方向，以及本轮触及的若干联机和客户端表现路径。

#### 变更

- 项目公开版本号更新为 `0.4.2`。

## 0.4.1 - 2026-05-23

### Update Log (English)

#### Headline Features

- Added the first full book-system integration pass, including Stardew book definitions, book items, reading flow, persistent read stats, repeat-read XP rules, and the project-specific Animal Catalogue behavior.
- Added the Bookseller NPC at the Stardew Valley town location with scheduled appearance days, buy/trade menus, no-portrait shop presentation, morning in-town notice, and fixed-position spawning.
- Added a Stardew-style reading presentation using Minecraft's enchanting-table book model as the 3D page-turning carrier, with imported `book_read` audio and rainbow star finish effects.

#### Book Effects and Sources

- Hooked the retained permanent book powers into gameplay, including movement, horse speed, bomb damage reduction, defense, trash-can odds, artifact value, Marlon recovery cost, wild seeds, woodcutting, diamond drops, crab pots, roe treasure, friendship gains, void monster drops, mystery-box odds, and grass slowdown behavior.
- Added shop and acquisition coverage for the currently available systems: Marnie Animal Catalogue, Dwarf Bombs book, Bookseller stock/trades, tree chopping, fishing treasure, monster drops, artifact spots, and Mystery Box book drops.
- Updated Bookseller stock handling so main shop entries are daily per-player limited while trade entries remain repeatable; mapped currently missing trade items to available project items where requested.

#### Polish, Fixes, and Planning

- Fixed book reading settlement so right-click reading now completes effects/consumption instead of only playing the visual, and applied the same timed reading path to the Dwarvish Translation Guide.
- Restored the default Stardew item tooltip price and stacked total-price lines independently of the removed Price Catalogue book.
- Fixed Bookseller shop portrait behavior and cutscene return-position handling touched during the book-system pass.
- Added detailed book-system planning and source-ledger documents, including TODO notes for deferred Golden Walnuts, gift boxes, Prize Ticket, Raccoon, SquidFest, VolcanoShop, DesertFestival, and formal Well Read advancement work.

#### Changes

- Updated the public mod version to `0.4.1`.

### 更新日志（中文）

#### 重点内容

- 加入第一轮完整书籍系统接入，包括星露谷书籍定义、书籍物品、阅读流程、已读统计持久化、重复阅读经验规则，以及本项目专属的动物目录书行为。
- 加入固定位置书摊老板 NPC，支持按季节日历出没、买书/换书菜单、无头像商店展示、早晨到访提示和固定点生成。
- 加入星露谷风格读书表现：使用 Minecraft 附魔台书模型作为 3D 翻页载体，并接入原版 `book_read` 音效和彩虹爆星收尾特效。

#### 书本效果与来源

- 将保留的永久书本能力接入实际玩法，包括移速、骑马速度、炸弹减伤、防御、垃圾桶概率、古物售价、马龙找回费用、野生种子、伐木、钻石掉落、蟹笼、鱼籽宝箱、友谊增长、虚空怪物掉落、神秘盒概率和草地减速行为。
- 补齐当前已有系统能承载的来源：玛妮动物目录、矮人炸弹书、书摊库存/兑换、砍树、钓鱼宝箱、怪物掉落、远古斑点和神秘盒书本掉落。
- 调整书摊库存逻辑：主商店条目按每日每玩家限购，兑换商店保持可重复兑换；按本项目现有物品对缺失兑换物做映射。

#### 打磨、修复与规划

- 修复书籍右键阅读只播放动画不结算的问题，现在会正确触发效果与消耗；矮人语手册也复用同一套定时阅读结算路径。
- 恢复项目默认的星露谷物品售价与堆叠总价 tooltip，不再受已移除的价格目录书影响。
- 修复书摊商店误显示头像的问题，并修正本轮书籍接入过程中触及的 cutscene 结束回原位逻辑。
- 新增书籍系统详细规划与源码锁定表，记录后续 TODO：Golden Walnuts 条件、一次性礼盒、兑奖券、浣熊、鱿鱼节、火山商店、沙漠节和正式 Well Read advancement。

#### 变更

- 项目公开版本号更新为 `0.4.1`。

## 0.4.0 - 2026-05-21

### Update Log (English)

#### Headline Features

- Added the first active-festival implementation, centered on the Spring 13 Egg Festival with map overlay activation, Pierre's festival shop, festival entry/exit handling, NPC venue takeover, main-event cutscenes, egg hunt scoring, winner resolution, and return-home flow.
- Integrated Egg Festival story flow with the existing cutscene/event runtime instead of using a separate festival-only timeline, including fade timing, actor staging, camera control, spectator handling, multiplayer actor placement, and dynamic winner dialogue.
- Added multiplayer-aware Egg Festival actor presentation so cutscene player actors can use sorted participant slots, real player UUIDs, player skins, slim/wide model selection, and copied equipment.

#### Festival, Time, and Map Behavior

- Added festival map overlay state handling for the Egg Festival venue and synchronized NPC runtime behavior with the overlay lifecycle.
- Added Egg Festival time-freeze behavior so the venue holds the festival sky/time at 9:00 while participants are inside, then advances to the festival end time when the event finishes.
- Fixed Egg Festival entry checks so the venue opens from the real 9:00 to 14:00 festival window and does not stay blocked by a stale same-day closed session during testing.
- Restored the original setup/start messaging path for Spring 13, including the setup warning before 9:00 and the festival-start broadcast at 9:00.

#### NPCs, Cutscenes, and Interaction Polish

- Added Egg Festival NPC actor control for free-stage placement, main-event lineup, hunt-stage behavior, award-stage staging, temporary non-contestant removal, and spawn suppression during the contest.
- Added Lewis start-contest confirmation so clicking Lewis prompts the original ready question before the main Egg Festival cutscene starts.
- Matched the original NPC fallback winner branch: when no player reaches the egg threshold, Abigail wins, Vincent reacts, Abigail walks up for the prize, and then returns to the lineup.
- Improved cutscene player/NPC visuals around black fades and award movement so real-stage transitions do not leak on screen and award winners face the expected direction.

#### UI, Audio, Rewards, and Localization

- Added Egg Festival HUD, actionbar timer/count display, scoreboard scoring, whistle/timer/coin/music feedback, and localized festival dialogue and system messages.
- Added temporary player-facing prize text noting that prize items are not hooked up in this version yet.
- Expanded English and Simplified Chinese localization for the new festival flow, including setup, entry, hunt, result, and award text.

#### Changes

- Updated the public mod version to `0.4.0`.

### 更新日志（中文）

#### 重点内容

- 加入第一版主动节日实现，核心为春 13 蛋蛋节：包含节日地图 overlay、皮埃尔节日商店、进入/离开处理、NPC 会场接管、主事件剧情、寻蛋计分、胜者判定和结束回家流程。
- 蛋蛋节剧情流程已接入现有 cutscene/event 运行时，不再使用独立节日时间轴；支持黑屏时机、演员站位、镜头控制、旁观者处理、多人演员排位和动态获胜对白。
- 多人 cutscene 玩家演员现在会按排序后的参赛槽位同步真实玩家 UUID，并使用对应玩家皮肤、粗/细手臂模型和装备外观。

#### 节日、时间与地图行为

- 加入蛋蛋节会场地图 overlay 状态处理，并让 NPC 运行时随 overlay 生命周期进入或恢复节日状态。
- 加入蛋蛋节时间冻结：玩家在会场内时，节日天空/时间固定在 9:00；节日结束后推进到原版结束时间。
- 修复蛋蛋节入口判断：会场现在按真实 9:00 到 14:00 时间窗开放，不会在同日测试时被旧的 closed session 一直挡在“布置中”。
- 补回春 13 原版风格的布置中提示与 9:00 节日开始广播。

#### NPC、剧情与交互打磨

- 加入蛋蛋节 NPC actor 控制，包括自由阶段站位、主事件队列、寻蛋阶段行为、领奖阶段站位、非参赛 NPC 临时移除和比赛期间生成抑制。
- 点击刘易斯开始比赛时现在会先弹出原版 ready 确认，不再一点击就直接进入主剧情。
- 对齐原版 NPC fallback 获胜分支：玩家未达到彩蛋阈值时由阿比盖尔获胜，文森特做朝向反应，阿比盖尔上前领奖后返回队列。
- 修正 cutscene 黑屏前后的真实站位切换和领奖走向，避免穿帮，并让领奖者朝向符合预期。

#### UI、音频、奖励与本地化

- 加入蛋蛋节 HUD、actionbar 计时/彩蛋数、scoreboard 计分、哨声/倒计时/金币/音乐反馈，以及节日对白和系统提示。
- 玩家获胜领奖对白暂时标注“本版本暂时未接入奖励”，避免误以为已经发放草帽或兑奖券。
- 扩展蛋蛋节相关英文和简体中文文本，包括布置、进入、寻蛋、结果和领奖内容。

#### 变更

- 项目公开版本号更新为 `0.4.0`。

## 0.3.10-fix1 - 2026-05-20

### Update Log (English)

#### Fixes

- Fixed Stardew Valley pregen upgrades by synchronizing the region manifest with the bundled region files and bumping the pregen version, allowing existing saves to reinstall the updated map data.
- Added Secret Woods access handling for existing saves, including per-player unlocked entrance visibility and collision behavior after clearing the hollow log.
- Reworked the locked Secret Woods boundary so blocked players are pushed back with an actionbar warning instead of being repeatedly teleported and camera-locked.
- Restored reliable Secret Woods resource-clump chopping by deferring custom clump removal until after the canceled vanilla break event finishes processing.
- Changed Secret Woods slime refreshing to run lazily when an unlocked player actually enters the loaded Secret Woods area, instead of trying to spawn entities during farm wake-up while the area may be unloaded.

#### Changes

- Updated the public mod version to `0.3.10-fix1`.

### 更新日志（中文）

#### 修复

- 修复星露谷预生成地图升级：同步 region manifest 与实际打包的 region 文件，并提升 pregen 版本，让老存档能重新安装更新后的地图数据。
- 补上秘密森林入口的老档兼容逻辑，包括砍开空心木桩后的玩家独立入口可见性与碰撞状态。
- 重做未解锁秘密森林时的边界阻挡：现在只会 actionbar 提示并把玩家推回入口外，不再反复传送导致视角/移动被锁住。
- 修复秘密森林资源簇砍伐不稳定的问题：自定义资源簇移除延后一 tick 执行，避免被取消的原版破坏事件同步复原。
- 调整秘密森林史莱姆刷新时机：改为已解锁玩家实际进入已加载的秘密森林区域时按天懒刷新，不再在农场睡醒结算时尝试生成远处实体。

#### 变更

- 项目公开版本号更新为 `0.3.10-fix1`。

## 0.3.10 - 2026-05-19

### Update Log (English)

#### Headline Features

- Added the first mastery-system pass, including mastery data, rewards, menu entry points, mastery blocks, mastery statues, and supporting sync payloads.
- Added late-game forge and equipment foundations, including the mini forge, anvil/heavy furnace block entities, weapon forge data, combined rings, trinket items, and enchantment guard logic.
- Added new companion/trinket presentation work for prismatic butterflies, fairy companions, frog/parrot/fairy-style trinkets, Galaxy Soul handling, and related client effects.

#### Combat, Tools, and Player Progression

- Expanded weapon stats, tooltips, cooldown handling, combat events, equipment sync, ring effects, and Curios integration paths.
- Improved hoe, watering can, scythe, axe, pickaxe, pan, fishing rod, mining, forage, artifact spot, and skull-cavern reward behavior touched by the progression update.
- Added or refined player-data fields and sync coverage for mastery, equipment, trinkets, forging, progression hints, and related UI state.

#### Furniture, Models, and Interaction Polish

- Rebuilt oak, spruce, and birch table models around the lower 14/16-block tabletop height, with full per-connection top-edge textures instead of rotated texture reuse.
- Adjusted table display-item placement, table collision/selection shapes, tablecloth height, and table leg/top model composition to match the new table geometry.
- Replaced oak, spruce, and birch chair models and textures, and raised their sitting height to 9/16-block so seating aligns with the updated models.
- Added or updated furniture, mastery, forge, statue, forage grape, and item model resources used by the new release content.

#### UI, Menus, Audio, and Assets

- Expanded common GUI texture helpers and continued Stardew-style menu scaling/asset normalization across gameplay screens and tooltip components.
- Added mini-forge and mastery-related client screens, renderers, item models, GUI resources, and localized text.
- Added new sound assets and sound registrations for forge, mastery, combat, tool, and utility feedback.

#### Fixes and Behavior Cleanup

- Improved cutscene locking/tracking, NPC spawn/runtime handling, mail behavior, mine drops/spawns, farm/interior protection, pass-out flow, and time/event bookkeeping touched by the release pass.
- Refined crop, bush, flower placement, sunflower, forage, berry, hot-spring visual, desert artifact spot, and utility block behaviors.
- Updated the public mod version to `0.3.10`.

### 更新日志（中文）

#### 重点内容

- 加入第一轮精通系统，包括精通数据、奖励、菜单入口、精通方块、精通雕像以及对应的同步网络包。
- 加入后期锻造与装备系统基础，包括迷你锻造台、铁砧/重型熔炉方块实体、武器锻造数据、合成戒指、饰品物品和附魔保护逻辑。
- 补充棱彩蝴蝶、仙灵伙伴、青蛙/鹦鹉/仙灵类饰品、银河之魂以及相关客户端特效的表现基础。

#### 战斗、工具与玩家成长

- 扩展武器属性、tooltip、冷却、战斗事件、装备同步、戒指效果和 Curios 兼容路径。
- 改进锄头、喷壶、镰刀、斧头、镐子、淘盘、钓竿、采矿、采集物、蚯蚓点和骷髅洞奖励等与成长线相关的行为。
- 补充玩家数据字段与同步范围，用于精通、装备、饰品、锻造、进度提示和相关 UI 状态。

#### 家具、模型与交互打磨

- 重做橡木、杉木、桦木桌模型，使桌面高度统一为 14/16 格，并为所有连接形态接入独立顶面边缘贴图，不再旋转复用材质。
- 调整桌上物品显示高度、桌子碰撞/选择形状、桌布高度以及桌腿/桌面模型组合，使其匹配新的桌子几何。
- 替换橡木椅、杉木椅、桦木椅模型和贴图，并将坐下高度提高到 9/16 格，让坐姿贴合新模型。
- 新增或更新本次内容需要的家具、精通、锻造、雕像、野葡萄和物品模型资源。

#### UI、菜单、音频与资源

- 扩展通用 GUI 贴图 helper，并继续推进星露谷风格菜单缩放和资源规范化。
- 加入迷你锻造、精通相关客户端界面、渲染器、物品模型、GUI 资源和本地化文本。
- 新增锻造、精通、战斗、工具和通用反馈所需的声音资源与声音注册。

#### 修复与行为清理

- 改进剧情锁定/追踪、NPC 生成与运行时、邮件、矿井掉落/生成、农场与室内保护、昏倒流程和时间/事件记录等路径。
- 调整作物、灌木、花卉放置、向日葵、采集物、浆果、温泉视觉、沙漠蚯蚓点和工具方块行为。
- 项目公开版本号更新为 `0.3.10`。

## 0.3.9fix2 - 2026-05-17

### Update Log (English)

#### Fixes

- Tightened Stardew Valley social-page parity for NPCs, including Krobus visibility, unknown-name display, gift eligibility, and Introductions quest filtering.
- Added the original Krobus mugshot crop to the social UI and wired the same mugshot into the Xaero Minimap NPC icon set.
- Corrected Marnie's animal-shop counter route point and made NPCs finish walking naturally to route-point centers instead of relying on a final snap.
- Prevented ore-pan sparkle points and fish splash bubbles from spawning in hot spring, farm, and sewer areas, and cleaned up existing invalid water-feature points when encountered.
- Cleared remaining workspace diagnostics by aligning Bush block-entity non-null annotations, removing unused spawn helper overloads, and normalizing a renderer whitespace issue.

#### Changes

- Updated the public mod version to `0.3.9fix2`.

### 更新日志（中文）

#### 修复

- 进一步对齐星露谷原版社交界面逻辑，包括 Krobus 显示、未认识时的 `???`、送礼资格和打招呼任务过滤。
- 添加从原版裁出的 Krobus mugshot，并将同一份头像接入 Xaero Minimap 的 NPC 图标适配。
- 修正 Marnie 动物商店柜台路线点，并让 NPC 在路线终点自然走到方块中心，不再依赖最终吸附。
- 限制淘金闪光点和钓鱼气泡的生成区域，温泉、农场和下水道区域不再生成，并会清理已存在的非法水面点。
- 清理剩余工作区诊断：补齐 Bush 方块实体非空标注、删除未使用的生成 helper 重载，并规范一个渲染器文件的空白问题。

#### 变更

- 项目公开版本号更新为 `0.3.9fix2`。

## 0.3.9fix1 - 2026-05-17

### Update Log (English)

#### Fixes

- Raised the overworld wizard tower structure template by one block so newly generated towers no longer sink one block into the ground.
- Stopped Joja Mart NPC maintenance from running while no players are in the Stardew Valley dimension, preventing repeated Morris and cashier fresh-spawn loops while players are in the Overworld.
- Stopped Joja's maintenance tick from redundantly forcing camel merchant and traveling cart checks; those static merchants now also skip their own timed checks when Stardew Valley has no players.
- Disabled NPC movement debug snapshot/log work by default behind the `stardewcraft.npcMovementDebug` system property, removing hot-path debug overhead during normal play.

#### Changes

- Bumped `StardewValleyPrebuiltRegionInstaller.CURRENT_PREGEN_VERSION` to `5` so existing saves reinstall the updated pregen layout.
- Updated the public mod version to `0.3.9fix1`.
- Reduced NPC runtime bookkeeping by caching implemented movement entries and refreshing runtime/pathing metadata only when NPC capability data changes.
- Improved NPC path evaluation so NPCs avoid tall or cross-cell decor collisions such as Joja Mart shelves instead of treating those blocks as walkable space.

### 更新日志（中文）

#### 修复

- 将主世界法师塔结构模板整体抬高一格，新生成的法师塔不再陷进地里一格。
- Joja 超市 NPC 巡检现在只会在星露谷维度有玩家时运行，避免玩家在主世界时 Morris 和收银员反复 fresh-spawn 并拖慢服务器。
- 移除 Joja tick 对骆驼商人和旅行货车的重复强制巡检；这两个静态商人自己的定时巡检也会在星露谷无人时跳过。
- NPC 移动调试快照和日志默认关闭，仅在设置 `stardewcraft.npcMovementDebug` 时启用，减少正常游玩时的热路径开销。

#### 变更

- 将 `StardewValleyPrebuiltRegionInstaller.CURRENT_PREGEN_VERSION` 提升到 `5`，让旧存档重新安装更新后的 pregen 地图布局。
- 项目公开版本号更新为 `0.3.9fix1`。
- 缓存 NPC movement entry，并让 runtime/pathing 元数据只在 NPC capability 数据变化时刷新，减少每 tick 重复 bookkeeping。
- 改进 NPC 寻路节点判定，让 NPC 避开超市货架这类高碰撞或跨格装饰方块，不再把它们当成可走空间。

## 0.3.9 - 2026-05-17

### Update Log (English)

#### Headline Features

- Added the Stardew Valley 1.6-style Powers wallet page to the V-menu, following the active `PowersTab` / `Powers.json` layout model instead of the older unused skills-page wallet path.
- Added standalone wallet/power icon assets for Forest Magic, Dwarvish Translation Guide, Rusty Key, Club Card, Special Charm, Skull Key, Magnifying Glass, Dark Talisman, Magic Ink, Bear Paw, Spring Onion Mastery, and Key to the Town.
- Added client sync for `SpecialItems`, so wallet-style permanent unlocks can be displayed from authoritative player data instead of being inferred only from mail flags.
- Promoted the Skull Key and Dwarvish Translation Guide toward true Stardew-style special items: permanent, not sellable, not consumed, synced to player special-item data, and visible in the powers page.
- Added full item-tooltip rendering for item-backed powers in the V-menu, so special items show their real Minecraft/StardewCraft tooltip instead of a simplified hand-written hover label.

#### Wallet, Powers, and Special Items

- Added a new powers tab to the V-menu with the same nine-column icon rhythm used by Stardew Valley's power display.
- Added locked-power rendering using dark translucent silhouettes and `???` hover text.
- Added unlock checks that can use either a Stardew mail flag or a synced special-item id, preserving old-save compatibility while supporting the new special-item path.
- Added Skull Key special-item persistence when the key enters a player's inventory, including `HasSkullKey`, `stardewcraft:skull_key`, save, sync, and obtained feedback.
- Added Dwarvish Translation Guide special-item persistence using `HasDwarvishTranslationGuide` plus `stardewcraft:dwarvish_translation_guide`.
- Changed Dwarvish Translation Guide use behavior from instant consume to a short right-click reading action.
- Added local reading feedback for the Dwarvish Translation Guide: an immediate page-turn sound, additional page-turn sounds during use, and a visible sustained-use animation.
- Kept Dwarvish Translation Guide completion feedback private to the user instead of broadcasting the read/learn sound to nearby players.
- Added brown-themed tooltip border styling and special-item tooltip lines for the Dwarvish Translation Guide.
- Added special-item type presentation for the Dwarvish Translation Guide, matching the Skull Key / Rusty Key style of permanent reward items.
- Updated Dwarf language access so either the old mail flag or the new special-item unlock lets the player understand Dwarves.

#### UI and Menu Work

- Continued the large UI scale normalization pass, reducing direct large-atlas UV dependence in favor of standalone GUI textures and shared draw helpers.
- Expanded common GUI texture helpers for powers icons and tintable power rendering.
- Improved V-menu tooltip routing so inventory-backed UI entries can display native item tooltips with all custom injected Stardew lines intact.
- Improved inventory, crafting, leaderboard, shop, farm, building, chest, catalogue, quest, overnight, and other Stardew-style screens touched by the scaling pass.
- Improved GUI consistency for item rendering, menu boxes, tab interactions, hover text, button texture slices, and Stardew-style pixel scaling across GUI scales.
- Added or refined font and UI resources needed by the newer menu and tooltip presentation.

#### Leaderboards and Player Data

- Extended the leaderboard foundation added in the previous line with more player-data integration, client cache handling, metrics, categories, and snapshot sync refinement.
- Added player-data fields and sync coverage needed by powers, special items, crafting interactions, ranking snapshots, and other client-side displays.
- Improved player login synchronization for several gameplay systems that need reliable first-open client state.
- Added more server-authored data paths so menus do not depend on stale client guesses after login, dimension changes, or world reloads.

#### World, Locations, and Movement

- Continued the pre-generated map coordinate migration work, including a written ledger for tracked coordinate, version, and installation changes.
- Continued cleanup around Stardew Valley prebuilt-region installation, interior allocation, desert layout handling, quarry access, farm-entry barriers, and cross-dimension teleport placement.
- Consolidated interior and public-area protection logic so subspace protection is less scattered and easier to reason about.
- Improved portal hints, interior transitions, NPC routing support, and location-graph behavior touched by the coordinate migration pass.
- Improved biome patching, forage placement, artifact spots, coal forest clumps, quarry spawning, and map bootstrap behavior around newer world-layout assumptions.

#### NPCs, Shops, Quests, and Events

- Improved NPC movement and route-planning internals, including central movement service behavior, spawn management, path navigation, schedule runtime, and location graph usage.
- Improved NPC friendship overview sync and friendship-related command/debug paths.
- Refined shop services and purchase handling across several vendors, including item availability and interaction flows touched by the wallet/special-item work.
- Refined Dwarf interaction behavior so the language gate follows both legacy and new unlock data.
- Updated museum reward handling around special item rewards, including the Dwarvish Translation Guide path.
- Improved event/cutscene payloads, debug commands, camera/player runtime details, and wake-up scheduling touched by this release pass.

#### Items, Tools, Fishing, Warp, and Economy

- Improved Stardew item tooltip injection for special items and related permanent rewards.
- Improved fishing rod item data handling and treasure screen behavior touched by recent item-data work.
- Improved warp wand behavior, unlock payloads, teleport payloads, destination handling, and related UI feedback.
- Improved cooking, crafting inventory actions, shipping-bin menu behavior, and shop purchase payload handling.
- Added compatibility and category-registration refinements for some vanilla/Stardew item interactions.

#### Audio, Rendering, and Assets

- Improved Stardew music manager behavior and sound registration touched by the current feature pass.
- Updated several block entity renderers to align with current render helper and resource assumptions.
- Improved Junimo text/model resources and related community-center UI presentation.
- Added standalone powers icon resources and recorded source extraction coordinates for future auditing.
- Updated many model/resource JSON files touched by the latest asset normalization pass.

#### Localization and Documentation

- Added English and Chinese text for the new powers page, wallet entries, special-item tooltips, Dwarvish Translation Guide feedback, and related UI labels.
- Added or updated documentation for leaderboard planning and pre-generated coordinate migration tracking.
- Updated the public project version to `0.3.9`.

### 更新日志（中文）

#### 重点内容

- 新增 V 键菜单里的星露谷 1.6 风格“能力 / 钱包”页面，按当前原版 `PowersTab` / `Powers.json` 的显示方式复刻，而不是继续沿用旧版未实际绘制的钱包入口判断。
- 新增独立钱包/能力图标素材，覆盖森林魔法、矮人语手册、生锈钥匙、会员卡、特殊魅力、骷髅钥匙、放大镜、黑暗护符、魔法墨水、熊掌、青葱技术和城镇钥匙。
- 新增客户端 `SpecialItems` 同步，让永久特殊物品解锁可以从服务端玩家数据直接显示，不再只能依赖 mail flag 猜测。
- 将骷髅钥匙和矮人语手册推进到真正的星露谷特殊物品规格：永久保留、不可出售、不会被使用消耗、写入玩家特殊物品数据，并在能力页显示。
- 能力页里的物品型能力现在直接显示对应物品自己的完整 tooltip，不再只显示手写标题和描述。

#### 钱包、能力页与特殊物品

- 新增 V 键菜单能力页，使用接近原版星露谷的九列图标排布与间距。
- 新增未解锁能力的黑色半透明剪影显示和 `???` 悬浮提示。
- 新增能力解锁判定：同一条能力可以同时兼容旧 mail flag 与新 special item id，兼顾老存档和新数据结构。
- 骷髅钥匙进入玩家背包时会写入 `HasSkullKey` 和 `stardewcraft:skull_key`，并保存、同步和提示获得状态。
- 矮人语手册现在写入 `HasDwarvishTranslationGuide` 和 `stardewcraft:dwarvish_translation_guide`，成为永久特殊物品。
- 矮人语手册从“右键瞬间学习并消耗”改为“右键阅读一小段时间后学习”，使用后不消失。
- 矮人语手册新增本地阅读反馈：开始阅读立刻翻页，中途继续翻页，并使用更明显的持续使用动作。
- 矮人语手册完成阅读或重复阅读的声音只发给使用者本人，不会广播给附近玩家。
- 矮人语手册新增棕色主题 tooltip 边框、特殊物品类型显示和状态说明。
- 矮人语言理解判定现在同时认可旧 mail flag 与新特殊物品解锁，避免老存档失效。

#### UI 与菜单

- 继续推进大规模 UI 缩放规范化，将更多界面从直接采样大图集迁移到独立贴图和共享绘制 helper。
- 扩展通用 GUI 贴图 helper，支持能力图标和可染色能力图标绘制。
- 改进 V 键菜单 tooltip 分发，让背包物品型 UI 项可以显示原生物品 tooltip，并保留所有 StardewCraft 注入的自定义行。
- 继续修正背包、合成、排行榜、商店、农场、建筑、箱子、目录、任务、过夜结算等界面在 UI 缩放迁移中的细节。
- 改进物品绘制、菜单框、tab 交互、悬浮提示、按钮切片和星露谷像素缩放的一致性。
- 补充或调整新菜单与 tooltip 表现所需的字体和 UI 资源。

#### 排行榜与玩家数据

- 继续完善上一版加入的排行榜系统，补强客户端缓存、服务端快照、榜单指标、分类和同步路径。
- 扩展玩家数据字段与同步范围，支撑能力页、特殊物品、合成交互、排行榜快照和其他客户端展示。
- 改进玩家登录时的多系统同步，减少首次打开菜单时客户端状态过旧的问题。
- 将更多菜单展示改为服务端权威数据驱动，减少登录、切维度或重载世界后的客户端猜测。

#### 世界、地点与移动

- 继续推进预生成地图坐标迁移，并补充坐标、版本、安装状态的追踪文档。
- 继续清理星露谷预生成区域安装、室内分配、沙漠布局、采石场访问、农场入口屏障和跨维度传送落点。
- 合并并简化室内/公共区域保护逻辑，让 subspace 保护不再分散在多套事件里。
- 改进传送门提示、室内切换、NPC 路由支持和地点图行为，配合坐标迁移后的地图结构。
- 调整生物群系 patch、采集物、蚯蚓点、煤炭森林树桩、采石场生成和地图 bootstrap 等与新版地图布局相关的行为。

#### NPC、商店、任务与事件

- 改进 NPC 移动和路线规划底层，包括集中移动服务、生成管理、寻路、日程运行和地点图使用。
- 改进 NPC 好感度概览同步，以及好感度相关命令和调试路径。
- 调整多个商店服务和购买流程，覆盖商品可用性、交互流程和本轮特殊物品相关改动。
- 矮人交互逻辑现在会按旧 mail flag 或新特殊物品判断语言是否已解锁。
- 调整博物馆奖励路径，配合矮人语手册这类特殊物品奖励。
- 改进事件/剧情网络包、调试命令、镜头/玩家运行细节和早晨唤醒调度等本轮涉及路径。

#### 物品、工具、钓鱼、传送与经济

- 改进 Stardew 物品 tooltip 注入，特别是特殊物品与永久奖励物品的展示。
- 改进钓竿物品数据处理和宝箱界面相关行为。
- 改进传送法杖行为、解锁网络包、传送网络包、目的地处理和相关 UI 反馈。
- 改进烹饪、合成背包操作、出货箱菜单和商店购买网络包处理。
- 补充部分原版 / Stardew 物品交互兼容和分类注册细节。

#### 音频、渲染与资源

- 改进 Stardew 音乐管理器和本轮功能涉及的声音注册。
- 调整多个方块实体渲染器，使其更贴合当前渲染 helper 与资源路径假设。
- 改进祝尼魔文本、模型资源和社区中心相关 UI 表现。
- 新增独立能力页图标资源，并记录素材来源坐标，方便之后核对。
- 更新大量本轮资源规范化涉及的模型和资源 JSON。

#### 本地化与文档

- 补充英文和中文的能力页、钱包条目、特殊物品 tooltip、矮人语手册反馈和相关 UI 文本。
- 新增或更新排行榜规划、预生成坐标迁移等文档。
- 项目公开版本号更新为 `0.3.9`。

## 0.3.8-fix4 - 2026-05-14

### Update Log (English)

#### Fixes

- Fixed Stardew-style UI texture sampling and UV drift across non-4x Minecraft GUI scales by moving many atlas-dependent widgets to standalone PNG slices.
- Fixed V-menu layout regressions from the UI scaling pass, preserving the 4x9 Minecraft inventory grid and the original top tab placement.
- Fixed the V-menu top-left frame artifact by correcting the sliced menu tile resource instead of hiding it with layout offsets.
- Fixed the leaderboard page header and row styling, removing the metric icon from the title and making top-three rows visibly distinct.
- Fixed the leaderboard side tabs to follow the workbench-style icon tab behavior, hover tooltips, and SHWIP click sound.
- Fixed shop UI panel shadow layering so the upper shop panel no longer casts a dark overlay across the inventory area.
- Fixed shop money box and money digit alignment under non-4x GUI scale.

#### Changes

- Added the V-menu leaderboard system with server-authored snapshots, pagination, client cache, request/sync payloads, and money, mining, fishing, shipping, combat, and life metrics.
- Added reusable Stardew UI texture helpers for standalone PNG rendering, scaled item drawing, common buttons, arrows, boxes, dialogue parts, social icons, skill icons, and game menu widgets.
- Migrated many Stardew-style screens and HUD elements away from direct large-atlas UV rendering, including shop, quest log, billboard, overnight, geode, elevator, catalogue, workbench, TV, and common dialogue UI pieces.
- Added the UI atlas slicing manifest/tooling and a written UI scaling normalization standard for future UI work.
- Added leaderboard persistence hooks for player names, mine block statistics, bombed mine blocks, shipped item totals, and total shipping value.

#### Localization

- Added English and Chinese leaderboard text, metric descriptions, value formats, V-menu tab label updates, and related configuration labels.

### 更新日志（中文）

#### 修复

- 修复大量星露谷风格 UI 在 Minecraft 非 4x GUI scale 下的贴图采样、UV 漂移和缩放异常，逐步改为独立 PNG 切片绘制。
- 修复 UI 缩放迁移过程中 V 键菜单布局被误改的问题，保留 Minecraft 4x9 背包网格和原本的顶部 tab 位置。
- 修复 V 键菜单左上角脏块，改为修正切片资源本身，而不是用界面偏移遮挡。
- 修复排行榜页标题和榜单行样式，移除标题里的指标图标，并让前三名高亮更清晰。
- 修复排行榜侧边 tab，使其按工作台图标 tab 的交互、悬浮提示和 SHWIP 点击音效表现。
- 修复商店上半部分面板阴影层级错误，避免黑色阴影盖到下方背包区域。
- 修复商店金币框和金币数字在非 4x GUI scale 下的位置失调。

#### 改动

- 新增 V 键菜单排行榜系统，包含服务端排行榜快照、分页、客户端缓存、请求/同步网络包，以及财富、采矿、钓鱼、出货、战斗和生活类榜单。
- 新增可复用的星露谷 UI 独立贴图 helper，覆盖缩放物品绘制、通用按钮、箭头、面板、对话框部件、社交图标、技能图标和游戏菜单控件。
- 将大量星露谷风格界面和 HUD 从直接采样大合图迁移到独立 PNG 绘制，包括商店、任务日志、公告板、过夜结算、晶球、电梯、家具目录、工作台、电视和通用对话 UI 部件。
- 新增 UI atlas 切片清单/脚本，以及后续 UI 缩放规范化的书面标准。
- 新增排行榜所需的玩家名称、矿井方块、爆破方块、出货数量和出货总价值统计接入。

#### 本地化

- 补充英文和中文排行榜文本、榜单说明、数值格式、V 键菜单 tab 名称和相关配置文本。

## 0.3.8-fix3 - 2026-05-13

### Update Log (English)

#### Fixes

- Fixed a Lewis cutscene crash that could happen when the client disconnected while an event was ending.
- Fixed cutscene movement freezing so vertical motion is preserved and players are less likely to trigger flight checks.
- Fixed targeted bait losing its target fish data when inserted into and removed from fishing rods.
- Fixed Stardew Valley weather forcing vanilla overworld rain, so vanilla weather commands work normally again outside the Stardew Valley dimension.
- Fixed auto-grabbers not collecting held animal products such as cow milk, goat milk, sheep milk, and wool.
- Fixed auto-feed troughs failing to detect their barn or coop when placed as valid adjacent interior fixtures.
- Fixed auto-feed trough hay consumption so it now pulls from the owning farm's shared silo storage instead of the interacting player's personal key.
- Fixed silo, pasture grass, and wheat hay storage ownership so hay is credited to the farm where the action happens.
- Fixed Stardew bed interactions so players enter the sleeping pose before confirming sleep, can cancel back out of bed, and no longer get placed at incorrect offsets on custom bed models.
- Fixed Stardew multiplayer sleep voting so only players who remain in bed count toward the vote, while waiting sleepers continue recovering stamina each second.
- Fixed charged hoe range previews disappearing when aiming at protected Stardew yellow dirt.
- Fixed multiplayer silo managers staying visually unbuilt after construction when the silo belonged to a shared farm owner instead of the interacting member.
- Fixed hay hoppers and silo readouts resolving hay storage through the wrong player in shared farms, which could show 0/0 despite an existing silo.
- Fixed crab pots being blocked by public-area protection in Stardew Valley waterways.
- Fixed crab pot catches using one combined pool instead of separating ocean and freshwater catch pools.
- Fixed crab pot ownership so only the player who placed a crab pot can bait, collect, or remove it.
- Disabled external item automation for crab pots so pipes cannot bypass crab pot ownership.
- Fixed farm join accept/reject commands being hidden behind operator-only command registration in multiplayer.
- Increased glow radius for Small Glow Ring, Glow Ring, Iridium Band, and Glowstone Ring by about 50%.
- Fixed NPC-bound friendship doors rendering opaque instead of using the oak door cutout layer.
- Fixed seasonal leaf tinting registration for vanilla and Stardew leaves without applying the effect to cherry leaves.
- Fixed beverage items using the eating animation instead of Minecraft's drinking animation, including artisan drinks, milk, Joja Cola, clinic tonics, Ginger Ale, and Triple Shot Espresso.

#### Changes

- Auto-feed troughs now continuously perform low-frequency refill checks while the chunk is loaded, pulling silo hay into empty connected trough networks as needed.
- Shared-farm hay storage now aggregates legacy member-owned hay while using the farm owner as the canonical storage key for new hay.
- Stardew Valley weather sync now uses the custom Stardew weather state instead of mutating vanilla level weather.
- Custom Stardew beds now resolve their sleep anchor to the correct head block and use vanilla sleeping orientation/rendering behavior instead of applying custom entity position offsets.
- Added built-in Xaero's Minimap icon resources for StardewCraft NPCs, animals, Junimos, crows, and traveling merchants.
- Added NPC-bound friendship doors that use oak door visuals, let Stardew NPCs pass through, and block players until they meet the configured friendship requirement.

#### Localization

- Added or corrected small English and Chinese localization entries touched by the fix pass.

### 更新日志（中文）

#### 修复

- 修复刘易斯剧情在客户端断开连接、剧情结束回包时可能崩溃的问题。
- 修复剧情冻结玩家移动时清空竖直速度导致更容易触发飞行检测的问题。
- 修复针对性鱼饵装入钓竿再取出后丢失目标鱼数据、变回普通鱼饵的问题。
- 修复星露谷天气强行锁定主世界原版下雨，导致 `/weather` 指令无法正常关雨的问题。
- 修复自动采集器无法采集牛奶、羊奶、绵羊奶和羊毛等动物持有产物的问题。
- 修复自动喂食槽在合法贴着室内空气格摆放时识别不到所属鸡舍或畜棚，导致完全不会自动补草的问题。
- 修复自动喂食槽扣草时没有从所在农场的共享筒仓干草池扣除的问题。
- 修复筒仓、牧草和小麦产出干草时归属不稳定的问题，现在会优先按所在农场记入干草。
- 修复星露谷床交互流程，现在玩家会先进入躺床状态再确认是否睡觉，取消时会正常起床，并修复自定义床模型上的错误躺床偏移。
- 修复多人睡觉投票流程，现在只有仍然躺在床上的玩家会计入投票，等待投票期间仍会每秒恢复体力。
- 修复锄头蓄力范围预览在对准受保护的星露谷黄土时不显示的问题。
- 修复多人服务器中共享农场成员建造筒仓后，服务端提示已建成但管理界面仍显示未成型的问题。
- 修复共享农场里筒仓界面和喂料斗按错误玩家读取干草存储，导致已有筒仓仍显示 0/0 的问题。
- 修复星露谷公共水域因公共区域保护而无法放置蟹笼的问题。
- 修复蟹笼捕获物没有区分海水/淡水池子、所有产物混在一起随机的问题。
- 修复蟹笼所有权，现在只有放置者可以塞鱼饵、收取产物或拆除蟹笼。
- 禁用蟹笼的外部物品自动化接口，避免管道绕过蟹笼主人限制。
- 修复多人模式中 `/stardew farm accept` 和 `/stardew farm reject` 被管理员权限命令树误拦截的问题。
- 将小型光辉戒指、光辉戒指、铱环和光辉石戒指的发光半径提高约 50%。
- 修复绑定 NPC 的好感门没有使用橡木门 cutout 渲染层，导致透明区域显示为不透明的问题。
- 修正原版树叶和星露谷树叶的季节染色注册，并避免樱花树叶被季节染色影响。
- 修复饮品物品使用时播放吃东西动画的问题，现在酒、果汁、咖啡、牛奶、Joja 可乐、诊所药水、姜汁汽水和三倍浓缩咖啡会使用 Minecraft 的饮用动画。

#### 改动

- 自动喂食槽现在会在区块加载期间持续进行低频补草检查，按需从筒仓向空的连接喂食槽网络补充干草。
- 共享农场干草存储现在以农场主人作为新干草的统一归属，同时兼容读取和扣除旧版本成员名下的干草。
- 星露谷天气同步现在只使用自定义星露谷天气状态，不再改写原版维度天气。
- 自定义星露谷床现在会把睡眠锚点解析到正确的床头格，并使用原版睡眠朝向与渲染逻辑，不再手动给实体叠加位置偏移。
- 内置 Xaero 小地图图标资源，覆盖星野牧歌 NPC、动物、祝尼魔、乌鸦和旅行商人等实体。
- 新增可绑定 NPC 的好感门，外观复用橡木门，星露谷 NPC 可直接穿过，玩家需要达到配置的好感度后才能开门通行。

#### 本地化

- 补充或修正了本轮修复涉及的少量英文与中文文本。

## 0.3.8fix - 2026-05-08

基线版本：release: 0.3.8-alpha

### 本次我们做了什么

- 修复 NPC 对话期间会提前恢复行走的问题；对话现在会完整锁定 NPC 移动，关闭对话或玩家下线后会正确解锁。
- 优化 NPC 长距离移动的中间寻路点计算，减少室内外切换、高低差路径里的异常卡住与误判瞬移。
- 扩展星露谷物品到原版物品的一次性转换配方，补齐苹果、骨片、钻石、鸡蛋、绿宝石、羽毛、蜂蜜、墨汁、牛奶、鹦鹉螺壳、兔子脚、史莱姆球、羊毛等兼容入口。
- 新增基于 c 标签和本地兼容标签的原版配方适配，让更多原版合成与部分跨模组配方直接接受星露谷等价物品。
- 补上兔子脚相关酿造兼容，使星露谷兔子脚也能进入原版酿造链。
- 改进共享农场联机加入流程，客户端会同步“待审批加入”状态；已有加入申请时再创建农场会先确认，加入成功后的落地与起步资源发放也更顺。
- 放宽共享农场成员的日常系统权限，让作物、牧草、乌鸦、农场洞穴和每日结算更按成员所属农场生效，而不是只认房主。
- 增强离线玩家跨天补偿与晨间事件调度，避免多人环境里邮件、早晨事件和社区中心过场被静默跳过。
- 调整矿井与骷髅矿生态，重生成时会清理残留怪物，并重新收紧高层矿石与特殊房间节奏，减少一层就出现过量高价值矿的情况。
- 加入怪物召唤调试命令，并补充木乃伊坍塌的服务端同步、客户端渲染与爆炸处决链路，让木乃伊表现更接近原版星露谷。
- 修正鱼塘水体标签与鱼塘拉鱼交互，优化商店连续购买停止条件、电视烹饪频道解锁校验、Joja 路线细节、跨维度进出和时间对齐等边角问题。
- 将炸弹范围进一步削弱一档，避免当前版本的爆炸覆盖面偏大。

### 主要改动分类

#### NPC 与剧情

- 对话锁移动、关闭回包与登出清理补齐。
- NPC 路径中间点高度解析修正。
- 多人环境下的晨间事件、邮件与社区中心过场调度更稳定。

#### 物品兼容与配方

- 新增 Stardew 物品到原版物品的单向转换配方。
- 新增 c 标签桥接与 vanilla_compat 标签层。
- 覆写大量原版配方以接受钻石、蛋、奶、蜂蜜、羽毛、兔子脚、史莱姆球等星露谷等价物。
- 新增兔子脚酿造兼容。

#### 联机与共享农场

- 农场加入申请待处理状态同步到客户端。
- 加入申请与创建农场的冲突流程增加确认保护。
- 共享农场成员权限、传送、每日结算与农场洞穴逻辑继续向“真正共享”收敛。

#### 矿井、怪物与战斗

- 矿层重生成会清理残留怪物。
- 骷髅矿高层矿石与房间分布再平衡。
- 木乃伊倒地渲染、同步和处决逻辑补齐。
- 新增怪物召唤调试命令。

#### 其他修复

- 鱼塘可作为水体使用，并支持等待上钩时直接从鱼塘拉鱼。
- 商店长按购买会在背包鼠标堆叠装不下或余额不足时自动停止。
- 电视菜谱解锁改为校验当天实际播出的配方。
- 星露谷维度时间对齐、跨维度落点、传送效果与若干本地化文本继续修正。
