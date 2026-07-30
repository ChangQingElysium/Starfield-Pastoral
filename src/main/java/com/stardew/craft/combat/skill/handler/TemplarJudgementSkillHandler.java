package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.TemplarJudgementTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Templar Blade's original judgement.
 */
public final class TemplarJudgementSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RADIUS = 6.0D;
    public static final float ENERGY_COST = 10.0F;

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
        ) || TemplarJudgementTracker.hasState(context.player().getUUID())) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (findTargets(context).isEmpty() || !canPayEnergy(context)) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        List<LivingEntity> targets = findTargets(context);
        if (targets.isEmpty()) {
            throw new IllegalStateException(
                    "Validated Templar Judgement targets are no longer available"
            );
        }
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Templar Judgement energy is no longer available"
            );
        }
        if (!PlayerStardewDataAPI.consumeEnergy(
                context.player(),
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Templar Judgement energy payment failed"
            );
        }

        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        TemplarJudgementTracker.start(
                context.player(),
                context.nowTick(),
                TemplarJudgementTracker.DURATION_TICKS,
                targets
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
        TemplarJudgementTracker.tick(context.player(), context.nowTick());
        return TemplarJudgementTracker.hasState(context.player().getUUID())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        TemplarJudgementTracker.removePlayer(context.player().getUUID());
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean freeEnergyBlessing
    ) {
        return freeEnergyBlessing || currentEnergy >= ENERGY_COST;
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }

    @SuppressWarnings("null")
    private static List<LivingEntity> findTargets(
            SkillExecutionContext context
    ) {
        Vec3 origin = context.player().position();
        AABB box = context.player().getBoundingBox().inflate(
                TARGET_RADIUS,
                TARGET_RADIUS * 0.75D,
                TARGET_RADIUS
        );
        return context.player().level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable()
                        && entity != context.player()
                        && entity.distanceToSqr(
                                origin.x,
                                origin.y,
                                origin.z
                        ) <= TARGET_RADIUS * TARGET_RADIUS
        );
    }
}
