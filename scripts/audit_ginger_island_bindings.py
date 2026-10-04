#!/usr/bin/env python3
"""Authoring ledger: preserve every source, plan entry and environment family.

Exported geometry is not a completed gameplay binding. This command makes that
distinction explicit; it does not change acceptance records, maps or saves.
"""
import collections
import fnmatch
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/ginger-island-research'
OUT = DOC / 'integration'
JAVA = 'src/main/java/com/stardew/craft/'
LOC = '源文件/StardewValley.Locations/'

# First match wins. State images inherit the same owner's behavior, not a second job.
# Evidence names locate the original function; an absent implementation stays pending.
PROFILES = [
    ('*/construction_parrots/effects/*', 'construction_effect', '施工时飞动的碎木；不作为独立家具出售。',
     '源文件/StardewValley.BellsAndWhistles/ParrotUpgradePerch.cs', None, '鹦鹉升级事件、临时模型实体和同步销毁'),
    ('*/construction_parrots/*', 'construction_actor', '原版鹦鹉施工、飞行、木料搬运动作。',
     '源文件/StardewValley.BellsAndWhistles/ParrotUpgradePerch.cs', None, '原生连续动画、施工调度和农场共享解锁'),
    ('*/golden_walnut/*', 'walnut_reward', '核桃为农场共享货币；发现与消费分开，奖励不能重复领取。',
     '源文件/StardewValley/FarmerTeam.cs', JAVA + 'gingerisland/WalnutDebris.java', '实体三维外观、完整奖励源和隐藏点绑定'),
    ('*/giant_turtle/*', 'progress_gate', '北口首次鹦鹉门槛与西口10核桃升级各有海龟挡路；度假村为另一个20核桃升级，不能混为海龟支路，三维移动不得绕过门槛。',
     LOC + 'IslandSouth.cs', None, '角色动画、共享状态和三维通行门槛'),
    ('*/flame_guide/*', 'event_actor', '火精灵按首次登岛事件引导北向路线。', LOC + 'IslandSouth.cs', None, '引导事件和原生动画'),
    ('*/leo_arrival/*', 'npc', '雷欧初次相遇、后续社交、事件和日程。', LOC + 'IslandEast.cs', None, '角色模型、事件和原版 NPC 数据'),
    ('*/parrot_perch/*', 'upgrade', '初始喂鹦鹉和后续升级；消费当前岛所属农场核桃。',
     LOC + 'IslandHut.cs:performAction', None, '升级条件、报价、施工和解锁范围'),
    ('*/banana_altar/*', 'puzzle_components', '献一根香蕉触发大猩猩序列，BananaShrine 一次奖励 3 核桃。',
     LOC + 'IslandEast.cs:performAction;SpawnBananaNutReward', None, '供奉、临时角色、火把状态和一次性奖励'),
    ('*/banana_torches/*', 'puzzle_state', '火把由香蕉祭坛事件点燃；不是可随意开关的第二个道具。',
     LOC + 'IslandEast.cs:performAction', JAVA + 'gingerisland/IslandFlameBlock.java', '祭坛事件触发点火及复位'),
    ('*/gem_bird/*', 'puzzle_actor', '雨天区域宝石鸟掉落指定宝石；区域对应按存档种子确定。',
     '源文件/StardewValley/IslandGemBird.cs', None, '生成、接近飞离、掉宝石和连续动画'),
    ('*/gem_pedestal/*', 'puzzle_state', '同一底座存放/取回物品；四底座解答完成后锁定。',
     LOC + 'IslandShrine.cs;源文件/StardewValley.Objects/ItemPedestal.cs', None, '物品槽、互斥、种子答案与共享完成状态'),
    ('*/gem_shrine_fx/*', 'puzzle_effect', '解答成功演出，奖励 5 核桃且只发一次。', LOC + 'IslandShrine.cs', None, '完成事件和原生动画'),
    ('*/gem_shrine/*', 'puzzle_components', '方向标记、神龛及完成状态归同一宝石谜题。', LOC + 'IslandShrine.cs', None, '与四个底座的状态和完成演出绑定'),
    ('*/gorilla/*', 'event_actor', '香蕉祭坛连续演出中的大猩猩。', LOC + 'IslandEast.cs', None, '事件时序和原生角色动画'),
    ('*/leo_hint_parrot/*', 'hint_actor', '原版按剩余隐藏核桃、有限掉落和当天提示生成提示。', LOC + 'IslandHut.cs', None, '原版提示分类、每日限制和角色动画'),
    ('*/walnut_bush/*', 'walnut_source', '摇动核桃灌木收集其固定隐藏核桃；持久化一次性来源。', LOC + 'IslandLocation.cs', None, '摇动、发现标识、核桃奖励与灌木状态'),
    ('*/walnut_tree/*', 'walnut_source', '弹弓命中树上核桃目标才掉落；其他工具不能替代。', LOC + 'IslandNorth.cs', None, '三维弹射碰撞、一次性来源和奖励'),
    ('*/leo_house_inside/*', 'scenery', '树屋固定容器和草铺；不擅自变成可搜刮储物箱。', LOC + 'IslandHut.cs', None, '随树屋场景放置，并复核原图 Action/TouchAction'),
    ('*/dig_boulder/*', 'progress_gate', '爆破封洞石并解救蜗牛教授；碎块为事件临时部件。', LOC + 'IslandNorth.cs', None, '炸弹触发、教授救出事件和封洞状态'),
    ('*/dig_bridge/*', 'upgrade_components', '损坏/修复桥段归一次鹦鹉修桥解锁。', LOC + 'IslandNorth.cs', None, '施工事件、对应构件替换和通行门槛'),
    ('*/dig_site/*', 'excavation_scenery', '化石浮雕和碎片是采石场场景，不等于新的逐像素矿点。', LOC + 'IslandNorth.cs', None, '场景定位；挖掘掉落须单独使用原版采石场表'),
    ('*/field_room/fossil_*/*', 'donation_display', '11 个化石捐赠槽、四组奖励和全部捐齐状态。',
     LOC + 'IslandFieldOffice.cs:donatePiece', None, '捐赠菜单、分组状态、奖励和对应展示变体'),
    ('*/field_room/survey_board/*', 'survey', '紫花 22、海星 18；一天共享一次答错限制，正确各奖励核桃。',
     LOC + 'IslandFieldOffice.cs:answerDialogueAction', None, '调查菜单、原版条件、每日失败和共享结果'),
    ('*/field_room/radio/*', 'ambient_effect', '教授在场时每 600ms 显示音符；原版不是储物箱或可切电台。',
     LOC + 'IslandFieldOffice.cs:UpdateWhenCurrentLocation', None, '在场条件和三维临时音符表现'),
    ('*/field_room/*', 'scenery', '帐篷室内画布地板和工作台固定场景。', LOC + 'IslandFieldOffice.cs', None, '场景摆放和所有 Action 锚点复核'),
    ('*/field_tent/*', 'entry_structure', '简化分区碰撞保留门口，进入独立的教授帐篷室内。', LOC + 'IslandNorth.cs',
     JAVA + 'block/decor/MapDecorStaticBlock.java', '场景门口及原版室内 Warp 绑定'),
    ('*/golden_parrot/*', 'paid_completion', '金鹦鹉按剩余核桃报价；不把付费补全当普通掉落。',
     '源文件/StardewValley/Game1.cs:ActivateGoldenParrot', None, '完整原版条件、报价、过夜补全和动画'),
    ('*/island_trader/*', 'shop_actor', '贸易商按原版货币、日期和解锁条件出售物品；原版待机连续播放。',
     LOC + 'IslandNorth.cs', None, '原版商店表、商人和整摊原生动画'),
    ('*/north_cliff/*', 'terrain', '现有土/岩壁及认可的崖面构件；沿三维地形拼装。', LOC + 'IslandNorth.cs',
     JAVA + 'block/decor/MapDecorStaticBlock.java', '不同坡向、转角、颗粒和实际地图拼装'),
    ('*/professor/*', 'npc', '教授获救后捐赠、调查和帐篷内日程。', LOC + 'IslandFieldOffice.cs', None, '角色、事件和捐赠/调查菜单'),
    ('*/farm_interior/fridge_open/*', 'storage', '复用现有冰箱库存及连续开合；厨房查食材须覆盖岛农舍。',
     LOC + 'IslandFarmHouse.cs:checkAction', JAVA + 'block/utility/FridgeBlock.java', '岛农舍厨房范围和多人菜单锁复核'),
    ('*/farm_interior/red_cooker/*', 'cooking', '厨房使用现有烹饪配方和材料系统。', LOC + 'IslandFarmHouse.cs',
     JAVA + 'gingerisland/IslandWorkstationBlock.java', '岛农舍厨房冰箱范围和原版解锁条件'),
    ('*/farm_interior/stove_fireplace/*', 'fireplace', '无燃料开关；点燃状态、光源和火焰贴图由同一壁炉拥有。',
     '源文件/StardewValley.Objects/Furniture.cs:checkForAction', JAVA + 'gingerisland/IslandFlameBlock.java', '真实客户端状态/火焰贴图检查'),
    ('*/farm_interior/tropical_bed/*', 'bed', '两格宽床，两条睡眠位；睡眠高度贴合被面。', LOC + 'IslandFarmHouse.cs:InitializeBeds',
     JAVA + 'gingerisland/TropicalBedBlock.java', '岛农舍睡眠权限、过夜地点和重生点绑定'),
    ('*/farm_obelisk/*', 'travel', '一秒演出后回当前农场 WarpTotemEntry；不是公共农场或床位。', LOC + 'IslandWest.cs:performAction', None, '原版返回点、访客当前农场和转场'),
    ('*/farmhouse_modules/*', 'upgrade_components', '废墟/修复农舍构件，仅替换升级拥有范围，不重铺玩家农田。', LOC + 'IslandWest.cs', None, '农舍 20 核桃、邮箱 5、图腾 20 等前置条件及施工范围'),
    ('*/farm_tidepools/*', 'scenery', '海滩破木和漂流木用于固定岸边装饰。', LOC + 'IslandWest.cs', None, '沿海岸摆放及原图行动属性复核'),
    ('*/ginger_plant/*', 'forage', '野生姜用锄头挖取；留存原版产地和再生规则。', LOC + 'IslandWest.cs', None, '原生动画、锄挖、物品产出和每日生成'),
    ('*/parrot_platform/*', 'transport', '站台目的地有解锁条件、费用及起飞/降落；保留站立区域。',
     '源文件/StardewValley.BellsAndWhistles/ParrotPlatform.cs', None, '交通选择、共享解锁和完整运输动画'),
    ('*/birdie/*', 'npc', '海盗妻子任务按玩家保存，钓鱼为坐姿并复用模组钓竿。', LOC + 'IslandWest.cs:checkAction', None, '角色原生动画、钓竿挂点和原版任务'),
    ('*/birdie_hut/*', 'scenery_structure', '原版没有单独可进入的 Birdie 室内；小屋作外景整体。', LOC + 'IslandWest.cs',
     JAVA + 'block/decor/MapDecorStaticBlock.java', '按原图外景定位及关键站位预留'),
    ('*/buried_hints/*', 'walnut_clue', '地面图案是固定核桃藏点线索；挖掘坐标独立持久化。', LOC + 'IslandLocation.cs', None, '藏点、锄挖条件、共享发现和一次性奖励'),
    ('*/captain_room/*', 'interior_structure', '四面船舱，48 格室内和两格门洞留空；夜窗为同一窗状态。',
     '源文件/Content/Maps/Island_CaptainRoom.tmx', JAVA + 'block/decor/MapDecorStaticBlock.java', '室内地点、窗时段和入口 Warp'),
    ('*/crystal_cave/*', 'music_puzzle', '五块灰度水晶运行时着色；记忆序列、失败缓和、闭/亮眼和一次 3 核桃。',
     LOC + 'IslandWestCave1.cs', None, '水晶合规导出、记忆游戏、响应动画和眼睛发光'),
    ('*/gourmand*/*', 'crop_puzzle', '甜瓜/小麦/大蒜须按序在田里成熟且未收获；每轮 5 核桃。',
     LOC + 'IslandFarmCave.cs', None, '地里作物条件、人物事件、三轮状态和奖励'),
    ('*/sand_duggy/*', 'walnut_actor', '四洞移动、玩家距离及空洞条件；当前洞口受工具命中才领奖。',
     '源文件/StardewValley.BellsAndWhistles/SandDuggy.cs', None, '三维洞口位置、移动/命中动画与一次性核桃'),
    ('*/shipwreck/*', 'entry_structure', '沉船为可到达船舱入口的外景，少量分区 AABB；CaptainRoom 单独室内。',
     '源文件/Content/Maps/Island_W.tmx', JAVA + 'block/decor/MapDecorStaticBlock.java', '入口地面站位及船舱 Warp 绑定'),
    ('*/tiger_grove/*', 'monster', '虎纹史莱姆含花/特殊变体，原版行为及掉落。', '源文件/StardewValley.Monsters/GreenSlime.cs', None, '怪物原生动画、AI、生成和掉落'),
    ('*/07_resort/visitors/*', 'npc_activity', '原版可访客名单、日期/天气/解锁条件；日程泳装及连续坐/躺/喝饮料等动作。',
     LOC + 'IslandSouth.cs', None, '访客调度、主模型和活动模型切换及原生弯曲骨骼'),
    ('*/07_resort/sign_chair/resort_beach_chair.bbmodel', 'seat', '玩家可坐躺椅，坐位高度按认可模型。',
     '源文件/StardewValley.Objects/Furniture.cs:checkForAction', JAVA + 'block/utility/ChairBlock.java', '原版度假村固定坐位布局'),
    ('*/07_resort/sign_chair/*', 'resort_schedule', '营业告示修改次日开门状态，不立即赶走今日游客。',
     LOC + 'IslandSouth.cs:performAction', None, '告示、次日状态、权限及访客日程'),
    ('*/07_resort/*', 'resort_components', '度假村房屋、柜台、更衣室、毛巾和整把遮阳伞；构件属于场景/升级。',
     LOC + 'IslandSouth.cs', None, '40 核桃升级、酒吧商店、活动站位和更衣动作'),
    ('*/flute_block/*', 'music_puzzle', '低矮木长笛块，原版音高递进、靠近发声及美人鱼雨天音序。',
     LOC + 'IslandSouthEast.cs;源文件/StardewValley/Object.cs', None, '音高、播放冷却、相邻检测和谜题序列'),
    ('*/mermaid/*', 'puzzle_actor', '雨天演出，按原版音序响应并奖励 5 核桃；手臂和尾用原有连续蒙皮。',
     LOC + 'IslandSouthEast.cs', None, '原生 Armature 导出、事件时序和一次性奖励'),
    ('*/mermaid_rock/*', 'puzzle_scenery', '美人鱼礁石需合理站位和雨天表演锚点，使用简化整体碰撞。',
     LOC + 'IslandSouthEast.cs', JAVA + 'block/decor/MapDecorStaticBlock.java', '雨天演员和表演锚点'),
    ('*/pirate_cove/cast/*', 'event_actor', '海盗按夜晚、偶数日和天气条件出现；对话、免费酒和飞镖奖励。',
     LOC + 'IslandSouthEastCave.cs', None, '八名角色原生导出和完整聚会条件'),
    ('*/pirate_cove/darts/*', 'minigame', '三维鹦鹉飞镖和两格宽盘，20 区/倍数环/数字；三次奖励门槛 20/15/10。',
     '源文件/StardewValley.Minigames/Darts.cs', None, '投掷/命中数学、计分、瞄准演出和三次核桃奖励'),
    ('*/pirate_cove/bar_and_stool/pirate_square_stool.bbmodel', 'seat', '方凳复用座位系统，保留认可坐位高度。',
     '源文件/StardewValley.Objects/Furniture.cs:checkForAction', JAVA + 'block/utility/ChairBlock.java', '海盗夜场坐位绑定'),
    ('*/pirate_cove/*', 'scenery', '固定海盗湾家具和桌上内容；不把藏宝图、金币拆成大件独立组件。',
     LOC + 'IslandSouthEastCave.cs', JAVA + 'block/decor/MapDecorStaticBlock.java', '三维场景摆放；酒吧/演员的交互单独绑定'),
    ('*/sand_starfish/*', 'survey_clue', '用于原图海星调查及沙地线索；数量与原版问题答案一致。',
     LOC + 'IslandFieldOffice.cs', None, '地图计数与调查视觉线索位置'),
    ('*/caldera/forge/*', 'forge', '使用现有锻造菜单、规则和库存；扩展格同一操作点。', LOC + 'Caldera.cs:checkAction',
     JAVA + 'gingerisland/IslandWorkstationBlock.java', '真实游戏站位和原版进入条件'),
    ('*/caldera/monkeys/*', 'ambient_actor', '原版山顶猴群动作；不使用脚边二维投影。', LOC + 'Caldera.cs', None, '原生动画和原版角色出现条件'),
    ('*/caldera/monument/*', 'perfection', '完美达成雕刻/面具领取条件和一次性领取。', LOC + 'Caldera.cs:checkAction', None, '完美检查、雕刻状态及面具领取'),
    ('*/containers/volcano_canister/*', 'breakable_loot', '174/175 火山罐按原版破坏次数和掉落表；整体 AABB。',
     '源文件/StardewValley.Objects/BreakableContainer.cs', None, '原版工具、碎片和罐掉落'),
    ('*/containers/volcano_chest/*', 'treasure', '普通/稀有宝箱开盖动画及每层种子掉落；不能反复重开刷物品。',
     LOC + 'VolcanoDungeon.cs', None, '原生箱盖动画、互斥、战利品及一次开启持久化'),
    ('*/dwarf_shop/*', 'shop_components', '矮人商店固定陈设；购物要求能理解矮人语。', LOC + 'VolcanoDungeon.cs:checkAction', None, '商店 77 锚点、语言条件和原版货品'),
    ('*/forage/magma_cap/*', 'forage', '熔岩菇是可采集物，需品质/拾取和原版每日生成。', LOC + 'VolcanoDungeon.cs', None, '采集物品和生成表'),
    ('*/mechanisms/volcano_parrot_perch/*', 'upgrade', '按火山桥/捷径升级条件报价和施工。', LOC + 'VolcanoDungeon.cs', None, '升级、农场核桃和拥有范围'),
    ('*/mechanisms/volcano_shortcut_hole/*', 'travel', '楼层出口请求确认并返回火山入口，不能当无逻辑装饰洞。', LOC + 'VolcanoDungeon.cs:checkAction', None, '确认、位置和转场'),
    ('*/mechanisms/volcano_spout/*', 'watering_source', '干/出水状态及补水管属于同一供水场景机制。', LOC + 'VolcanoDungeon.cs', None, '原版开水条件、灌水判定和动画'),
    ('*/mechanisms/volcano_well_pipe/*', 'watering_source', '火山供水点可填充水壶，按原图 TouchAction 定位。', LOC + 'VolcanoDungeon.cs', None, '注水动作锚点和交互距离'),
    ('*/monsters/*', 'monster_or_effect', '逐怪物还原 AI、触发、投射物、受伤/死亡、掉落和连续动画。', '源文件/StardewValley.Monsters/', None, '各怪物原版专属逻辑及原生模型导出'),
    ('*/bones/*', 'loot_scenery', '龙骨固定造景；龙牙采集点和装饰骨片必须区分。', LOC + 'VolcanoDungeon.cs', None, '原版龙牙采集规则、地图条件与固定装饰分工'),
    ('*/setpieces/*', 'scenery', '齿轮、柱、碎石等固定场景；按地图属性确认地板井盖用途。', LOC + 'VolcanoDungeon.cs', None, '场景定位及井盖/通行属性核对'),
    ('*/volcano_terrain*', 'terrain', '地板多变体和原有连接族；浇熔岩出现连边薄层，5 层/山顶排除。',
     LOC + 'VolcanoDungeon.cs:performToolAction', JAVA + 'gingerisland/VolcanoCooling.java', '岩壁连接、热侧面、地形生态；冷却随动态楼层复位'),
    ('*/caldera/terrain/*', 'terrain', '山顶地板及岩壁多向接缝；山顶熔岩浇水只冒汽不生成平台。', LOC + 'Caldera.cs:performToolAction', None, '岩壁/热侧面连接及山顶浇水冒汽分支'),
    ('*/mechanisms/dwarf_gate/*', 'gate_material', '石栏、上楣和按钮计数面板归同一火山开门机关。',
     LOC + 'VolcanoDungeon.cs:performTouchAction', None, '按钮逐项同步、门开关及所有面板/石栏状态'),
    ('*/mechanisms/volcano_floor_switch/*', 'latched_switch', '一格低矮踏钮；只有玩家踩下，服务端保持按下；按钮不变绿，计数灯在门柱。',
     LOC + 'DwarfGate.cs:OnPress;'+LOC+'DwarfGate.cs:ApplyTiles', JAVA + 'gingerisland/VolcanoFloorSwitchBlock.java',
     '按钮到指定门组的关联、门柱计数面板、全部按下后 500ms 开门及动态楼层复位'),
    ('*/mechanisms/volcano_bridge/*', 'bridge_material', '桥体/扶手与鹦鹉解锁共享状态，兼顾四向拼装。', LOC + 'VolcanoDungeon.cs', None, '四向扶手、升级拥有范围及通行门槛'),
    ('*/qi_room_tile/*', 'terrain', '齐先生室内连边地砖使用原有连接机制。', '源文件/Content/Maps/QiNutRoom.tmx', None, '齐地砖连边 16 状态和室内地形'),
    ('*/qi_walnut_door/*', 'progress_gate', '累计发现 100 核桃才进齐先生房，消费不扣累计发现；连续门动画。', LOC + 'IslandWest.cs:checkAction', None, '权限门槛、原生开门和室内 Warp'),
    ('*/qi_challenge_board/*', 'special_orders', '齐先生挑战板使用原版特殊订单、刷新、多人归属。', '源文件/StardewValley/GameLocation.cs:performAction', None, '齐挑战列表和农场共享订单/奖励'),
    ('*/qi_dropbox/*', 'special_orders', '挑战指定交付槽，不是可自由搜刮的装饰箱。', '源文件/StardewValley/GameLocation.cs:performAction', None, '物品验证、订单进度、动画贴图和交付菜单'),
    ('*/qi_gem_shop/*', 'shop', '齐钻商店使用原版货币和货品，不花金币或核桃。', '源文件/StardewValley/GameLocation.cs:performAction', None, '齐钻余额、货品和原生商店动画'),
    ('*/qi_cat/*', 'ambient_effect', '齐猫由交互触发指定动画，不是额外 NPC。', '源文件/StardewValley/GameLocation.cs:ShowQiCat', None, '触发、贴图动画与临时效果'),
    ('*/fizz/*', 'npc_shop', 'Fizz 出现需原版介绍条件；出售完美豁免，不冒充齐钻商人。', '源文件/StardewValley/NPC.cs:checkAction', None, '角色和原版豁免交易条件'),
    ('*/qi_computer_desk/*', 'perfection', '完美追踪器按原版各项完成度展示。', '源文件/StardewValley/GameLocation.cs:performAction', None, '完美统计和菜单'),
    ('*/ostrich_incubator/*', 'incubator', '原版谷仓；项目沿用鸵鸟 family=coop 的既有覆写，放置与孵化都使用同一住所配置；既有时长/默认 9000、Coopmaster 半时和过夜完成保持。',
     '源文件/StardewValley/Object.cs:OutputIncubator;placementAction;源文件/Content/Data/Machines.json:(BC)254',
     JAVA + 'gingerisland/OstrichIncubatorBlock.java', '有效二级鸡舍内放置/放蛋及谷仓拒绝回归；实际领取全流程与客户端呼吸动画仍单独验收'),
    ('*/reward_machines/heavy_tapper/*', 'machine', '同一树上一个任务，tapper_multiplier_2，按产出天数先取整减半。',
     '源文件/StardewValley.TerrainFeatures/Tree.cs:UpdateTapperProduct;TryGetTapperOutput', JAVA + 'gingerisland/HeavyTapperBlock.java', '真实树木挂点和客户端表现'),
    ('*/reward_machines/hopper/*', 'auto_loader', '原版只从上方进料，机器已有产出时停；收走产出才补料，不自动抽取产物。',
     '源文件/StardewValley/Object.cs:AttemptAutoLoad;CheckForActionOnMachine', None, '27 槽进料斗、方向、机器插入规则和原生动画'),
    ('*/collectible_furniture/tropical_tv/*', 'tv', '热带电视沿用现有节目系统。', '源文件/StardewValley.Objects/TV.cs', JAVA + 'block/tv/TVBlock.java', '真实客户端电视屏幕和朝向'),
    ('*/collectible_furniture/tropical_chair/*', 'seat', '热带椅可坐，按认可模型坐位高度。', '源文件/StardewValley.Objects/Furniture.cs:checkForAction', JAVA + 'block/utility/ChairBlock.java', '真实客户端坐姿和坐位朝向'),
    ('*/window_and_torch/basic_window/*', 'ambient_state', '白昼/夜晚为同一窗口，不注册第二个夜窗物品。', '源文件/StardewValley.Objects/Furniture.cs', None, '原版时间/照明状态及联动模型'),
    ('*/window_and_torch/jungle_torch/*', 'torch', '丛林火把应支持原版开关和点亮；不用替代灯具。', '源文件/StardewValley.Objects/Furniture.cs:checkForAction', None, '熄灭几何状态、光源和火焰贴图'),
    ('*/collectible_furniture/*', 'furniture_scenery', '认可家具的原版外观和放置规则，挂画贴墙，地毯贴地。', '源文件/StardewValley.Objects/Furniture.cs', JAVA + 'block/decor/MapDecorStaticBlock.java', '墙面/地面放置、家具商品绑定和客户端表现'),
    ('*/parrot_trinket/*', 'companion', '饰品鹦鹉跟随主人、肩膀停落及原版几率行为；复用已认可模型。', '源文件/StardewValley.Companions/FlyingCompanion.cs', None, '饰品绑定、主人同步及原生跟随/停落动画'),
    ('*/caldera_floating_face/*', 'perfection_actor', '完美后山顶漂浮面孔演出。', LOC + 'Caldera.cs', None, '完美条件、连续动画和原版演出'),
    ('*/counting_purple_flowers/*', 'survey_clue', '调查紫花总数必须对应 22，不能随机散布改变答案。', LOC + 'IslandFieldOffice.cs', None, '全岛原图固定计数和调查定位'),
    ('*/north_marsupial/*', 'ambient_actor', '北侧袋鼠按原版触发出现/离开；不作为敌对怪。', LOC + 'IslandNorth.cs', None, '出现条件和连续动物动画'),
    ('*/13_environment/*', 'environment_scenery', '认可植被、海草及固定贝壳装饰；保留已有可采集物，不重复注册采集逻辑。',
     str(DOC.relative_to(ROOT) / 'visual-catalog/environment-audit.json'), JAVA + 'block/decor/MapDecorStaticBlock.java', '逐地图摆放与复用范围核实'),
]


