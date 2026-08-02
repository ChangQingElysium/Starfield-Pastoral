package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative lifecycle for Templar Blade's original counter vow.
 */
public final class TemplarVowSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 40;
    public static final int ANIMATION_TICKS = 40;
    public static final double COUNTER_TARGET_RANGE = 4.0D;
    public static final float COUNTER_DAMAGE_MULTIPLIER = 1.10F;
    public static final float EXPIRE_SLASH_DAMAGE_MULTIPLIER = 0.80F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int EXPIRE_SHELTER_DURATION_TICKS = 40;
    public static final int EXPIRE_SHELTER_AMPLIFIER = 0;

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
        return WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
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
        TemplarVowExecutionState executionState =
                new TemplarVowExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS,
                        context.weaponSnapshot(),
                        cooldown
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.start(
                        context.player(),
                        ACTIVE_DURATION_TICKS
                )
        );
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

    public static Optional<CounterActivation> consumeCounter(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return Optional.empty();
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.TEMPLAR_VOW,
                TemplarVowExecutionState.class
        ).flatMap(state -> state.consumeCounter(
                nowTick,
                player.level().dimension()
        ));
    }

    public static void finishCounter(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return;
        }
        WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.TEMPLAR_VOW,
                TemplarVowExecutionState.class
        ).ifPresent(state -> state.finishCounter(player, nowTick));
    }

    public static SkillContext createStrikeContext(float damageMultiplier) {
        return SkillContext.builder()
                .skillId("templar_vow")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return instance.requireExecutionState(
                TemplarVowExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(TemplarVowExecutionState.class)
                .ifPresent(state -> state.cancel(
                        context.player(),
                        context.player().level().getGameTime(),
                        reason != SkillInstance.EndReason.CASTER_UNAVAILABLE
                ));
    }

    public record CounterActivation(WeaponDamageSnapshot weaponSnapshot) {}
}
