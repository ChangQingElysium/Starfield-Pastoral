package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.ElfBladeMarkTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Elf Blade's original Moonlit Leaf Blades.
 */
public final class ElfBladeLeafSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 100;
    public static final int ANIMATION_TICKS = 8;
    public static final int LEAF_COUNT = 3;
    public static final int MARK_DURATION_TICKS = 140;
    public static final int MARK_STACKS_PER_HIT = 1;

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
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        float damageMultiplier = context.skillData().getDamagePercent() / 100.0F;
        DeferredSkillCooldown cooldown = WeaponSkillRuntime.deferCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        ElfBladeLeafExecutionState executionState =
                new ElfBladeLeafExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS,
                        cooldown
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.start(
                        context,
                        damageMultiplier,
                        skillId
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

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return instance.requireExecutionState(
                ElfBladeLeafExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        if (shouldDiscardLeaves(reason)) {
            instance.executionState(ElfBladeLeafExecutionState.class)
                    .ifPresent(state -> state.cancel(
                            context.player(),
                            context.player().level().getGameTime()
                    ));
        }
    }

    public static void fireLeafAtTarget(
            ServerPlayer player,
            LivingEntity target,
            long nowTick
    ) {
        if (player == null || target == null) {
            return;
        }
        WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.ELF_BLADE_LEAF,
                ElfBladeLeafExecutionState.class
        ).ifPresent(state -> state.fireLeafAtTarget(
                player,
                target,
                nowTick
        ));
    }

    /** Adds one leaf mark stack after exact positive projectile damage. */
    public static void applyLeafMark(
            ServerPlayer player,
            LivingEntity target,
            long nowTick
    ) {
        ElfBladeMarkTracker.apply(
                target,
                player,
                nowTick,
                MARK_DURATION_TICKS,
                MARK_STACKS_PER_HIT
        );
    }

    static boolean shouldDiscardLeaves(
            SkillInstance.EndReason reason
    ) {
        return reason != SkillInstance.EndReason.COMPLETED;
    }
}
