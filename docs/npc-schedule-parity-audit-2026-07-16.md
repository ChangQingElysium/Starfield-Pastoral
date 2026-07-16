# NPC 日程原版一致性审查（2026-07-16）

## 结论

当前 NPC 日程不是对 `源文件/Content/Characters/schedules` 的完整导入，而是一批手工重写的简化 JSON，再叠加少量节庆运行时覆盖。

仓库里早已有一份正确的目标规划：`docs/root-archive/ALL_NPC_SCHEDULE_PARITY_MASTER_PLAN.md`，其中明确要求“原始 schedule 行是唯一事实源，禁止手写重编”。当前实现没有完成这个迁移目标。

## 审查口径

- 原版事实源：`StardewValley/NPC.cs::TryLoadSchedule`、`Content/Characters/schedules/*.json`。
- 模组数据：`data/stardewcraft/npc/schedules/*.json`。
- 模组选表器：`NpcScheduleRuntimeService.selectScheduleKey`。
- 对比时将 `Monday`/`Mon` 等星期写法视为同一 key。
- “节点一致”只比较 checkpoint 时间与目标 location，不强求 Minecraft 坐标与 SDV tile 直接相等。

## 总体数据

| 指标 | 结果 |
|---|---:|
| 模组与原版都有日程的 NPC | 29 |
| 原版 schedule key | 387 |
| 模组能对应到的原版 key | 206（53.2%） |
| 模组自定义/额外 key | 49 |
| 原版时间节点 | 1479 |
| 模组时间节点 | 893（60.4%） |
| 共有 key 中时间+地点序列一致 | 69 / 206（33.5%） |

## 选表器层面的系统问题

### P0

1. **婚后日程未接入**
   - 原版优先查 `marriage_<festival>`、`marriage_<season>_<day>`、`marriageJob`、`marriage_<weekday>`。
   - 模组选表器不判断婚姻，日程 JSON 也基本没有 marriage key。

2. **海岛度假日程未接入**
   - 原版的 `islandScheduleName` 优先级仅次于一年目 Green Rain。
   - 模组只有 Resort 对话数据，没有对应日程选择与路线。

3. **好感日程只查“恰好当前心数”**
   - 原版从当前心数逐级向下寻找可用 key。
   - 模组只构造一个 `<day>_<hearts>` / `<weekday>_<hearts>` 候选。
   - 例如玩家 8 心时，只有 6 心变体的日程会被错过。

4. **`rain2` 随机语义错误**
   - 原版下雨时只有 50% 概率尝试 `rain2`，否则进入 `rain`。
   - 模组只要存在 `rain2` 就永远优先选它。

5. **MAIL 日程条件不读取真实旗标**
   - 当前默认策略是 `MAIL` 阻断、`NOT MAIL` 放行，不是原版条件计算。

6. **地点替换 key 未实现**
   - `JojaMart_Replacement`、`CommunityCenter_Replacement` 不是普通日期 key，而是地图状态改变时的目的地替换。
   - 当前选表器不执行这层语义。

7. **全局 NPC 日程绑定某个玩家**
   - 单机优先房主，专服退化为首个在线非旁观玩家。
   - 好感与 MAIL 上下文仍可因在线顺序变化，不具备服务器确定性。
   - Pam 的 `ccVault -> bus` 分支已在本轮改为“世界存档中任意玩家拥有 ccVault”，不再依赖在线顺序。

### P1

8. **节庆日程是局部硬编码覆盖**
   - Desert Festival 只覆盖预设访客/摊主。
   - Squid Fest 和 Trout Derby 主要只覆盖 Willy。
   - 这些服务可以让部分 NPC “看起来在节庆地图”，但不等于完整执行原版当日 schedule。

9. **原版 `bed` 回家语义没有通用解析**
   - 现有 JSON 通常手动改写为某个 `@npc_sleep` 点，造成大量重复及转写差异。

10. **原版对话 token/路线动画只部分保留**
    - 解析器只提取一个行为 token，原版的时段对话与更复杂命令未完整建模。

## 逐 NPC 对照

表中“key 覆盖”是模组存在的原版同义 key / 原版总 key；“节点”是模组 / 原版。

