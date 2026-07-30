package com.stardew.craft.combat.skill.runtime;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class SkillTargeting {
    private SkillTargeting() {}

    /**
     * Exact extraction of the legacy line target selection used by Forest Blessing.
     */
    public static LivingEntity findTargetEntity(Player player, double range) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eyePosition.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eyePosition,
                end,
                box,
                entity -> entity instanceof LivingEntity
                        && entity.isPickable()
                        && entity != player
        );
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }

    /**
     * Exact extraction of the legacy forward-volume nearest-target selection.
     */
    public static LivingEntity findNearestTargetInFront(Player player, double range) {
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
            double distance = entity.getEyePosition().subtract(origin).lengthSqr();
            if (distance <= range * range && distance < bestDistance) {
                bestDistance = distance;
                closest = entity;
            }
        }
        return closest;
    }

    /**
     * Exact extraction of the legacy forward-arc selection used by Crescent Slash.
     */
    public static List<LivingEntity> findTargetsInArc(Player player, double range, double minimumDot) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB box = player.getBoundingBox().inflate(range, range * 0.75, range);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable() && entity != player
        );

        targets.removeIf(entity -> {
            Vec3 to = entity.getEyePosition().subtract(origin);
            if (to.lengthSqr() > range * range) {
                return true;
            }
            return to.normalize().dot(look) < minimumDot;
        });
        targets.sort((first, second) ->
                Double.compare(first.distanceToSqr(player), second.distanceToSqr(player)));
        return targets;
    }
}
