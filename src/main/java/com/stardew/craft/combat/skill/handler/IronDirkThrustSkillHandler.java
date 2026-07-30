package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Iron Dirk's original Shadow Thrust.
 */
public final class IronDirkThrustSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 7.0;
    public static final double BEHIND_DISTANCE = 3.0;
    public static final float CRITICAL_CHANCE_BONUS = 0.10F;
    public static final int RESISTANCE_DURATION_TICKS = 5;
    public static final int RESISTANCE_AMPLIFIER = 0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

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
        return findNearestTargetEntityInFront(context.player(), TARGET_RANGE) == null
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        LivingEntity target = findNearestTargetEntityInFront(
                context.player(),
                TARGET_RANGE
        );
        if (target == null) {
            throw new IllegalStateException("Validated Iron Dirk target is no longer available");
        }
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
                RESISTANCE_DURATION_TICKS,
                RESISTANCE_AMPLIFIER,
                false,
                false,
                true
        ));

        Vec3 frontPosition = getFrontPosition(target, context.player());
        teleportPlayer(context.player(), frontPosition);
        faceTarget(context.player(), target);

        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(context.skillData()),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
        );

        Vec3 behindPosition = getBehindPosition(
                target,
                context.player(),
                BEHIND_DISTANCE
        );
        teleportPlayer(context.player(), behindPosition);
        faceTarget(context.player(), target);

        // Preserve legacy synchronization order: animation packet precedes lock.
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

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .critChanceBonus(CRITICAL_CHANCE_BONUS)
                .build();
    }

    @SuppressWarnings("null")
    private static LivingEntity findNearestTargetEntityInFront(
            Player player,
            double range
    ) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB box = player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(1.0D, 1.0D, 1.0D);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable() && entity != player
        );

        LivingEntity closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : targets) {
            Vec3 toTarget = entity.getEyePosition().subtract(origin);
            double distance = toTarget.lengthSqr();
            if (distance <= range * range && distance < bestDistance) {
                bestDistance = distance;
                closest = entity;
            }
        }
        return closest;
    }

    private static Vec3 getFrontPosition(LivingEntity target, Player player) {
        Vec3 targetPosition = target.position();
        Vec3 direction = player.position().subtract(targetPosition);
        if (direction.horizontalDistanceSqr() < 0.5) {
            direction = target.getLookAngle().scale(-1);
        }

        double offsetDistance = target.getBbWidth() * 0.5 + 0.8;
        Vec3 offset = new Vec3(direction.x, 0, direction.z)
                .normalize()
                .scale(offsetDistance);
        Vec3 desired = new Vec3(
                targetPosition.x + offset.x,
                target.getY(),
                targetPosition.z + offset.z
        );
        return findSafePositionAroundTarget(player, target, desired, offset);
    }

    private static Vec3 getBehindPosition(
            LivingEntity target,
            Player player,
            double distance
    ) {
        Vec3 targetPosition = target.position();
        Vec3 targetLook = target.getLookAngle();
        Vec3 direction = new Vec3(targetLook.x, 0, targetLook.z);
        if (direction.lengthSqr() < 1.0E-6) {
            Vec3 fallback = targetPosition.subtract(player.position());
            direction = new Vec3(fallback.x, 0, fallback.z);
        }
        if (direction.lengthSqr() < 1.0E-6) {
            direction = new Vec3(0, 0, 1);
        }

        double offsetDistance = distance + target.getBbWidth() * 0.5;
        Vec3 offset = direction.normalize().scale(-offsetDistance);
        Vec3 desired = new Vec3(
                targetPosition.x + offset.x,
                target.getY(),
                targetPosition.z + offset.z
        );
        return findSafePositionAroundTarget(player, target, desired, offset);
    }

    private static Vec3 findSafePositionAroundTarget(
            Player player,
            LivingEntity target,
            Vec3 desired,
            Vec3 offset
    ) {
        Vec3 safe = findSafePosition(player, desired);
        if (safe != null) {
            return safe;
        }
        for (int index = 1; index <= 4; index++) {
            double angle = index * 0.4;
            Vec3 safePositive = findRotatedSafePosition(
                    player,
                    target,
                    offset,
                    angle
            );
            if (safePositive != null) {
                return safePositive;
            }
            Vec3 safeNegative = findRotatedSafePosition(
                    player,
                    target,
                    offset,
                    -angle
            );
            if (safeNegative != null) {
                return safeNegative;
            }
        }
        return desired;
    }

    private static Vec3 findRotatedSafePosition(
            Player player,
            LivingEntity target,
            Vec3 offset,
            double angle
    ) {
        Vec3 rotatedOffset = rotateVector(offset, angle);
        Vec3 targetPosition = target.position();
        Vec3 desired = new Vec3(
                targetPosition.x + rotatedOffset.x,
                target.getY(),
                targetPosition.z + rotatedOffset.z
        );
        return findSafePosition(player, desired);
    }

    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        AABB box = player.getBoundingBox().move(
                desired.x - player.getX(),
                desired.y - player.getY(),
                desired.z - player.getZ()
        );
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0, 0.25, 0);
        AABB raisedBox = player.getBoundingBox().move(
                raised.x - player.getX(),
                raised.y - player.getY(),
                raised.z - player.getZ()
        );
        return player.level().noCollision(player, raisedBox) ? raised : null;
    }

    static Vec3 rotateVector(Vec3 vector, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                vector.x * cosine - vector.z * sine,
                vector.y,
                vector.x * sine + vector.z * cosine
        );
    }

    private static void faceTarget(Player player, LivingEntity target) {
        Vec3 toTarget = target.position().subtract(player.position());
        if (toTarget.horizontalDistanceSqr() <= 0.01) {
            return;
        }
        float yaw = (float) (
                Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI)
        );
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    private static void teleportPlayer(Player player, Vec3 position) {
        player.teleportTo(position.x, position.y, position.z);
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
    }
}
