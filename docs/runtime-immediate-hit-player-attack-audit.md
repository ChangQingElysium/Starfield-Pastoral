# Runtime 即时命中 `player.attack` 副作用审计

日期：2026-07-30
范围：`combat/skill/handler` 中调用
`context.player().attack(target)` 的 Runtime Handler
性质：审计与经授权迁移记录；分类本身不授权批量迁移，也不修改原创技能数值、
阶段或表现

## 结论

迁移前共有 **29 个 Handler、30 个源码调用点**。数量差异来自
`ShadowDaggerExecuteSkillHandler` 的基础命中和处决追加命中各有一次
`player.attack`。

审计时分类如下：

- **22 个调用点可安全迁移到 `WeaponSkillDamage`**：没有技能专属逻辑依赖
  `Player.attack` 独有副作用；伤害、技能上下文和项目内命中后处理可以沿现有
  `LivingDamageEvent.Pre/Post` 路径保留。
- **2 个调用点暂时必须保留 `player.attack`**：Crescent Slash 和 Forest
  Blessing 的实际命中发生在激活后的后续 tick。当前 `player.attack` 在真正命中
  时重新经过 `AttackEntityEvent`，因此会尊重期间新出现的过场、节日和仪式锁。
  在 `WeaponSkillDamage` 没有“命中时重新检查攻击许可”的明确选项前，不应替换。
- **6 个调用点需要专门迁移策略**：技能有命中结果分支、同目标多段命中或
  “即使伤害被拒绝也施加状态”的原创语义。不能用机械替换处理。
- **0 个调用点因伤害计算、武器耐久、原版附魔或统计而必须永久保留
  `player.attack`**。

当前这 30 个调用点已全部迁入 `WeaponSkillDamage`。生产代码中的武器技能
Handler、Tracker、反击、投射物与中央 child 均不再直接调用
`Player.attack`；需要在实际命中 tick 保留取消语义的路径使用
`AttackGatePolicy.RESPECT_AT_IMPACT`。

## 两条路径实际做了什么

### `Player.attack`

本项目当前映射的 Minecraft/NeoForge `Player.attack` 还会执行：

1. `CommonHooks.onPlayerAttackTarget`，即 `AttackEntityEvent` 及取消结果。
2. `target.isAttackable()`、`skipAttackInteraction` 和可反弹投射物判断。
3. 原版蓄力、`CriticalHitEvent`、`SweepAttackEvent`、冲刺击退和横扫选区。
4. `PlayerSweepAttackMixin`；当前只阻止 Stardew 匕首产生原版横扫。
5. `setLastHurtMob`。
6. `ItemStack.hurtEnemy`、`postHurtEnemy` 和
   `EnchantmentHelper.doPostAttackEffects`。
7. `Stats.DAMAGE_DEALT`、原版攻击声音/粒子、0.1 饥饿消耗。
8. `resetAttackStrengthTicker`。

项目自己的 `AttackEntityEvent` 监听还会：

- 写入 `AttackTargetTracker`；
- 执行 Silver Foldback 左键逻辑；
- 尊重农场保护、过场、节日世界和沙漠 Galaxy Sword 仪式锁；
- 在矿洞攻击实体时连带打碎附近木桶。

### `WeaponSkillDamage.apply`

该路径会：

1. 绑定 `SkillContext` 和可选的释放时武器快照；
2. 使用 `playerAttack` DamageSource 调用 `target.hurt`；
3. 进入 `LivingDamageEvent.Pre/Post`；
4. 进入 `DamagePipeline`，保留 Stardew 武器伤害、暴击、精准、怪物防御、
   装备、职业和 Stardew 附魔；
5. 保留 `WeaponCombatEvents` 内按 skill id 执行的治疗、印记、延迟 Tracker、
   吸血、伤害数字、战斗经验、战利品和饰品命中回调。

该路径不会触发上一节列出的近战动作副作用。

## 全局判断

