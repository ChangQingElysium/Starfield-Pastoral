package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Server-authoritative lifecycle for Steel Smallsword's original Light Counter.
 *
 * <p>The incoming-damage event remains responsible for consuming the parry and
 * performing the counterattack. This handler owns activation, cooldown, window
 * expiry, animation lock and cancellation cleanup.</p>
 */
public final class LightCounterSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int WINDOW_TICKS = 20;
    public static final int COUNTER_ANIM_TICKS = 8;
    public static final int INITIAL_RESISTANCE_TICKS = 7;
    public static final int INITIAL_RESISTANCE_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = WINDOW_TICKS;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(context.player().getUUID(), context.skillId())) {
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
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() ->
                context.player().addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE,
                        INITIAL_RESISTANCE_TICKS,
                        INITIAL_RESISTANCE_AMPLIFIER,
                        false,
                        false,
                        true
                ))
        );
        instance.initializeExecutionState(
                new LightCounterExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        WINDOW_TICKS,
                        weaponId,
                        context.weaponSnapshot()
                )
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
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

    /**
     * Atomically consumes only this caster's exact active Light Counter.
     * Cooldown and runtime termination remain owned by the skill runtime.
     */
    public static Optional<CounterActivation> consumeParry(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return Optional.empty();
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.LIGHT_COUNTER,
                LightCounterExecutionState.class
        ).flatMap(state -> state.consume(
                nowTick,
                player.level().dimension()
        ));
    }

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return instance.requireExecutionState(
                LightCounterExecutionState.class
        ).advance(
                context.nowTick(),
                context.player().level().dimension()
        );
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(LightCounterExecutionState.class)
                .ifPresent(LightCounterExecutionState::cancel);
    }

    public record CounterActivation(
            String weaponId,
            WeaponDamageSnapshot weaponSnapshot
    ) {}
}
