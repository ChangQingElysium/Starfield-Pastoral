package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative lifecycle for Insect Head's original Compound Eye Stance.
 */
public final class InsectEyeStanceSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 30;
    public static final int ANIMATION_TICKS = 1;
    public static final float DAMAGE_MULTIPLIER = 1.05F;

    public record AttackReservation(
            SkillContext skillContext,
            Runnable commit,
            Runnable release
    ) {
    }

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        return WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )
                ? SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        DeferredSkillCooldown cooldown = WeaponSkillRuntime.deferCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        InsectEyeStanceExecutionState executionState =
                new InsectEyeStanceExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS,
                        skillId,
                        cooldown
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.start(
                        context.player(),
                        ACTIVE_DURATION_TICKS
                )
        );

        // The authored stance has a one-tick notification and no animation lock.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    /** Consumes only this caster's exact active Compound Eye execution. */
    public static SkillContext consumeAttack(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return null;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE,
                InsectEyeStanceExecutionState.class
        ).map(state -> state.consumeAttack(player, nowTick))
                .orElse(null);
    }

    /** Reserves the first-hit token until the exact hurt call reaches Pre. */
    public static AttackReservation reserveAttack(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return null;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE,
                InsectEyeStanceExecutionState.class
        ).map(state -> state.reserveAttack(player, nowTick))
                .orElse(null);
    }

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return instance.requireExecutionState(
                InsectEyeStanceExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(InsectEyeStanceExecutionState.class)
                .ifPresent(state -> state.cancel(
                        context.player(),
                        context.player().level().getGameTime()
                ));
    }
}
