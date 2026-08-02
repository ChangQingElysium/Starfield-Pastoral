package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative lifecycle for Infinity Blade's original Singularity
 * Evolution.
 */
public final class SingularityEvolveSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 20;
    public static final double EFFECT_RADIUS = 4.0D;
    public static final float EXPLOSION_DAMAGE_MULTIPLIER = 1.6F;
    public static final float SLASH_DAMAGE_MULTIPLIER = 1.2F;
    public static final double DASH_DISTANCE = 5.0D;
    public static final int DASH_DURATION_TICKS = 5;
    public static final double PULL_STRENGTH = 0.15D;
    public static final double SLASH_PATH_HALF_WIDTH = 0.9D;
    public static final float RIFT_LENGTH = 3.0F;
    public static final int RIFT_DURATION_TICKS = 40;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int HIT_SINGULARITY_RESTORE = 4;
    public static final float HIT_ENERGY_RESTORE = 10.0F;
    public static final float HIT_HEALTH_RESTORE = 5.0F;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.COOLDOWN
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        boolean evolved = evolvedForStacks(
                SingularityTracker.getStacks(context.player())
        );

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        SingularityEvolveExecutionState executionState =
                new SingularityEvolveExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS,
                        skillId,
                        evolved,
                        context.weaponSnapshot()
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() -> {
            executionState.start(
                    context.player(),
                    ACTIVE_DURATION_TICKS
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

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return instance.requireExecutionState(
                SingularityEvolveExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(SingularityEvolveExecutionState.class)
                .ifPresent(state -> state.cancel(context.player()));
    }

    public static boolean settleAppliedHitRewards(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        boolean claimed = WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.SINGULARITY_EVOLVE,
                SingularityEvolveExecutionState.class
        ).map(SingularityEvolveExecutionState::claimHitRewards)
                .orElse(false);
        if (!claimed) {
            return false;
        }
        SingularityTracker.addStacks(
                player,
                HIT_SINGULARITY_RESTORE
        );
        PlayerStardewDataAPI.restoreEnergy(
                player,
                HIT_ENERGY_RESTORE
        );
        CombatHealing.heal(player, HIT_HEALTH_RESTORE);
        return true;
    }

    static boolean evolvedForStacks(int stacks) {
        return Math.max(0, stacks)
                >= SingularityTracker.EVOLVE_THRESHOLD;
    }
}
