package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Server-authoritative lifecycle for Dwarf Dagger's original Ley Line Rush.
 */
public final class DwarfDaggerRushSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 100;
    public static final int SPEED_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = 8;
    public static final int THRUST_COOLDOWN_REFRESH_TICKS = 0;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        boolean coolingDown = WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        );
        if (coolingDown) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        DwarfDaggerRushExecutionState executionState =
                new DwarfDaggerRushExecutionState(
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS
                );
        instance.initializeExecutionState(executionState);

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> {
            executionState.start(
                    context.player(),
                    ACTIVE_DURATION_TICKS
            );
            context.player().addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    ACTIVE_DURATION_TICKS,
                    SPEED_AMPLIFIER,
                    false,
                    true,
                    true
            ));
        });

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

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return instance.requireExecutionState(
                DwarfDaggerRushExecutionState.class
        ).advance(context);
    }

    /** Reads only this caster's exact active Ley Line Rush execution. */
    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DWARF_DAGGER_RUSH,
                DwarfDaggerRushExecutionState.class
        ).map(state -> state.isActive(player, nowTick))
                .orElse(false);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(DwarfDaggerRushExecutionState.class)
                .ifPresent(state -> state.cancel(context.player()));
    }
}
