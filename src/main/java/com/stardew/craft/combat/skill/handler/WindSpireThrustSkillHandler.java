package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.WindSpirePayload;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WindSpireTracker;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative extraction of Wind Spire's original Wind Spire Thrust.
 */
public final class WindSpireThrustSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 6.0;
    public static final double NO_TARGET_DASH_DISTANCE = 4.0;
    public static final int DASH_DURATION_TICKS = 5;
    public static final int GALE_DURATION_TICKS = 60;
    public static final int SPEED_AMPLIFIER = 0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
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
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            throw new IllegalStateException(
                    "Validated Wind Spire movement is now locked"
            );
        }
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );

        LivingEntity target = findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
        }
        instance.registerCommittedEffect(() -> {
            if (target == null) {
                DashMovementTracker.start(
                        context.player(),
                        context.nowTick(),
                        computeDashEnd(
                                context.player(),
                                NO_TARGET_DASH_DISTANCE
                        ),
                        DASH_DURATION_TICKS
                );
            } else {
                teleportToTargetFront(context.player(), target);
                faceTarget(context.player(), target);
                WeaponSkillDamage.apply(
                        context.player(),
                        target,
                        createHitContext(context.skillData()),
                        context.weaponSnapshot(),
                        context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                );
            }
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

    /** Grants Gale only after the exact thrust deals positive damage. */
    public static void grantGale(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                ModMobEffects.SPEED,
                GALE_DURATION_TICKS,
                SPEED_AMPLIFIER,
                false,
                true,
                true
        ));
        WindSpireTracker.start(player, nowTick, GALE_DURATION_TICKS);
        PacketDistributor.sendToPlayer(
                player,
                new WindSpirePayload(true, GALE_DURATION_TICKS)
        );
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .build();
    }

    private static LivingEntity findNearestTargetInFront(Player player, double range) {
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

    private static void teleportToTargetFront(Player player, LivingEntity target) {
        Vec3 targetPosition = target.position();
        Vec3 direction = player.position().subtract(targetPosition);
        if (direction.horizontalDistanceSqr() < 0.5) {
            direction = target.getLookAngle().scale(-1);
        }

        double offsetDistance = target.getBbWidth() * 0.5 + 0.8;
        Vec3 offset = new Vec3(direction.x, 0.0, direction.z)
                .normalize()
                .scale(offsetDistance);
        Vec3 desired = new Vec3(
                targetPosition.x + offset.x,
                target.getY(),
                targetPosition.z + offset.z
        );
        Vec3 safe = findSafePosition(player, desired);
        if (safe == null) {
            for (int index = 1; index <= 4 && safe == null; index++) {
                double angle = index * 0.4;
                safe = findSafePosition(
                        player,
                        targetOffsetPosition(target, rotate(offset, angle))
                );
                if (safe == null) {
                    safe = findSafePosition(
                            player,
                            targetOffsetPosition(target, rotate(offset, -angle))
                    );
                }
            }
        }

        Vec3 destination = safe != null ? safe : desired;
        if (player instanceof ServerPlayer serverPlayer) {
            WeaponSkillMovementArbiter.revokeCurrent(serverPlayer);
        }
        player.teleportTo(destination.x, destination.y, destination.z);
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
    }

    private static Vec3 targetOffsetPosition(LivingEntity target, Vec3 offset) {
        return new Vec3(
                target.getX() + offset.x,
                target.getY(),
                target.getZ() + offset.z
        );
    }

    private static Vec3 rotate(Vec3 vector, double radians) {
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
        float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z)
                * (180.0 / Math.PI));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    private static Vec3 computeDashEnd(Player player, double distance) {
        Vec3 start = player.position();
        Vec3 look = horizontalLook(player);
        Vec3 end = start.add(look.scale(distance));
        HitResult hit = player.level().clip(new ClipContext(
                start.add(0.0, player.getBbHeight() * 0.5, 0.0),
                end.add(0.0, player.getBbHeight() * 0.5, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation().subtract(look.scale(0.4));
        }
        Vec3 safe = findSafePosition(player, end);
        return safe != null ? safe : end;
    }

    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            double yaw = Math.toRadians(player.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        return horizontal.normalize();
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
        Vec3 raised = desired.add(0.0, 0.25, 0.0);
        AABB raisedBox = player.getBoundingBox().move(
                raised.x - player.getX(),
                raised.y - player.getY(),
                raised.z - player.getZ()
        );
        return player.level().noCollision(player, raisedBox) ? raised : null;
    }
}