以下差异不构成这 30 个原创技能继续依赖 `player.attack` 的理由：

- 三类 Stardew 武器都声明为不可损坏，`hurtEnemy` 没有自定义耐久或技能逻辑。
- Stardew 暴击由 `DamagePipeline` 决定，不依赖原版跳劈暴击。
- Stardew 的 Bug Killer、Crusader、Vampiric 等效果已在
  `WeaponCombatEvents` 的 Pre/Post 阶段实现。
- 技能命中携带非 `normal` 的 `SkillContext`，`AttackTargetTracker` 不参与其
  主目标/横扫判定。它只会影响 `player.attack` 顺带产生、且已丢失技能上下文的
  原版横扫子命中。
- 明确选区的原创技能不应额外生成一次原版横扫；迁移到
  `WeaponSkillDamage` 会消除这类隐式旁路。

但仍需做一个项目级决策：Runtime 技能是否应继续触发“攻击实体时打碎矿洞木桶”
和第三方 `AttackEntityEvent` 副作用。建议默认把 Runtime 技能命中视为
**技能伤害而非一次新的玩家输入**，不触发木桶横扫；需要权限检查的技能通过
显式命中许可选项完成，而不是重新调用整个 `Player.attack`。

## 逐调用点分类

分类含义：

- **可安全改**：保留当前 handler 中命中前后代码，使用带
  `context.weaponSnapshot()` 的 `WeaponSkillDamage.apply` 即可保留原创语义。
- **必须暂留**：现有 API 缺少命中时攻击许可检查；在补齐前保留
  `player.attack`。
- **需专门选项**：需要 typed hit result、多段命中策略或明确的
  on-attempt/on-hit 状态策略。

