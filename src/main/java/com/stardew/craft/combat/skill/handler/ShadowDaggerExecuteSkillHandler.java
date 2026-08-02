package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Shadow Dagger's original execute strike.
 */
public final class ShadowDaggerExecuteSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final float EXECUTE_HEALTH_RATIO = 0.30F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        ) == null
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Shadow Dagger target is no longer available"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        boolean execute = target.getHealth() <= target.getMaxHealth() * EXECUTE_HEALTH_RATIO;
        int baseCooldown = context.skillData().getCooldown() * 20;
        int appliedCooldown = execute ? baseCooldown : Math.max(1, baseCooldown / 2);
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                appliedCooldown
        );
        instance.initializeExecutionState(new State(target.getUUID(), execute));

        instance.registerCommittedEffect(() -> {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createHitContext(context.skillData()),
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
        });

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
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .build();
    }

    static SkillContext createExecuteBonusContext() {
        return SkillContext.builder()
                .skillId("shadow_dagger_execute_bonus")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(1.0F)
                .build();
    }

    /** Emits the derived execute damage only after the root slash applied. */
    public static boolean onAppliedRootHit(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || target == null || weaponSnapshot == null) {
            return false;
        }
        State state = WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.SHADOW_DAGGER_EXECUTE,
                State.class
        ).orElse(null);
        if (state == null || !state.consumeBonus(target.getUUID())) {
            return false;
        }
        WeaponSkillDamage.apply(
                player,
                target,
                createExecuteBonusContext(),
                weaponSnapshot,
                nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
        return true;
    }

    private static final class State implements SkillInstance.ExecutionState {
        private final UUID targetId;
        private final boolean executeArmed;
        private boolean bonusConsumed;

        private State(UUID targetId, boolean executeArmed) {
            this.targetId = targetId;
            this.executeArmed = executeArmed;
        }

        private boolean consumeBonus(UUID appliedTargetId) {
            if (!executeArmed
                    || bonusConsumed
                    || !targetId.equals(appliedTargetId)) {
                return false;
            }
            bonusConsumed = true;
            return true;
        }
    }

}
