package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.InfinityDaggerMarkTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.YetiFreezeTracker;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Infinity Dagger's original backstab.
 *
 * <p>Both authored hits resolve in the activation call. One runtime-owned
 * state records whether either hit applied positive damage to the cast's
 * exact target before the original control settlement point.</p>
 */
public final class InfinityDaggerSingularityBackstabSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 12.0F;
    public static final double TARGET_RANGE = 5.0D;
    public static final double BEHIND_DISTANCE = 3.0D;
    public static final float MARKED_SECOND_HIT_BONUS = 0.20F;
    public static final int FREEZE_DURATION_TICKS = 24;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int SECOND_HIT_ANIMATION_TICKS = 6;
    public static final int FINAL_ANIMATION_TICKS = 8;

    private static final double ROTATION_STEP_RADIANS = 0.4D;
    private static final int ROTATION_ATTEMPTS = 4;
    private static final double RAISED_POSITION_OFFSET = 0.25D;

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
        if (!canPayEnergy(context)) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return resolveCast(context.player()) == null
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            throw new IllegalStateException(
                    "Validated Singularity Backstab movement is now locked"
            );
        }
        CastPlan plan = resolveCast(context.player());
        if (plan == null || !canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Singularity Backstab can no longer start safely"
            );
        }

        LivingEntity target = plan.target();
        instance.setTargetEntityIds(List.of(target.getId()));
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Singularity Backstab energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );

        boolean marked = InfinityDaggerMarkTracker.isMarkedBy(
                target,
                context.player(),
                context.nowTick()
        );
        if (marked) {
            InfinityDaggerMarkTracker.consumeDuringBegin(
                    instance,
                    target,
                    context.player(),
                    context.nowTick()
            );
        }
        InfinityDaggerSingularityBackstabExecutionState executionState =
                new InfinityDaggerSingularityBackstabExecutionState(
                        target.getUUID()
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() -> {
            teleportPlayer(context.player(), plan.destination());
            faceTarget(context.player(), target);
            attack(
                    context,
                    target,
                    createHitContext(
                            context.skillData(),
                            false,
                            false
                    ),
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
            if (!canContinueCast(
                    context.player().isAlive()
                            && !context.player().isRemoved(),
                    target.level() == context.player().level()
            )) {
                return;
            }
            if (shouldStrikeSecond(target.isAlive())) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(
                        context.player(),
                        weaponId,
                        skillId,
                        SECOND_HIT_ANIMATION_TICKS
                );
                WeaponSkillAnimationLock.setLock(
                        context.player(),
                        context.nowTick(),
                        SECOND_HIT_ANIMATION_TICKS
                );
                attack(
                        context,
                        target,
                        createHitContext(
                            context.skillData(),
                            true,
                            marked
                        ),
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            }
            if (marked) {
                playConsumedMarkEffects(target);
            }
            if (executionState.settleControl()) {
                YetiFreezeTracker.applyWithEquipmentProtection(
                        target,
                        context.nowTick(),
                        FREEZE_DURATION_TICKS,
                        YetiFreezeTracker.PresentationPolicy
                                .SYNC_FREEZE_OVERLAY
                );
            }
        });

        // Preserve the original final cast notification order.
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                FINAL_ANIMATION_TICKS
        );
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                FINAL_ANIMATION_TICKS
        );
    }

    /** Records one exact positive hit for the active backstab execution. */
    public static boolean recordAppliedHit(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers
                        .INFINITY_DAGGER_SINGULARITY_BACKSTAB,
                InfinityDaggerSingularityBackstabExecutionState.class
        ).map(state -> state.recordAppliedHit(target.getUUID()))
                .orElse(false);
    }

    static SkillContext createHitContext(
            WeaponSkillData skillData,
            boolean secondHit,
            boolean marked
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier(
                        skillData,
                        secondHit,
                        marked
                ))
                .guaranteedCrit(true)
                .build();
    }

    static float damageMultiplier(
            WeaponSkillData skillData,
            boolean secondHit,
            boolean marked
    ) {
        float base = skillData.getDamagePercent() / 100.0F;
        return secondHit && marked
                ? base + MARKED_SECOND_HIT_BONUS
                : base;
    }

    static boolean shouldStrikeSecond(boolean targetAlive) {
        return targetAlive;
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

    static boolean isCastContextValid(
            boolean casterAvailable,
            boolean sameDimension,
            boolean targetAvailable
    ) {
        return casterAvailable
                && sameDimension
                && targetAvailable;
    }

    static boolean canContinueCast(
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable && sameDimension;
    }

    static List<Vec3> behindCandidates(
            Vec3 targetPosition,
            Vec3 targetLook,
            Vec3 playerPosition,
            double targetWidth,
            double distance
    ) {
        Vec3 direction = new Vec3(
                targetLook.x,
                0.0D,
                targetLook.z
        );
        if (direction.lengthSqr() < 1.0E-6D) {
            Vec3 fallback = targetPosition.subtract(playerPosition);
            direction = new Vec3(
                    fallback.x,
                    0.0D,
                    fallback.z
            );
        }
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        }

        double offsetDistance = distance + targetWidth * 0.5D;
        Vec3 offset = direction.normalize().scale(-offsetDistance);
        List<Vec3> candidates =
                new ArrayList<>(1 + ROTATION_ATTEMPTS * 2);
        candidates.add(targetOffsetPosition(targetPosition, offset));
        for (int index = 1; index <= ROTATION_ATTEMPTS; index++) {
            double angle = index * ROTATION_STEP_RADIANS;
            candidates.add(targetOffsetPosition(
                    targetPosition,
                    rotate(offset, angle)
            ));
            candidates.add(targetOffsetPosition(
                    targetPosition,
                    rotate(offset, -angle)
            ));
        }
        return List.copyOf(candidates);
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

    private static CastPlan resolveCast(Player player) {
        LivingEntity target = SkillTargeting.findNearestTargetInFront(
                player,
                TARGET_RANGE
        );
        boolean casterAvailable =
                player.isAlive() && !player.isRemoved();
        boolean sameDimension = target != null
                && target.level() == player.level();
        boolean targetAvailable = target != null
                && target.isAlive()
                && target.isPickable();
        if (!isCastContextValid(
                casterAvailable,
                sameDimension,
                targetAvailable
        )) {
            return null;
        }

        Vec3 destination = findSafeBehindPosition(
                player,
                target,
                BEHIND_DISTANCE
        );
        return destination == null
                ? null
                : new CastPlan(target, destination);
    }

    private static Vec3 findSafeBehindPosition(
            Player player,
            LivingEntity target,
            double distance
    ) {
        for (Vec3 candidate : behindCandidates(
                target.position(),
                target.getLookAngle(),
                player.position(),
                target.getBbWidth(),
                distance
        )) {
            Vec3 safe = findSafePosition(player, candidate);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private static Vec3 findSafePosition(
            Player player,
            Vec3 desired
    ) {
        if (!player.level().getWorldBorder().isWithinBounds(
                BlockPos.containing(desired)
        )) {
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

        Vec3 raised = desired.add(
                0.0D,
                RAISED_POSITION_OFFSET,
                0.0D
        );
        if (!player.level().getWorldBorder().isWithinBounds(
                BlockPos.containing(raised)
        )) {
            return null;
        }
        AABB raisedBounds = player.getBoundingBox().move(
                raised.x - player.getX(),
                raised.y - player.getY(),
                raised.z - player.getZ()
        );
        return player.level().noCollision(player, raisedBounds)
                ? raised
                : null;
    }

    private static Vec3 targetOffsetPosition(
            Vec3 targetPosition,
            Vec3 offset
    ) {
        return new Vec3(
                targetPosition.x + offset.x,
                targetPosition.y,
                targetPosition.z + offset.z
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

    private static void teleportPlayer(
            Player player,
            Vec3 destination
    ) {
        if (player instanceof ServerPlayer serverPlayer) {
            WeaponSkillMovementArbiter.revokeCurrent(serverPlayer);
        }
        player.teleportTo(
                destination.x,
                destination.y,
                destination.z
        );
        player.setDeltaMovement(
                0.0D,
                player.getDeltaMovement().y,
                0.0D
        );
    }

    private static void faceTarget(
            Player player,
            LivingEntity target
    ) {
        Vec3 toTarget = target.position().subtract(player.position());
        if (toTarget.horizontalDistanceSqr() <= 0.01D) {
            return;
        }
        float yaw = (float) (
                Math.atan2(-toTarget.x, toTarget.z)
                        * (180.0D / Math.PI)
        );
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    private static void attack(
            SkillExecutionContext context,
            LivingEntity target,
            SkillContext hitContext,
            WeaponSkillDamage.HitCooldownPolicy hitCooldownPolicy
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                hitContext,
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                hitCooldownPolicy
        );
    }

    private static void playConsumedMarkEffects(
            LivingEntity target
    ) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6D;
        double z = target.getZ();
        double radius = 0.32D;
        for (int index = 0; index < 12; index++) {
            double angle = Math.PI * 2.0D * index / 12.0D;
            double particleX = x + Math.cos(angle) * radius;
            double particleZ = z + Math.sin(angle) * radius;
            double velocityX = (x - particleX) * 0.08D;
            double velocityZ = (z - particleZ) * 0.08D;
            serverLevel.addParticle(
                    ParticleTypes.PORTAL,
                    particleX,
                    y,
                    particleZ,
                    velocityX,
                    0.0D,
                    velocityZ
            );
        }
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                x,
                y,
                z,
                10,
                0.25D,
                0.18D,
                0.25D,
                0.02D
        );
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.PLAYERS,
                0.5F,
                0.75F
        );
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS,
                0.35F,
                0.75F
        );
    }

    private record CastPlan(
            LivingEntity target,
            Vec3 destination
    ) {}
}