| # | Handler / 调用点 | 分类 | 依据与迁移约束 |
|---:|---|---|---|
| 1 | `BoneFractureSkillHandler` | 可安全改 | Weakness、Slowness 和 fracture tracker 都由 handler 显式施加；不读取 vanilla melee 状态。 |
| 2 | `BurglarShankSkillHandler` | 可安全改 | 击杀奖励与未击杀 Weak Point 在 `LivingDamageEvent.Post` 按 skill id 处理，direct damage 路径会保留。 |
| 3 | `ClaymoreFoldbackSkillHandler` 初段 | 可安全改 | 初段只需要 Stardew 伤害管线；折返二段由独立 tracker/快照拥有。 |
| 4 | `CrescentSlashSkillHandler` | 必须暂留 | 命中在激活后第 3 tick；当前会在实际命中时重新经过攻击许可事件。先补 `respectAttackGateAtImpact`。 |
| 5 | `CrystalDaggerLayerSkillHandler` | 可安全改 | 瞬移、命中和加层均显式；层数不依赖 `player.attack` 返回副作用。 |
| 6 | `DarkSwordBloodDebtSkillHandler` | 可安全改 | Blood Debt 状态先启动，吸血由 `WeaponCombatEvents` 的技能伤害结果驱动。 |
| 7 | `DesperatePlunderSkillHandler` | 已专门迁移 | 忽略 `apply` 布尔返回；扣血后尝试命中，再读取目标死亡状态决定治疗，否则给予 Fury。 |
| 8 | `DragonBreathJudgementSkillHandler` | 可安全改 | 每个目标的 crit bonus 已写入 `SkillContext`；无需原版暴击或横扫。 |
| 9 | `DragonBreathThrustSkillHandler` | 可安全改 | 路径目标由 handler 明确选出；Vulnerable、stagger 和 dash tracker 均显式。direct damage 可避免路径命中再次横扫。 |
| 10 | `DragontoothShivStabSkillHandler` | 可安全改 | guaranteed crit 在上下文，冻结由 handler 显式施加。 |
| 11 | `DwarfRuneGuardSkillHandler` | 可安全改 | Shelter、slow、energy restore 和 guard tracker 均由 handler 拥有。 |
| 12 | `ForestBlessingSkillHandler` | 必须暂留 | 实际命中在激活后第 3 tick；与 Crescent 相同，先补实际命中时的攻击许可检查。 |
| 13 | `GalaxyDaggerStarleapSkillHandler` | 可安全改 | 释放前消费 mark，guaranteed crit 和伤害加成都在上下文；瞬移、冻结、表现显式。 |
| 14 | `GalaxyJudgementSkillHandler` | 可安全改 | 多目标主斩已有显式选区；Starfall 是独立 tracker。移除原版横扫更符合一次一目标的 authored loop。 |
| 15 | `HolySmiteSkillHandler` | 可安全改 | 成功伤害后的治疗与 dodge state 在 `LivingDamageEvent.Pre` 按 `holy_smite` 处理，direct damage 会进入同一分支。 |
| 16 | `InfinityDaggerSingularityBackstabSkillHandler` helper | 已专门迁移 | 一、二段继续共用 helper；基础段后仍先检查施法者/维度和目标存活，二段前显式清 i-frame，且不读取 `apply` 布尔值。 |
| 17 | `InsectDashSkillHandler` | 可安全改 | 多目标、stage、energy、dash 和终段 buff 均显式；不应让每个路径目标再触发原版横扫。 |
| 18 | `IronDirkThrustSkillHandler` | 可安全改 | 前后瞬移、朝向和短抗性显式；无 vanilla melee 结果依赖。 |
| 19 | `LavaKatanaBrandSkillHandler` | 可安全改 | 必须使用释放时快照并保留 `prepareRelease`/`discardPreparedRelease`；熔印由 Post hook 按 skill id 应用。 |
| 20 | `ShadowDaggerExecuteSkillHandler` 基础段 | 已专门迁移 | 是否进入处决段仍在基础伤害前按释放时生命值锁定；基础段不读取 `apply` 布尔值，也不改变后续处决判定。 |
| 21 | `ShadowDaggerExecuteSkillHandler` 处决追加段 | 已专门迁移 | 锁定 execute 后无条件追加；第二段仍先清 i-frame/hurtTime，使用独立 skill id 和同一释放武器快照。 |
| 22 | `StartrailRiftSkillHandler` | 可安全改 | 路径目标显式；命中后的 stack/energy/health 奖励按“选到目标”而非 vanilla hit side effect 执行。 |
| 23 | `TemperedQuenchSkillHandler` | 可安全改 | Quench 延迟爆破在 `LivingDamageEvent.Pre` 按成功伤害启动；direct damage 会保留。 |
| 24 | `TetanusStrikeSkillHandler` | 可安全改 | Vulnerable 明确要求在命中前施加，继续保持调用顺序即可。 |
| 25 | `TideReelSkillHandler` | 可安全改 | fish 状态、slow、拉拽和反馈均显式，不依赖原版击退或 sweep。 |
| 26 | `TreeBlessingSkillHandler` | 可安全改 | Shelter 与是否有目标无关；可选攻击只需要 Stardew 伤害管线。 |
| 27 | `WickedKrisNestBurstSkillHandler` | 已专门迁移 | `apply` 后无条件注入新毒层，保持 `ON_ATTEMPT`，不读取命中返回值。 |
| 28 | `WickedKrisVenomRippleSkillHandler` | 已专门迁移 | 每个目标先清 i-frame，`apply` 后无条件刷新毒，保持 `ON_ATTEMPT`。 |
| 29 | `WindSpireThrustSkillHandler` | 可安全改 | 瞬移、加速和 WindSpire tracker 均显式；无原版近战状态依赖。 |
| 30 | `YetiToothMarkSkillHandler` | 可安全改 | 印记与减速由 `LivingDamageEvent.Post` 按 skill id 和正伤害应用，direct damage 会进入同一分支。 |

## 审计提出的能力与最终取舍

不建议在 `WeaponSkillDamage` 中复制整个 `Player.attack`。只补原创技能真正需要的
边界：

