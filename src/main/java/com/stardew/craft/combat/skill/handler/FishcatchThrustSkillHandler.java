package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Broken Trident's original Fish Catch Thrust.
 *
 * <p>The execution state retains the authored three-strike cadence and per-hit
 * Fish Catch outcome.</p>
 */
public final class FishcatchThrustSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 2.5;
    public static final int DAMAGE_RESISTANCE_TICKS = 5;
    public static final int DAMAGE_RESISTANCE_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = 18;
    public static final int STRIKE_COUNT = 3;
    public static final int STRIKE_INTERVAL_TICKS = 3;
    public static final int FISH_CATCH_DURATION_TICKS = 100;
    public static final float FISH_CATCH_DAMAGE_BONUS = 0.10F;
    public static final int FISH_CATCH_SLOW_AMPLIFIER = 0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final double REACQUIRE_RANGE = 2.5;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        return findInitialTarget(context) == null
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = findInitialTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Fish Catch Thrust target disappeared after validation"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        instance.setTargetEntityIds(List.of(target.getId()));
        instance.initializeExecutionState(
                new FishcatchThrustExecutionState(
                        context.nowTick(),
                        target.getUUID()
                )
        );

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() ->
                context.player().addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE,
                        DAMAGE_RESISTANCE_TICKS,
                        DAMAGE_RESISTANCE_AMPLIFIER,
                        false,
                        false,
                        true
                ))
        );
        // Preserve the old server notification order.
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
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return instance.requireExecutionState(
                FishcatchThrustExecutionState.class
        ).advance(context);
    }

    /** Settles one exact positive strike while its execution is active. */
    public static boolean onAppliedHit(
            ServerPlayer player,
            LivingEntity target,
            long nowTick
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.FISHCATCH_THRUST,
                FishcatchThrustExecutionState.class
        ).map(state -> state.recordAppliedHit(player, target, nowTick))
                .orElse(false);
    }

    private static LivingEntity findInitialTarget(SkillExecutionContext context) {
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                INITIAL_TARGET_RANGE
        );
    }
}
