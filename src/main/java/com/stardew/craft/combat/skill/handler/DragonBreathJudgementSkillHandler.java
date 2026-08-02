package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Dragontooth Cutlass's original judgement.
 */
public final class DragonBreathJudgementSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0D;
    public static final double MINIMUM_TARGET_DOT = 0.5D;
    public static final float CRITICAL_CHANCE_PER_EXTRA_STACK = 0.04F;
    public static final int MAXIMUM_STACK_REFUND = 5;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        return DragonBreathTracker.canCastMajor(context.player())
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        List<LivingEntity> targets = SkillTargeting.findTargetsInArc(
                context.player(),
                TARGET_RANGE,
                MINIMUM_TARGET_DOT
        );
        targets.removeIf(target -> !target.isAlive());
        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );

        int consumedStacks =
                DragonBreathTracker.consumeForMajor(context.player());
        if (consumedStacks < DragonBreathTracker.MAJOR_THRESHOLD) {
            throw new IllegalStateException(
                    "Validated Dragon Breath stacks are no longer available"
            );
        }
        instance.registerBeginFailureCleanup(() ->
                DragonBreathTracker.setStacks(
                        context.player(),
                        consumedStacks
                )
        );

        float criticalChanceBonus =
                criticalChanceBonus(consumedStacks);
        DragonBreathJudgementExecutionState executionState =
                new DragonBreathJudgementExecutionState();
        instance.initializeExecutionState(executionState);
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        instance.registerCommittedEffect(() -> {
            for (LivingEntity target : targets) {
                attackTarget(context, target, criticalChanceBonus);
            }
            int refund = executionState.settleRefund();
            if (refund > 0) {
                DragonBreathTracker.addStacks(
                        context.player(),
                        refund
                );
            }
            WeaponSkillAnimationDispatcher.sendSkillAnim(
                    context.player(),
                    weaponId,
                    skillId,
                    ANIMATION_TICKS
            );
            WeaponSkillAnimationLock.setLock(
                    context.player(),
                    context.nowTick(),
                    ANIMATION_TICKS
            );
        });
    }

    public static boolean recordAppliedHit(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DRAGON_BREATH_JUDGEMENT,
                DragonBreathJudgementExecutionState.class
        ).map(state -> state.recordAppliedTarget(target.getUUID()))
                .orElse(false);
    }

    static int extraStacks(int consumedStacks) {
        return Math.max(
                0,
                consumedStacks - DragonBreathTracker.MAJOR_THRESHOLD
        );
    }

    static float criticalChanceBonus(int consumedStacks) {
        return extraStacks(consumedStacks)
                * CRITICAL_CHANCE_PER_EXTRA_STACK;
    }

    static int refundForTargetCount(int targetCount) {
        return Math.min(
                MAXIMUM_STACK_REFUND,
                Math.max(0, targetCount)
        );
    }

    static SkillContext createHitContext(
            WeaponSkillData skillData,
            float criticalChanceBonus
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .critChanceBonus(
                        Math.max(0.0F, criticalChanceBonus)
                )
                .build();
    }

    private static void attackTarget(
            SkillExecutionContext context,
            LivingEntity target,
            float criticalChanceBonus
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(
                        context.skillData(),
                        criticalChanceBonus
                ),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
        );
    }
}
