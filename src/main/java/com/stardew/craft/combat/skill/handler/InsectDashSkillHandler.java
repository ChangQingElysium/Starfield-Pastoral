package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.InsectDashChainState;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Insect Head's original Wing Dash chain.
 */
public final class InsectDashSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double DASH_DISTANCE = 7.0;
    public static final double PATH_HIT_RADIUS = 1.2;
    public static final int REQUIRED_HITS_TO_CONTINUE = 2;
    public static final int MAX_STAGE = 3;
    public static final int DASH_DURATION_TICKS = 5;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int FINISH_SPEED_DURATION_TICKS = 60;
    public static final int FINISH_SPEED_AMPLIFIER = 0;
    public static final int ANIMATION_TICKS = 8;
    private static final double WALL_CLEARANCE = 0.4;
    private static final double MINIMUM_DASH_DISTANCE_SQUARED = 1.0E-4;
    private static final double PATH_SAMPLE_DISTANCE = 0.25;

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
        int stage = InsectDashChainState.getNextStage(
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
        if (!canPayEnergy(context, stage)) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return resolveSafeDashEnd(context.player(), DASH_DISTANCE) != null
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
                    "Validated Wing Dash movement is now locked"
            );
        }
        int stage = InsectDashChainState.getNextStage(
                context.player(),
                context.nowTick()
        );
        Vec3 start = context.player().position();
        Vec3 end = resolveSafeDashEnd(context.player(), DASH_DISTANCE);
        if (end == null || !canPayEnergy(context, stage)) {
            throw new IllegalStateException(
                    "Validated Wing Dash can no longer start safely"
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

        float energyCost = energyCostForStage(stage);
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                energyCost
        )) {
            throw new IllegalStateException(
                    "Validated Wing Dash energy payment is no longer available"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        DeferredSkillCooldown deferredCooldown = null;
        if (stage < MAX_STAGE) {
            deferredCooldown = WeaponSkillRuntime.deferCooldown(
                    context,
                    instance,
                    context.skillData().getCooldown() * 20
            );
        } else {
            startCooldown(context, instance);
        }
        InsectDashExecutionState executionState =
                new InsectDashExecutionState(
                        stage,
                        targets.stream()
                                .map(LivingEntity::getUUID)
                                .toList(),
                        deferredCooldown
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() -> {
            try {
                for (LivingEntity target : targets) {
                    WeaponSkillDamage.apply(
                            context.player(),
                            target,
                            createHitContext(skillId, stage),
                            context.weaponSnapshot(),
                            context.nowTick()
                                    + HIT_CONTEXT_LIFETIME_TICKS,
                            WeaponSkillDamage.AttackGatePolicy
                                    .RESPECT_AT_IMPACT,
                            WeaponSkillDamage.HitCooldownPolicy
                                    .RESPECT_VANILLA
                    );
                }
                executionState.settle(
                        context.player(),
                        context.nowTick()
                );
                DashMovementTracker.start(
                        context.player(),
                        context.nowTick(),
                        end,
                        DASH_DURATION_TICKS
                );
                if (executionState.earnedFinishSpeed()) {
                    context.player().addEffect(new MobEffectInstance(
                            ModMobEffects.SPEED,
                            FINISH_SPEED_DURATION_TICKS,
                            FINISH_SPEED_AMPLIFIER,
                            false,
                            true,
                            true
                    ));
                }
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
            } catch (RuntimeException exception) {
                executionState.cancel(
                        context.player(),
                        context.nowTick()
                );
                throw exception;
            }
        });
    }

    static float energyCostForStage(int stage) {
        return stage <= 1 ? 3.0F : (stage == 2 ? 5.0F : 7.0F);
    }

    static float damageMultiplierForStage(int stage) {
        return stage <= 1 ? 0.8F : (stage == 2 ? 1.0F : 1.2F);
    }

    static SkillContext createHitContext(String skillId, int stage) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplierForStage(stage))
                .build();
    }

    static boolean continuesChain(int stage, int hitCount) {
        return stage < MAX_STAGE && hitCount >= REQUIRED_HITS_TO_CONTINUE;
    }

    /** Records one exact positive applied hit for the active dash cast. */
    public static boolean recordAppliedHit(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.INSECT_DASH,
                InsectDashExecutionState.class
        ).map(state -> state.recordAppliedHit(target.getUUID()))
                .orElse(false);
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing,
            int stage
    ) {
        return creativeMode
                || freeEnergyBlessing
                || currentEnergy >= energyCostForStage(stage);
    }

    private static boolean canPayEnergy(
            SkillExecutionContext context,
            int stage
    ) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                ),
                stage
        );
    }

    private static void startCooldown(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
    }

    private static Vec3 resolveSafeDashEnd(Player player, double distance) {
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
                        < MINIMUM_DASH_DISTANCE_SQUARED) {
            return null;
        }
        return isPathClear(player, start, safe) ? safe : null;
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
        AABB destinationBounds = player.getBoundingBox().move(
                desired.x - player.getX(),
                desired.y - player.getY(),
                desired.z - player.getZ()
        );
        if (player.level().noCollision(player, destinationBounds)) {
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

    private static List<LivingEntity> findTargetsAlongPath(
            Player player,
            Vec3 start,
            Vec3 end,
            double radius
    ) {
        Vec3 minimum = new Vec3(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y),
                Math.min(start.z, end.z)
        );
        Vec3 maximum = new Vec3(
                Math.max(start.x, end.x),
                Math.max(start.y, end.y),
                Math.max(start.z, end.z)
        );
        AABB bounds = new AABB(minimum, maximum).inflate(
                radius,
                radius * 0.75,
                radius
        );
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable() && entity != player
        );
        targets.removeIf(entity ->
                distanceToSegmentSqr(entity.position(), start, end)
                        > radius * radius
        );
        return targets;
    }

    private static double distanceToSegmentSqr(
            Vec3 point,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 segment = end.subtract(start);
        Vec3 offset = point.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 1.0E-6) {
            return offset.lengthSqr();
        }
        double fraction = Math.max(
                0.0,
                Math.min(1.0, offset.dot(segment) / lengthSquared)
        );
        return point.distanceToSqr(start.add(segment.scale(fraction)));
    }
}
