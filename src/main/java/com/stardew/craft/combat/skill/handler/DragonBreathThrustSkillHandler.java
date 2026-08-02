package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Dragontooth Cutlass's original thrust.
 */
public final class DragonBreathThrustSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double DASH_DISTANCE = 5.0D;
    public static final double PATH_HIT_RADIUS = 0.9D;
    public static final int DASH_DURATION_TICKS = 5;
    public static final float CRITICAL_CHANCE_BONUS = 0.10F;
    public static final int VULNERABLE_DURATION_TICKS = 80;
    public static final int VULNERABLE_AMPLIFIER = 1;
    public static final int STAGGER_DURATION_TICKS = 40;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;
    private static final double WALL_CLEARANCE = 0.4D;
    private static final double MINIMUM_DASH_DISTANCE_SQUARED = 1.0E-4D;
    private static final double PATH_SAMPLE_DISTANCE = 0.25D;

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
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return resolveSafeDashEnd(
                context.player(),
                DASH_DISTANCE
        ) != null
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            throw new IllegalStateException(
                    "Validated Dragon Breath Thrust movement is now locked"
            );
        }
        Vec3 start = context.player().position();
        Vec3 end = resolveSafeDashEnd(
                context.player(),
                DASH_DISTANCE
        );
        if (end == null) {
            throw new IllegalStateException(
                    "Validated Dragon Breath Thrust path is no longer safe"
            );
        }

        List<LivingEntity> targets = findTargetsAlongPath(
                context.player(),
                start,
                end,
                PATH_HIT_RADIUS
        );
        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );

        DragonBreathThrustExecutionState executionState =
                new DragonBreathThrustExecutionState(
                        context.nowTick(),
                        DASH_DURATION_TICKS
                );
        instance.initializeExecutionState(executionState);

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> {
            executionState.start(
                    context.player(),
                    context.nowTick(),
                    end,
                    DASH_DURATION_TICKS
            );
            for (LivingEntity target : targets) {
                attackTarget(context, target);
            }
        });

        // Preserve the original order: movement and hits precede presentation.
        // This thrust never imposed a server-side attack lock.
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
                DragonBreathThrustExecutionState.class
        ).result(context.nowTick());
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(DragonBreathThrustExecutionState.class)
                .ifPresent(state -> state.finish(context.player(), reason));
    }

    static boolean shouldCancelMovement(SkillInstance.EndReason reason) {
        return reason != SkillInstance.EndReason.COMPLETED;
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .critChanceBonus(CRITICAL_CHANCE_BONUS)
                .build();
    }

    static double distancePointToSegment2D(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        double segmentX = endX - startX;
        double segmentZ = endZ - startZ;
        double offsetX = pointX - startX;
        double offsetZ = pointZ - startZ;
        double lengthSquared =
                segmentX * segmentX + segmentZ * segmentZ;
        if (lengthSquared <= 1.0E-6D) {
            return Math.sqrt(
                    offsetX * offsetX + offsetZ * offsetZ
            );
        }
        double fraction = Mth.clamp(
                (offsetX * segmentX + offsetZ * segmentZ)
                        / lengthSquared,
                0.0D,
                1.0D
        );
        double closestX = startX + segmentX * fraction;
        double closestZ = startZ + segmentZ * fraction;
        double distanceX = pointX - closestX;
        double distanceZ = pointZ - closestZ;
        return Math.sqrt(
                distanceX * distanceX + distanceZ * distanceZ
        );
    }

    private static void attackTarget(
            SkillExecutionContext context,
            LivingEntity target
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(context.skillData()),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
        );
    }

    static Vec3 resolveSafeDashEnd(
            Player player,
            double distance
    ) {
        Vec3 start = player.position();
        Vec3 look = horizontalLook(player);
        Vec3 end = start.add(look.scale(distance));
        HitResult hit = player.level().clip(new ClipContext(
                start.add(0.0D, player.getBbHeight() * 0.5D, 0.0D),
                end.add(0.0D, player.getBbHeight() * 0.5D, 0.0D),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            double hitDistance = Math.max(
                    0.0D,
                    hit.getLocation().subtract(start).dot(look)
                            - WALL_CLEARANCE
            );
            end = start.add(look.scale(hitDistance));
        }
        if (!isSafePosition(player, end)
                || end.subtract(start).horizontalDistanceSqr()
                        < MINIMUM_DASH_DISTANCE_SQUARED
                || !isPathClear(player, start, end)) {
            return null;
        }
        return end;
    }

    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            double yaw = Math.toRadians(player.getYRot());
            horizontal = new Vec3(
                    -Math.sin(yaw),
                    0.0D,
                    Math.cos(yaw)
            );
        }
        return horizontal.normalize();
    }

    private static boolean isSafePosition(
            Player player,
            Vec3 position
    ) {
        AABB bounds = player.getBoundingBox().move(
                position.x - player.getX(),
                position.y - player.getY(),
                position.z - player.getZ()
        );
        return player.level().noCollision(player, bounds);
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
            if (!isSafePosition(player, position)) {
                return false;
            }
        }
        return true;
    }

    static List<LivingEntity> findTargetsAlongPath(
            Player player,
            Vec3 start,
            Vec3 end,
            double radius
    ) {
        AABB bounds = player.getBoundingBox()
                .expandTowards(end.subtract(start))
                .inflate(
                        radius,
                        player.getBbHeight() * 0.75D,
                        radius
                );
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isAlive()
                        && entity.isPickable()
                        && entity != player
        );
        targets.removeIf(entity ->
                distancePointToSegment2D(
                        entity.getX(),
                        entity.getZ(),
                        start.x,
                        start.z,
                        end.x,
                        end.z
                ) > radius
        );
        targets.sort((first, second) -> Double.compare(
                first.distanceToSqr(player),
                second.distanceToSqr(player)
        ));
        return targets;
    }
}