1. `AttackGatePolicy`
   - `SKILL_DAMAGE`：默认；不重新发玩家输入事件。
   - `RESPECT_AT_IMPACT`：在延迟命中时检查项目/NeoForge 攻击许可，取消后不伤害。
2. 状态施加时机必须留在 handler 中明确表达：
   - `ON_ATTEMPT`：Wicked Kris 两条；
   - `ON_POSITIVE_DAMAGE`：Holy Smite、Tempered Quench、Yeti Mark 等现有
     Pre/Post hook；
   - `ON_KILL`：Desperate Plunder 和 Burglar Shank。

最终只新增了 `AttackGatePolicy`。没有引入泛化 `SkillHitResult` 或
`IFramePolicy`：现有原创技能不读取 `apply` 的布尔返回，死亡判断继续读取目标
权威状态；明确的多段技能继续在调用点保留独立 skill ID 和原 i-frame 清理顺序。

## 执行顺序（已完成）

1. 先迁移表中 22 个“可安全改”调用点，并逐条使用释放时武器快照。
2. 为 Crescent/Forest 增加 `RESPECT_AT_IMPACT` 后再迁移。
3. 最后单独迁移 Desperate、Infinity/Shadow 多段和 Wicked Kris；每条建立命中结果/
   状态时机契约测试。
4. 迁移期间禁止批量正则替换，禁止顺手改变伤害倍率、目标选区、i-frame、技能阶段
   或 VFX。

## 执行状态

安全批 A 已迁移至 `WeaponSkillDamage.apply(...)`，并显式传递
`context.weaponSnapshot()`：

- Bone Fracture、Burglar Shank、Claymore Foldback、Crystal Dagger Layer
- Dark Sword Blood Debt
- Dragon Breath Judgement、Dragon Breath Thrust、Dragontooth Shiv Stab
- Dwarf Rune Guard、Galaxy Dagger Starleap、Galaxy Judgement

这些 Handler 已删除本地 pending、`Player.attack` 和 `finally` 清理样板；
技能上下文构造、目标选择、i-frame、状态、表现和调用顺序保持在原位置。

三个语义例外也已单独迁移：

- Desperate Plunder 保留“扣血 → 尝试命中 → 检查目标死亡状态 → 治疗或 Fury”，
  不把 `apply`/`hurt` 的布尔值当作最终伤害结果。
- Wicked Kris Nest Burst 与 Venom Ripple 保持 `ON_ATTEMPT`：伤害尝试后
  无条件注毒或刷新毒；Venom Ripple 仍在每个目标命中前清零 i-frame。

两个原创多段技能也已逐段迁移，没有引入命中结果分支：

- Infinity Singularity Backstab 保留一、二段共用 helper、安全位移、基础段后的
  施法者/维度检查、目标存活检查，以及二段前的 i-frame 清理。
- Shadow Execute 保留释放前锁定 execute；基础段后不根据伤害返回值或目标存活
  再判定，锁定后仍无条件执行具有独立 skill id 的第二段，并先清 i-frame。

三组后续 tick / 受击触发的反应伤害也已迁移，并在实际命中时使用
`RESPECT_AT_IMPACT` 重新检查攻击许可：

- Silver Foldback 的初段、留位斩和返回斩继续保留原有状态退出、攻击、位移、
  冷却和表现顺序；现代路径使用 Foldback 保存的释放武器快照。
- Light Counter 先消费招架窗口并减伤，再以原攻击者为反击目标；动画通知和状态
  清理顺序不变。
- Templar Vow 的受击强反和到期轻斩共享激活时的释放武器快照；强反后的立即结束
  与到期轻斩后的 Shelter、挥手、冷却、客户端通知顺序不变。

兼容 overload 在没有历史快照时明确调用带 `AttackGatePolicy` 的兼容 damage
入口；现代 Runtime 路径均显式传递快照。所有分支均忽略 `apply` 布尔返回值。

本次没有引入泛化命中结果 API，也没有改变技能数值、表现或通知顺序。
