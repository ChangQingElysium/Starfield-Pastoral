package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.CarvingKnifeThrustTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Carving Knife's original Carving Thrust.
 */
public final class CarvingThrustSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 2.5;
    public static final int DAMAGE_RESISTANCE_TICKS = 5;
    public static final int DAMAGE_RESISTANCE_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = 18;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(context.player().getUUID(), context.skillId())) {
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
            throw new IllegalStateException("Carving Thrust target disappeared after validation");
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        instance.setTargetEntityIds(List.of(target.getId()));

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        context.player().addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                DAMAGE_RESISTANCE_TICKS,
                DAMAGE_RESISTANCE_AMPLIFIER,
                false,
                false,
                true
        ));
        CarvingKnifeThrustTracker.start(
                context.player(),
                context.nowTick(),
                target,
                weaponId,
                skillId
        );

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
        return CarvingKnifeThrustTracker.isActive(context.player().getUUID())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        CarvingKnifeThrustTracker.removePlayer(context.player().getUUID());
    }

    private static LivingEntity findInitialTarget(SkillExecutionContext context) {
        Vec3 origin = context.player().getEyePosition();
        Vec3 look = context.player().getLookAngle().normalize();
        AABB box = context.player().getBoundingBox()
                .expandTowards(look.scale(INITIAL_TARGET_RANGE))
                .inflate(1.0D, 1.0D, 1.0D);

        LivingEntity closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : context.player().level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                candidate -> candidate.isPickable() && candidate != context.player()
        )) {
            double distance = entity.getEyePosition().subtract(origin).lengthSqr();
            if (distance <= INITIAL_TARGET_RANGE * INITIAL_TARGET_RANGE
                    && distance < bestDistance) {
                bestDistance = distance;
                closest = entity;
            }
        }
        return closest;
    }
}
