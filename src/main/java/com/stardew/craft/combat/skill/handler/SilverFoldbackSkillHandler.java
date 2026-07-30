package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.SilverSaberFoldbackState;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Silver Saber's original two-step Foldback.
 */
public final class SilverFoldbackSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 5.0;
    public static final double EMPTY_DASH_DISTANCE = 5.0;
    public static final int EMPTY_DASH_DURATION_TICKS = 5;
    private static final double WALL_CLEARANCE = 0.4;
    private static final double MINIMUM_DASH_DISTANCE_SQUARED = 1.0E-4;
    private static final double PATH_SAMPLE_DISTANCE = 0.25;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        SilverSaberSkillHelper.settleInvalidFoldback(
                context.player(),
                context.nowTick()
        );
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        return resolvePlan(context) != null
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        CastPlan plan = resolvePlan(context);
        if (plan == null) {
            throw new IllegalStateException(
                    "Validated Silver Foldback movement is no longer safe"
            );
        }

        String weaponId = context.weaponId().getPath();
        switch (plan.mode) {
            case RETURN -> {
                if (plan.target != null) {
                    instance.setTargetEntityIds(
                            List.of(plan.target.getId())
                    );
                }
                SilverSaberSkillHelper.executeReturnStrike(
                        context.player(),
                        plan.target,
                        plan.destination,
                        weaponId,
                        context.skillData(),
                        context.nowTick(),
                        SilverFoldbackSkillHandler::teleport,
                        context.weaponSnapshot()
                );
            }
            case TARGET -> {
                instance.setTargetEntityIds(
                        List.of(plan.target.getId())
                );
                Vec3 origin = context.player().position();
                teleport(context.player(), plan.destination);
                faceTarget(context.player(), plan.target);
                SilverSaberSkillHelper.executeInitialDashAfterTeleport(
                        context.player(),
                        plan.target,
                        origin,
                        weaponId,
                        context.skillData(),
                        context.nowTick(),
                        context.weaponSnapshot()
                );
            }
            case EMPTY -> SilverSaberSkillHelper.executeEmptyDash(
                    context.player(),
                    weaponId,
                    context.skillData(),
                    context.nowTick(),
                    (player, distance) -> DashMovementTracker.start(
                            context.player(),
                            context.nowTick(),
                            plan.destination,
                            EMPTY_DASH_DURATION_TICKS
                    )
            );
        }
    }

    static CastMode modeFor(
            boolean foldbackActive,
            boolean targetAvailable
    ) {
        if (foldbackActive) {
            return CastMode.RETURN;
        }
        return targetAvailable ? CastMode.TARGET : CastMode.EMPTY;
    }

    private static CastPlan resolvePlan(SkillExecutionContext context) {
        boolean foldbackActive = SilverSaberFoldbackState.isActive(
                context.player(),
                context.nowTick()
        );
        LivingEntity target = SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
        CastMode mode = modeFor(foldbackActive, target != null);
        return switch (mode) {
            case RETURN -> {
                Vec3 safeOrigin = findSafePosition(
                        context.player(),
                        SilverSaberFoldbackState.getOrigin(context.player())
                );
                yield safeOrigin == null
                        ? null
                        : new CastPlan(mode, target, safeOrigin);
            }
            case TARGET -> {
                Vec3 destination = findTargetFrontPosition(
                        context.player(),
                        target
                );
                yield destination == null
                        ? null
                        : new CastPlan(mode, target, destination);
            }
            case EMPTY -> {
                Vec3 destination = resolveSafeDashEnd(
                        context.player(),
                        EMPTY_DASH_DISTANCE
                );
                yield destination == null
                        ? null
                        : new CastPlan(mode, null, destination);
            }
        };
    }

    private static Vec3 findTargetFrontPosition(
            Player player,
            LivingEntity target
    ) {
        Vec3 targetPosition = target.position();
        Vec3 direction = player.position().subtract(targetPosition);
        if (direction.horizontalDistanceSqr() < 0.5) {
            direction = target.getLookAngle().scale(-1.0);
        }
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            return null;
        }

        double offsetDistance = target.getBbWidth() * 0.5 + 0.8;
        Vec3 offset = horizontal.normalize().scale(offsetDistance);
        Vec3 safe = findSafePosition(
                player,
                targetOffsetPosition(target, offset)
        );
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
        return safe;
    }

    private static Vec3 targetOffsetPosition(
            LivingEntity target,
            Vec3 offset
    ) {
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

    private static Vec3 resolveSafeDashEnd(
            Player player,
            double distance
    ) {
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
            double hitDistance = Math.max(
                    0.0,
                    hit.getLocation().subtract(start).dot(look)
                            - WALL_CLEARANCE
            );
            end = start.add(look.scale(hitDistance));
        }

        Vec3 safe = findSafePosition(player, end);
        if (safe == null
                || safe.subtract(start).horizontalDistanceSqr()
                        < MINIMUM_DASH_DISTANCE_SQUARED
                || !isPathClear(player, start, safe)) {
            return null;
        }
        return safe;
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
        if (desired == null) {
            return null;
        }
        AABB bounds = player.getBoundingBox().move(
                desired.x - player.getX(),
                desired.y - player.getY(),
                desired.z - player.getZ()
        );
        if (player.level().noCollision(player, bounds)) {
            return desired;
        }

        Vec3 raised = desired.add(0.0, 0.25, 0.0);
        AABB raisedBounds = player.getBoundingBox().move(
                raised.x - player.getX(),
                raised.y - player.getY(),
                raised.z - player.getZ()
        );
        return player.level().noCollision(player, raisedBounds)
                ? raised
                : null;
    }

    private static boolean isPathClear(
            Player player,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 movement = end.subtract(start);
        int sampleCount = Math.max(
                1,
                (int) Math.ceil(
                        movement.horizontalDistance()
                                / PATH_SAMPLE_DISTANCE
                )
        );
        for (int sample = 1; sample <= sampleCount; sample++) {
            Vec3 position = start.add(
                    movement.scale((double) sample / sampleCount)
            );
            AABB bounds = player.getBoundingBox().move(
                    position.x - player.getX(),
                    position.y - player.getY(),
                    position.z - player.getZ()
            );
            if (!player.level().noCollision(player, bounds)) {
                return false;
            }
        }
        return true;
    }

    private static void faceTarget(
            Player player,
            LivingEntity target
    ) {
        Vec3 direction = target.position().subtract(player.position());
        if (direction.horizontalDistanceSqr() <= 0.01) {
            return;
        }
        float yaw = (float) (
                Math.atan2(-direction.x, direction.z)
                        * (180.0 / Math.PI)
        );
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    private static void teleport(Player player, Vec3 destination) {
        player.teleportTo(
                destination.x,
                destination.y,
                destination.z
        );
        player.setDeltaMovement(
                0.0,
                player.getDeltaMovement().y,
                0.0
        );
    }

    enum CastMode {
        RETURN,
        TARGET,
        EMPTY
    }

    private record CastPlan(
            CastMode mode,
            LivingEntity target,
            Vec3 destination
    ) {}
}
