package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.DarkSwordBloodMoonPayload;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.skill.DarkSwordBloodMoonTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Dark Sword's original Blood Moon Harvest.
 */
public final class DarkSwordBloodMoonSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int ACTIVE_DURATION_TICKS = 80;
    public static final int BURN_INTERVAL_TICKS = 10;
    public static final int PRESENTATION_NOTIFICATION_TICKS = 1;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.COOLDOWN
            );
        }
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || DarkSwordBloodMoonTracker.hasState(
                context.player().getUUID()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Blood Moon energy is no longer available"
            );
        }
        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Blood Moon energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        DarkSwordBloodMoonTracker.start(
                context.player(),
                context.nowTick(),
                ACTIVE_DURATION_TICKS,
                BURN_INTERVAL_TICKS,
                WeaponStats.fromItemStack(
                        context.weapon()
                ).getAverageDamage(),
                context.weaponSnapshot()
        );
        PacketDistributor.sendToPlayer(
                context.player(),
                new DarkSwordBloodMoonPayload(
                        true,
                        ACTIVE_DURATION_TICKS
                )
        );

        // The authored skill had no attack lock or held-item motion, but its
        // one-shot local cast effect still needs a server-authored notification.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                PRESENTATION_NOTIFICATION_TICKS
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
        if (!DarkSwordBloodMoonTracker.isBoundToCurrentContext(
                context.player()
        )) {
            return SkillTickResult.CANCEL;
        }
        DarkSwordBloodMoonTracker.tick(
                context.player(),
                context.nowTick()
        );
        return DarkSwordBloodMoonTracker.hasState(
                context.player().getUUID()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        boolean stateWasActive =
                DarkSwordBloodMoonTracker.cancel(context.player());
        boolean commitCooldown = shouldCommitCooldown(
                reason,
                stateWasActive
        );
        if (commitCooldown) {
            WeaponSkillCooldowns.setCooldown(
                    context.player(),
                    context.weaponId().getPath(),
                    context.skillData().getId(),
                    context.player().level().getGameTime(),
                    context.skillData().getCooldown() * 20
            );
        }
        if (commitCooldown
                && reason != SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new DarkSwordBloodMoonPayload(false, 0)
            );
        }
    }

    static boolean shouldCommitCooldown(
            SkillInstance.EndReason reason,
            boolean stateWasActive
    ) {
        return stateWasActive
                || reason == SkillInstance.EndReason.COMPLETED
                || reason == SkillInstance.EndReason.CASTER_UNAVAILABLE;
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode
                || freeEnergyBlessing
                || currentEnergy >= ENERGY_COST;
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }
}
