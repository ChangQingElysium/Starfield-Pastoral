package com.stardew.craft.combat.skill.handler;

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
 * Server-authoritative lifecycle for Iron Edge's original Steel Spine Fury.
 *
 * <p>The incoming-damage event charges the stance and the normal-attack event
 * consumes its stored strike. This handler owns activation, expiry ticking and
 * cancellation cleanup. Cooldown remains deferred until the stance is charged
 * or expires, matching the authored behavior.</p>
 */
public final class SteelSpineFurySkillHandler implements RuntimeWeaponSkillHandler {
    public static final int STANCE_DURATION_TICKS = 80;
    static final int MAX_BONUS_DAMAGE = 12;
    static final float BONUS_RATIO = 0.40F;
    static final float FALLBACK_MULTIPLIER = 1.40F;

    public record AttackBoost(
            boolean strong,
            int bonusDamage,
            float damageMultiplier
    ) {}

    public record AttackReservation(
            AttackBoost boost,
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
        boolean coolingDown = WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        );
        return coolingDown
                ? SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        DeferredSkillCooldown cooldown = WeaponSkillRuntime.deferCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        SteelSpineFuryExecutionState executionState =
                new SteelSpineFuryExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        STANCE_DURATION_TICKS,
                        cooldown
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.start(
                        context.player(),
                        STANCE_DURATION_TICKS
                )
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    /** Charges only this caster's exact active Steel Spine execution. */
    public static void onDamageTaken(
            ServerPlayer player,
            long nowTick,
            int stardewDamage
    ) {
        if (player == null || stardewDamage <= 0) {
            return;
        }
        WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY,
                SteelSpineFuryExecutionState.class
        ).ifPresent(state -> state.onDamageTaken(
                player,
                nowTick,
                stardewDamage
        ));
    }

    /** Consumes the exact execution's one ready strong or fallback strike. */
    public static AttackBoost consumeAttack(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return null;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY,
                SteelSpineFuryExecutionState.class
        ).map(state -> state.consumeAttack(player, nowTick))
                .orElse(null);
    }

    /** Reserves the charged strike until the exact hurt call reaches Pre. */
    public static AttackReservation reserveAttack(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return null;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY,
                SteelSpineFuryExecutionState.class
        ).map(state -> state.reserveAttack(player, nowTick))
                .orElse(null);
    }

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return instance.requireExecutionState(
                SteelSpineFuryExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(SteelSpineFuryExecutionState.class)
                .ifPresent(state -> state.interrupt(
                        context.player(),
                        context.nowTick()
                ));
    }
}
