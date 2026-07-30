package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.OssifiedExecutionTracker;
import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Ossified Blade's original execution circle.
 */
public final class OssifiedExecutionSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 6.0;
    public static final float ENERGY_COST = 10.0F;
    public static final float CIRCLE_RADIUS = 4.0F;
    public static final int DURATION_TICKS = 60;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || OssifiedExecutionTracker.isActive(
                context.player(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
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
        LivingEntity target = findMarkedTarget(context);
        if (target == null) {
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
        LivingEntity target = findMarkedTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Ossified Execution target is no longer marked"
            );
        }
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Ossified Execution energy is no longer available"
            );
        }
        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Ossified Execution energy payment failed"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        OssifiedExecutionTracker.start(
                context.player(),
                target,
                context.nowTick(),
                CIRCLE_RADIUS,
                DURATION_TICKS,
                context.weaponSnapshot()
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
        if (context.nowTick() >= instance.startGameTick() + DURATION_TICKS) {
            return SkillTickResult.COMPLETE;
        }
        return OssifiedExecutionTracker.isActive(
                context.player(),
                context.nowTick()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.CANCEL;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        OssifiedExecutionTracker.stop(context.player());
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

    private static LivingEntity findMarkedTarget(SkillExecutionContext context) {
        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        return target != null && OssifiedMarkTracker.isMarkedBy(
                target,
                context.player(),
                context.nowTick()
        )
                ? target
                : null;
    }
}