def source_references(evidence):
    result = []; previous = None
    for token in evidence.split(';'):
        if '/' in token:
            previous = token.split(':')[0]
            result.append(token)
        else: result.append(previous + ':' + token)
    return result


def main():
    source = json.loads((OUT / 'source-assets.json').read_text())['entries']
    runtime = json.loads((ROOT / 'src/main/resources/data/stardewcraft/ginger_island/assets.json').read_text())['blocks']
    kinds = {x['id']: x['kind'] for x in runtime}
    rows = []
    for entry in source:
        row = dict(entry)
        if entry['status'] == 'excluded':
            row.update(role='excluded_authoring_stage', behavior_status='excluded')
            rows.append(row)
            continue
        match = next((p for p in PROFILES if fnmatch.fnmatch(entry['source'], p[0])), None)
        if match is None:
            row.update(role='unclassified', behavior_status='needs_source_audit', remaining='逐原版地图和交互函数核对，不默认当装饰')
        else:
            _, role, requirement, evidence, project, remaining = match
            row.update(role=role, source_requirement=requirement, source_evidence=source_references(evidence),
                       project=project, remaining=remaining,
                       behavior_status='partial' if project else 'pending')
        row['registration_kind'] = kinds.get(entry.get('id'))
        if entry['status'] == 'state_runtime_binding':
            row['registration_kind'] = kinds.get(entry['binding']['block'].split(':')[1])
        row['geometry_ready'] = entry['status'] in ('exported', 'existing_runtime_binding', 'state_runtime_binding', 'model_only')
        row['independent_block_item'] = entry['status'] == 'exported' and entry.get('id') in kinds
        if entry['status'] == 'model_only':
            row['catalog_disposition'] = entry['reason']
        rows.append(row)
    plan = json.loads((DOC / 'visual-catalog/production-progress.json').read_text())
    environment = json.loads((DOC / 'visual-catalog/environment-audit.json').read_text())
    # Include standalone mechanism/terrain textures absent from bbmodel enumeration.
    # Their existence is recorded even when there is no model or behavior binding yet.
    hashes = collections.defaultdict(list)
    for path in (ROOT / 'src/main/resources/assets/stardewcraft/textures/block/ginger_island').rglob('*.png'):
        hashes[hashlib.sha256(path.read_bytes()).hexdigest()].append(str(path.relative_to(ROOT)))
    textures = []
    for path in sorted((ROOT / 'assets-src/ginger_island').rglob('*.png')):
        if 'textures' not in path.parts: continue
        rel = str(path.relative_to(ROOT))
        match = next((p for p in PROFILES if fnmatch.fnmatch(rel, p[0])), None)
        copies = hashes.get(hashlib.sha256(path.read_bytes()).hexdigest(), [])
        textures.append({'source': rel, 'runtime_exact_copies': copies,
                         'role': match[1] if match else 'material_dependency',
                         'status': 'copied_not_behavior_verified' if copies else 'needs_binding_or_embedded_texture_review'})
    result = {'version': 1, 'boundary': '注册、原版行为、动画、地图放置和真实游戏验收分别记录；partial 不代表完成。',
              'collision_policy': '普通物件整体 AABB，入口/室内/操作空间用少量分区 AABB；不逐像素。',
              'assets': rows, 'standalone_textures': textures,
              'original_plan': plan['entries'], 'environment_families': environment['families']}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'behavior-audit.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    lines = ['# 姜岛资产接入与原版行为清单', '', result['boundary'], '',
             f"正式资产 {sum(r['role'] != 'excluded_authoring_stage' for r in rows)} 项；保留原规划 {len(plan['entries'])} 项及环境族 {len(environment['families'])} 项。", '',
             '| 资产 | 原版用途 | 注册/几何 | 逻辑状态 | 仍需接入 |', '|---|---|---|---|---|']
    for row in rows:
        if row['role'] == 'excluded_authoring_stage': continue
        label = row.get('id', row['source'])
        lines.append(f"| {label} | {row.get('source_requirement', row['role'])} | {row['status']} | {row['behavior_status']} | {row['remaining']} |")
    lines.extend(['', '状态图、施工效果、动物、怪物、泳装活动和原规划的复用/跳过项全部保留；没有以正式模型数替代原规划范围。',
                  '原版函数名只用于定位；每项完成前仍须读完整函数、数据表及项目实际实现。脚本不把几何导出推定为行为已对齐。', ''])
    (OUT / 'behavior-audit.md').write_text('\n'.join(lines))
    counts = collections.Counter(r['behavior_status'] for r in rows)
    print(json.dumps({'asset_status': counts, 'plan_entries': len(plan['entries']),
                      'environment_families': len(environment['families']), 'standalone_textures': len(textures)}, ensure_ascii=False))
    for row in rows:
        if row['role'] == 'unclassified': print('UNCLASSIFIED ' + row['source'])


if __name__ == '__main__': main()
