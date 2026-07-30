# 武器伤害入口审计（阶段 C）

日期：2026-07-30

## 结论

阶段 C 已完成：

- 生产代码中不存在 `hurt(..., 1.0F)` 管线哨兵；
- 武器技能的即时、延迟、多段、DOT、范围、反击、投射物和派生 child
  命中统一进入 `WeaponSkillDamage`；
- 棍棒普通横扫也使用同一快照感知入口，不再维护例外；
- 最终伤害仍只有一套算术：
  `WeaponCombatEvents -> DamageCalculator -> DamagePipeline`；
- 所有技能命中携带 `SkillContext`，所有延迟命中继承释放时
  `WeaponDamageSnapshot`；
- 统一入口在 `finally` 回收未被伤害事件消费的 pending context。

`WeaponDamageEntryContractTest` 和 `WeaponSentinelDamageAuditTest` 会阻止新的
原始一点伤害或绕过快照的技能入口进入生产代码。

## 入口语义

`WeaponSkillDamage` 不直接复制伤害公式。它负责：

1. 验证服务端施法者、目标所在维度和上下文有效期；
2. 解析显式快照，或从匹配的活动 Runtime 领取释放快照；
3. 绑定 `SkillContext + WeaponDamageSnapshot`；
4. 按需执行命中时攻击许可检查；
5. 用释放武器平均基础伤害与技能倍率形成有效的预减伤输入；
6. 触发 `LivingDamageEvent.Pre/Post`，由中央管线权威重算；
7. 无论命中是否被接受，都清理未消费上下文。

技能不能直接调用 `DamagePipeline.evaluate`，也不能重新读取当前主手来解释一次
已经释放的技能。

## 攻击许可策略

### `SKILL_DAMAGE`

默认策略。技能命中不是一次新的玩家输入，不重复触发
`AttackEntityEvent`。适用于即时技能、投射物、DOT、中央派生 child，以及原逻辑
不依赖命中时许可事件的延迟伤害。

### `RESPECT_AT_IMPACT`

原实现会在实际命中 tick 调用 `Player.attack` 的路径使用此策略。统一入口先绑定
pending，再调用 `CommonHooks.onPlayerAttackTarget`，最后伤害并在 `finally`
清理。这样技能自己的动画锁不会误拒绝命中，同时保留过场、节日、农场保护和
第三方可取消攻击事件的语义。

当前使用此策略的路径包括：

- Crescent Slash、Forest Blessing；
- Eternal Collapse、Rift Path、Femur Slam、Singularity Evolve、Starfall；
- Silver Foldback、Light Counter、Templar Vow；
- 其他原本在后续 tick 或受击回调中重新调用 `Player.attack` 的反应命中。

## 释放武器快照

现代 Runtime Handler 必须显式传 `context.weaponSnapshot()`。允许使用兼容
overload 的旧 Tracker 也只能在父 Runtime 仍存活、且 child 技能 ID 与父技能
一致时隐式领取快照。

以下状态必须自己保存快照：

- 父 Runtime 结束后仍存在的 DOT、领域和延迟爆发；
- 投射物或可保存的效果实体；
- child 技能 ID 与父技能不同的派生伤害；
- 可跨区块卸载/加载的实体。

投射物使用 Minecraft 1.21 `ItemStack.saveOptional/parseOptional` 保存完整
ItemStack，并同时保存 weapon ID。旧 NBT 缺字段时保持兼容，但现代生产调用不再
回退到当前手持武器。

本轮补齐的持久化链包括：

- Meowmere、Tide Anchor、Tempered Billet；
- Ice Spine、Elf Blade Leaf；
- Lava Katana burn、Wicked Kris poison；
- Eternal/Femur/Starfall 以及 Singularity/Rift 的延迟状态。

## 原创多段与状态时机

迁移没有把所有命中机械折叠成一种行为：

- Desperate Plunder 仍在尝试伤害后读取目标死亡状态，决定治疗或 Fury；
- Wicked Kris Nest/Ripple 仍是 `ON_ATTEMPT`，即伤害被拒绝也会注毒或刷新毒；
- Infinity Dagger 两段继续共用原 helper，保留目标存活、换维取消和第二段
  i-frame 规则；
- Shadow Dagger 在基础段前锁定 execute 条件，锁定后仍无条件追加独立 skill ID
  的第二段；
- 多目标技能逐目标调用统一入口，不共享一次 pending。

这些规则均有专门的源级契约测试，防止以后把原创机制误改成“只有正伤害才触发”
或折叠多段。

## 中央派生 child

`WeaponCombatEvents` 中的以下 child 只继承当前伤害已经解析出的
`damageWeaponSnapshot`：

- `crystal_dagger_burst`
- `singularity_followup`
- `ossified_mark_bonus`
- `galaxy_dagger_mark_bonus`
- `infinity_dagger_mark_bonus`
- `tide_mark_bonus`

中央 child 不得重新读取玩家主手，也不得本地复制 pending/cleanup 样板。

## 新增伤害入口规则

1. 即时 Handler：调用 `WeaponSkillDamage.apply` 并显式传
   `context.weaponSnapshot()`。
2. 延迟 Tracker：状态持有快照；若旧行为在命中 tick 重查攻击许可，显式使用
   `RESPECT_AT_IMPACT`。
3. 投射物/效果实体：构造时接收快照、NBT 持久化、命中时显式传入。
4. 多目标：每个目标独立调用统一入口。
5. 派生 child：只继承父伤害快照。
6. 禁止新增 `hurt(..., 1.0F)`、本地 pending/cleanup 样板或第二套伤害公式。
