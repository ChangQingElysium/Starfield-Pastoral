# StardewCraft 0.5.x Quest Ledger

This is the plain-language table for the quest-system stage.

Source baseline:

- Vanilla source: `Content/Data/Quests.json`, 66 rows.
- StardewCraft current data: `src/main/resources/data/stardewcraft/quests.json`, 14 rows.

Status labels:

- `有`: present and roughly the right kind of quest.
- `有但要核对`: present, but vanilla trigger/type/behavior still needs source checking.
- `有但类型不对`: present, but implemented as the wrong quest type.
- `缺`: not present in StardewCraft quest data.
- `暂缓`: not first quest-system batch because it depends on another system.

Priority labels:

- `先做`: belongs in the first quest-system implementation path.
- `先做底座`: implement the quest runtime/type first, then add rows.
- `下一批`: good candidate after the first batch.
- `等别的系统`: do not pull this into the first quest pass.

## 总表

| ID | 原版任务 | 类型 | 原版让玩家做什么 | 我们现在怎样 | 差什么 | 处理 |
| --- | --- | --- | --- | --- | --- | --- |
| `1` | Meet The Wizard | `Location` | 进入巫师塔。 | 有但类型不对：现在是 `Basic`。 | 要核对我们自己的巫师开局和原版 `WizardHouse` 进门任务怎么合并。 | 下一批，别先碰法师建筑。 |
| `2` | The Mysterious Qi | `Basic` | 把彩虹贝壳放到火车站箱子。 | 缺。 | 沙漠/赌场 Qi 任务线依赖。 | 等别的系统。 |
| `3` | The Mysterious Qi | `Basic` | 把 10 个甜菜放进刘易斯冰箱。 | 缺。 | 沙漠/赌场 Qi 任务线依赖。 | 等别的系统。 |
| `4` | The Mysterious Qi | `Basic` | 给沙龙最后一餐。 | 缺。 | 沙漠/赌场 Qi 任务线依赖。 | 等别的系统。 |
| `5` | The Mysterious Qi | `Basic` | 检查农舍旁边的木材堆。 | 缺。 | 沙漠/赌场 Qi 任务线依赖。 | 等别的系统。 |
| `6` | Getting Started | `ItemHarvest` | 种并收获一个防风草。 | 有。 | 核对触发、完成、后续 `7/8` 是否和原版一致。 | 先做。 |
| `7` | Raising Animals | `Building` | 建造鸡舍。 | 有，文案已改成鸡舍管理器教学版。 | 后续只需实测管理器建造完成是否稳定触发 `Coop`。 | 已处理，后续验证。 |
| `8` | Advancement | `Crafting` | 农业 1 级后制作稻草人。 | 有，条件使用 `scarecrow` 配方。 | 后续只需实测农业 1 级解锁和制作完成触发。 | 已处理，后续验证。 |
| `9` | Introductions | `Social` | 和村民打招呼。 | 有。 | 核对 NPC 范围和原版介绍名单。 | 先做。 |
| `10` | Copper Ore | `Basic` | 带铜矿去 Clint 铁匠铺。 | 原版数据存在，但源码主流程未发现正常 `addQuest("10")`；当前按 `copperFound` -> Clint 熔炉事件 -> `11` 走。 | 不作为第一批实现，避免误做成普通交付任务。 | 跳过，保留源码备注。 |
| `11` | Forging Ahead | `Crafting` | 制作熔炉。 | 有；由 Clint 熔炉事件添加，条件使用 `furnace` 配方。 | 后续实测事件后能否接到任务、制作后是否接 `12`。 | 已处理，后续验证。 |
| `12` | Smelting | `ItemHarvest` | 用熔炉炼一个铜锭。 | 有；机器收成已接入 item-received 任务事件。 | 后续实测铜锭进包后完成。 | 已处理，后续验证。 |
| `13` | To The Beach | `Basic` | 下午 5 点前去海滩。 | 有；`spring_2_1` 威利信件附带任务，海滩威利送竿事件完成后移除任务并写 `NOQUEST_13`。 | 后续实测春 2 邮件、海滩事件和跳过事件时状态命令是否都稳定。 | 已处理，后续验证。 |
| `14` | Explore The Mine | `Basic` | 到矿井 5 层。 | 有；Marlon 矿井开场事件添加任务，我们用 `Location`/`MineFloor:5` 作为 MC 等价。 | 原版到第 5 层会完成 `14` 并排次日 `guildQuest`，现在已接入；后续实测矿井流程。 | 已处理，后续验证。 |
| `15` | Initiation | `Monster` | 杀 10 只绿色史莱姆。 | 有；`guildQuest` 马龙信件附带任务，杀满后接 `16`。 | 怪物 id 当前用 `sd_mob_slime` 对应我方史莱姆；后续实测击杀计数。 | 已处理，后续验证。 |
| `16` | Initiation | `Location` | 进入冒险家公会。 | 有；完成 `15` 后接到任务，进入固定室内区域 `adventurer_guild` 时同时广播原版地点名 `AdventureGuild`。 | 源码未发现单独的 16 号入会 cutscene；目前按原版 Location 任务处理。 | 已处理，后续验证。 |
| `17` | Deeper In The Mine | `Basic` | 到矿井 40 层。 | 有；`14` 后续接 `17`，用 `Location`/`MineFloor:40` 作为 MC 等价。 | 后续实测 5 层完成后链到 17、40 层完成后链到 18。 | 已处理，后续验证。 |
| `18` | To The Bottom? | `Basic` | 到矿井底层。 | 有；用 `Location`/`MineFloor:120` 作为 MC 等价，120 层宝箱已有头骨钥匙。 | 后续实测 120 层任务完成和头骨钥匙入包触发。 | 已处理，后续验证。 |
| `19` | The Skull Key | `Basic` | 弄清骷髅钥匙用途。 | 有；头骨钥匙入包时接任务，首次使用沙漠矿洞入口时完成任务并安排 `skullCave` 次日来信。 | 后续实测旧存档已持钥匙玩家和新玩家两条路径。 | 已处理，后续验证。 |
| `20` | Qi's Challenge | `Basic` | 到骷髅洞穴 25 层。 | 机制有但内容不闭环；`skullCave` 信件附带任务，MC 中用 `Location`/`MineFloor:145` 表示头骨洞穴第 25 层，完成后次日寄 `QiChallengeComplete` 并给 10000 金。 | Qi 先生角色/身份呈现、后续 Qi 任务线/赌场/沙漠系统还没有作为系统完成；不能把这条叫完整完成。后续还要实测头骨洞穴层数和奖励信。 | 机制已接，系统依赖未完成。 |
| `21` | Marnie's Request | `Basic` | 把山洞萝卜带到 Marnie 店里。 | 缺；原版是 Marnie 上门事件 91 + AnimalShop 完成事件 92。 | 需要 wake-up/enter-area 剧情事件、`has_item` 前置、`remove_item` 指令、Marnie +100 好感；对白/站位需要共创。 | 下一批，剧情共创。 |
| `22` | Fish Casserole | `Basic` | 晚上 7 点带大嘴鲈鱼进 Jodi 家。 | 缺；原版是 Jodi 上门事件 93 + SamHouse 年 1/年 2 两版完成事件 94/95。 | 需要 wake-up/enter-area 剧情事件、时间/物品/年份前置、`remove_item` 指令；晚餐事件对白/站位需要共创。 | 下一批，剧情共创。 |
| `23` | Archaeology | `Location` | 去博物馆找 Gunther。 | 缺。 | 需要博物馆位置和 Gunther 流程。 | 下一批，接博物馆。 |
| `24` | Archaeology | `Basic` | 给博物馆捐一个文物/矿物。 | 缺。 | 依赖博物馆捐赠系统。 | 下一批，接博物馆。 |
| `25` | How To Win Friends | `Basic` | 送别人一份礼物。 | 缺。 | 需要送礼触发；NPC 系统已有部分基础。 | 先做。 |
| `26` | Rat Problem | `Basic` | 调查社区中心。 | 缺。 | 依赖社区中心入口/Junimo 纸条流程。 | 等社区中心系统。 |
| `27` | Goblin Problem | `Basic` | 进入女巫小屋。 | 缺。 | 法师/女巫/哥布林线依赖。 | 等法师系统。 |
| `28` | Dark Talisman | `Basic` | 去下水道问 Krobus 黑暗护身符。 | 缺。 | 下水道、Krobus、法师线依赖。 | 等法师系统。 |
| `29` | Strange Note | `Basic` | 把枫糖浆带去秘密森林。 | 缺。 | 秘密纸条触发。 | 等秘密纸条系统。 |
| `30` | Cryptic Note | `Basic` | 到骷髅洞穴 100 层。 | 缺。 | 秘密纸条 + 骷髅洞穴依赖。 | 等别的系统。 |
| `31` | A Winter Mystery | `Basic` | 找到神秘黑影。 | 缺。 | 放大镜/秘密纸条系统前置。 | 等秘密纸条系统。 |
| `100` | Robin's Lost Axe | `LostItem` | 找到 Robin 的斧头并还给她。 | 缺。 | 没有 `LostItemQuest` 真类型；还缺地点/拾取锚点。 | 先做底座。 |
| `101` | Jodi's Request | `ItemDelivery` | 给 Jodi 一个花椰菜。 | 缺。 | 需要物品和 NPC 可用性核对。 | 下一批。 |
| `102` | Mayor's Shorts | `LostItem` | 找到刘易斯的紫色短裤并归还。 | 有但类型不对：现在是 `ItemDelivery`。 | 应迁回 `LostItemQuest` 或明确保留 MC 改写理由。 | 先做底座。 |
| `103` | Pam Is Thirsty | `ItemDelivery` | 给 Pam 一杯淡啤酒。 | 缺。 | 酒类物品/NPC 可用性核对。 | 下一批。 |
| `104` | Crop Research | `ItemDelivery` | 给 Demetrius 一个甜瓜。 | 缺。 | 物品/NPC 可用性核对。 | 下一批。 |
| `105` | Knee Therapy | `ItemDelivery` | 给 George 一个辣椒。 | 缺。 | 物品/NPC 可用性核对。 | 下一批。 |
| `106` | Cow's Delight | `ItemDelivery` | 给 Marnie 一束苋菜。 | 缺。 | 物品/NPC 可用性核对。 | 下一批。 |
| `107` | Blackberry Basket | `LostItem` | 找到 Linus 的篮子并归还。 | 缺。 | 没有 `LostItemQuest` 真类型；还缺地点/拾取锚点。 | 先做底座。 |
| `108` | Carving Pumpkins | `ItemDelivery` | 给 Caroline 一个南瓜。 | 缺。 | 物品/NPC 可用性核对。 | 下一批。 |
| `109` | Catch A Squid | `ItemDelivery` | 给 Willy 一条鱿鱼。 | 缺。 | 鱼类/NPC 可用性核对。 | 下一批。 |
| `110` | Clint's Attempt | `ItemDelivery` | 给 Emily 一个紫水晶。 | 缺。 | 物品/NPC 可用性核对；奖励为 0 也要核对完成后移除。 | 下一批。 |
| `111` | A Dark Reagent | `ItemDelivery` | 给 Wizard 一个虚空精华。 | 缺。 | 法师 NPC 和物品可用性核对。 | 下一批，可能接法师。 |
| `112` | A Favor For Clint | `ItemDelivery` | 给 Clint 一个铁锭。 | 缺。 | 物品/NPC 可用性核对。 | 下一批。 |
| `113` | Robin's Request | `ItemDelivery` | 给 Robin 10 个硬木。 | 缺。 | 多数量交付要核对；ItemDelivery 已支持数量字段。 | 下一批。 |
| `114` | Fish Stew | `ItemDelivery` | 给 Gus 一条长鳍金枪鱼。 | 缺。 | 鱼类/NPC 可用性核对。 | 下一批。 |
| `115` | Fresh Fruit | `ItemDelivery` | 给 Emily 一个杏子。 | 缺。 | 果树/物品/NPC 可用性核对。 | 下一批。 |
| `116` | Granny's Gift | `ItemDelivery` | 给 Evelyn 一根韭葱。 | 缺。 | 物品/NPC 可用性核对。 | 下一批。 |
| `117` | Pierre's Notice | `ItemDelivery` | 给 Pierre 一份生鱼片。 | 缺。 | 烹饪物品/NPC 可用性核对。 | 下一批。 |
| `118` | Aquatic Research | `ItemDelivery` | 给 Demetrius 一条河豚。 | 缺。 | 鱼类/NPC 可用性核对。 | 下一批。 |
| `119` | A Soldier's Star | `ItemDelivery` | 给 Kent 一个杨桃。 | 缺。 | Kent 年 2 到来、作物可用性。 | 下一批或暂缓。 |
| `120` | Mayor's Need | `ItemDelivery` | 给 Lewis 一瓶松露油。 | 缺。 | 动物/加工品可用性。 | 下一批或暂缓。 |
| `121` | Wanted: Lobster | `ItemDelivery` | 给 Gus 一只龙虾。 | 缺。 | 蟹笼/鱼类可用性。 | 下一批或暂缓。 |
| `122` | Pam Needs Juice | `ItemDelivery` | 给 Pam 一个电池组。 | 缺。 | 电池/避雷针等可用性。 | 下一批或暂缓。 |
| `123` | Staff Of Power | `ItemDelivery` | 给 Wizard 一个铱锭。 | 缺。 | 铱锭和法师后期请求。 | 下一批或暂缓。 |
| `124` | Catch a Lingcod | `ItemDelivery` | 给 Willy 一条蛇齿单线鱼。 | 缺。 | 鱼类可用性。 | 下一批或暂缓。 |
| `125` | Exotic Spirits | `ItemDelivery` | 给 Gus 一个椰子。 | 缺。 | 沙漠/椰子可用性。 | 等别的系统。 |
| `126` | Errand for your Wife | `ItemDelivery` | 给 Emily 200 个纤维。 | 缺。 | 多数量交付；原版完成后还有 `emilyFiber` 特殊标记。 | 下一批，需特殊处理。 |
| `127` | Haley's Cake-Walk | `Basic` | 晴天早晨带巧克力蛋糕进城。 | 缺。 | 时间、天气、地点、物品、Haley 事件依赖。 | 等 NPC 剧情。 |
| `128` | ... | `SecretLostItem` | 把指定物品交给 Abigail，和 `129` 互斥。 | 缺。 | 没有 `SecretLostItemQuest` 真类型；获取来源依赖秘密纸条。 | 先做底座，触发等秘密纸条。 |
| `129` | ... | `SecretLostItem` | 把指定物品交给 Caroline，和 `128` 互斥。 | 缺。 | 没有 `SecretLostItemQuest` 真类型；获取来源依赖秘密纸条。 | 先做底座，触发等秘密纸条。 |
| `130` | The Pirate's Wife | `Basic` | 帮 Birdie 找丈夫遗物。 | 缺。 | 姜岛后期任务线。 | 等别的系统。 |
| `131` | Willy's Challenge | `Fishing` | 钓 3 条沙鱼。 | 缺。 | 沙漠鱼类/任务触发依赖。 | 等别的系统。 |
| `132` | Getting Started | `ItemHarvest` | 从鸡身上收获一个蛋。 | 缺。 | 动物系统/新版本任务分支。 | 下一批或暂缓。 |
| `133` | Feeding Animals | `Building` | 建一个筒仓。 | 缺。 | Robin 建筑/动物喂养流程。 | 下一批或暂缓。 |
| `134` | The Giant Stump | `Basic` | 修好大树桩。 | 缺。 | 1.6 巨大树桩后期线依赖。 | 等别的系统。 |

## 第一批建议

第一批不要贪多，只做这些：

1. 核对已经有的早期任务：`6`, `7`, `9`, `18`；`14`, `15`, `17` 已处理，后续实测。
2. 补早期缺失任务：`25`；`13`, `16`, `11`, `12` 已补，`10` 按源码主流程跳过。
3. 做 `LostItemQuest` 底座，然后处理 `100`, `102`, `107`。
4. 做 `SecretLostItemQuest` 底座，但 `128`, `129` 的真正触发先等秘密纸条系统。
5. 单独核对每日公告栏任务，不和静态任务内容混在同一个 PR 里。

暂时不要拉进第一批：

- Qi/赌场/沙漠：`2`-`5`, `30`, `125`, `131`；`19`, `20` 已能依托现有头骨钥匙/骷髅矿洞入口先补齐。
- 社区中心/Joja：`26`。
- 法师/女巫/下水道：`27`, `28`。
- 秘密纸条完整系统：`29`, `31`。
- 姜岛/1.6 后期：`130`, `134`。