| NPC | key 覆盖 | 节点 | 等级 | 主要缺口 |
|---|---:|---:|---|---|
| Abigail | 16/23 | 61/72 | P1 | rain2、Desert Festival、多个婚后/星期变体 |
| Alex | 7/15 | 35/58 | P1 | 沙漠节、冬 17、夏 16、婚后日程 |
| Caroline | 10/11 | 53/52 | P2 | Desert Festival；部分日程被展开重写 |
| Clint | 5/8 | 11/19 | P1 | CC replacement、Desert Festival、冬 16 |
| Demetrius | 7/10 | 22/45 | P1 | Desert Festival、夏 25、冬 16；节点大量缩减 |
| Elliott | 9/16 | 26/64 | P1 | Squid Fest、Desert Festival、冬 17、婚后变体 |
| Emily | 5/15 | 22/55 | P0 | 约 2/3 key 缺失，多日 Desert Festival/婚后变体缺失 |
| Evelyn | 8/12 | 26/54 | P1 | Desert Festival、冬 17、特殊日 2/23 |
| George | 5/9 | 17/32 | P1 | Desert Festival、冬 17、周日变体 |
| Gus | 5/8 | 32/50 | P1 | Desert Festival、秋 4；店内动作序列改写 |
| Haley | 7/16 | 30/66 | P1 | Desert Festival 两天、冬 9/16、婚后变体 |
| Harvey | 10/15 | 45/68 | P1 | Desert Festival、冬 15、marriageJob |
| Jas | 8/13 | 25/39 | P1 | Desert Festival、冬 15/18、特殊日 9/23 |
| Jodi | 3/13 | 21/95 | P0 | 严重缩减；Desert Festival、Joja replacement、春 11/18、冬 17 |
| Leah | 6/15 | 25/53 | P0 | Desert Festival、春 16、无桥夏日程、冬 15、婚后 |
| Lewis | 15/20 | 77/100 | P1 | Desert Festival 三天、秋 9、冬 16；仍有一批自定义展开 key |
| Linus | 6/8 | 20/36 | P1 | Desert Festival、冬 15；共有 key 节点也不一致 |
| Marnie | 5/13 | 36/42 | P0 | Desert Festival、秋 18、冬 16/18、多个星期 key |
| Maru | 8/17 | 33/61 | P1 | CC replacement、Desert Festival、冬 16、marriageJob |
| Pam | 9/9 | 24/23 | 本轮修复 | 原版 key 已补齐；地点使用现有 Minecraft endpoint |
| Penny | 7/22 | 35/68 | P0 | 缺 15 个原版 key，含 CC replacement、Desert Festival、rain2、婚后 |
| Pierre | 3/5 | 21/15 | P1 | Desert Festival、Fri；模组另行扩写四季 |
| Robin | 4/9 | 21/33 | P1 | Desert Festival、夏 18、冬 16、多个星期 key |
| Sam | 10/22 | 47/71 | P1 | Desert Festival、Joja replacement、rain2、婚后、多个特殊日 |
| Sandy | 0/3 | 1/13 | P0 | 被固定为全天站柜台；原版 spring、fall_15、DesertFestival 均未执行 |
| Sebastian | 8/19 | 42/56 | P0 | Desert Festival、rain2、夏 4、冬 16、婚后/好感特殊日 |
| Shane | 6/15 | 24/60 | P0 | Desert Festival、冬 15、大量婚后与周日变体 |
| Vincent | 8/13 | 36/40 | P1 | Desert Festival、春 11、冬 16、特殊日 9/23 |
| Willy | 6/13 | 25/39 | P0 | Squid Fest、Trout Derby、Desert Festival、诊所日与冬 15–17 |

## NPC 集合差异

- 原版有、模组未接入：`Kent`、`Leo`、`LeoMainland`。
- 模组额外静态/自定义文件：`Gunther`、`Krobus`、`Marlon`、`Wizard`。
- `Morris` 在 capability 中启用了 pathing，但没有 schedule JSON；需要单独确认它是否应保持剧情/商店驱动。
- `Dwarf`、`Joja Cashier`、`Governor` 等是静态 NPC，不应用普通村民日程衡量。

## 修复顺序

1. 先修选表器：婚后/海岛上下文、好感向下匹配、rain2 随机、MAIL、replacement。
2. 建立原版 schedule 导入层，不再手工复制 387 个 key。
3. 将“原版 tile 意图”与“Minecraft endpoint”分离；缺 endpoint 时显式报错，不改写日程。
4. 先修 P0 NPC：Jodi、Penny、Emily、Sandy、Shane、Sebastian、Willy、Marnie、Leah。
5. 再处理 P1 NPC，最后补 Kent/Leo 与婚后/海岛功能。
6. 为每个 NPC 生成“原版 key -> 选中 key -> endpoint -> 实际到达”验收轨迹。

## 完成定义

- 同一存档状态下，模组与原版选中相同 schedule key。
- checkpoint、location、facing、behavior 与条件分支语义一致。
- 所有目的地有明确 endpoint，NPC 可在容差内到达。
- 多人服的全局日程不依赖玩家上线顺序。
- 任何回退、条件拒绝和坐标缺失都能在 trace 中解释。
