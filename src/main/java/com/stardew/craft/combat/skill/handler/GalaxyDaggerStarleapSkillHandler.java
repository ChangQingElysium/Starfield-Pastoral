package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.GalaxyDaggerMarkTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.YetiFreezeTracker;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Galaxy Dagger's original Star Leap.
 */
public final class GalaxyDaggerStarleapSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final double TARGET_RANGE = 5.0D;
    public static final double BEHIND_DISTANCE = 3.0D;
    public static final float MARK_DAMAGE_BONUS = 0.30F;
    public static final int FREEZE_DURATION_TICKS = 16;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    private static final double ROTATION_STEP_RADIANS = 0.4D;
    private static final int ROTATION_ATTEMPTS = 4;
    private static final double RAISED_POSITION_OFFSET = 0.25D;

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
        CastPlan plan = resolveCast(context.player());
        if (plan == null || !canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Star Leap can no longer start safely"
            );
        }

        LivingEntity target = plan.target();
        instance.setTargetEntityIds(List.of(target.getId()));

        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Star Leap energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        teleportPlayer(context.player(), plan.destination());
        faceTarget(context.player(), target);

        boolean marked = GalaxyDaggerMarkTracker.consumeIfEligible(
                target,
                context.player(),
                context.nowTick()
        );
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(context.skillData(), marked),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
        );

        YetiFreezeTracker.apply(
                target,
                context.nowTick(),
                FREEZE_DURATION_TICKS
        );
        if (marked) {
            playConsumedMarkEffects(target);
        }

        // Preserve the authored action notification order.
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

    static SkillContext createHitContext(
            WeaponSkillData skillData,
            boolean marked
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier(skillData, marked))
                .guaranteedCrit(true)
                .build();
    }

    static float damageMultiplier(
            WeaponSkillData skillData,
            boolean marked
    ) {
        float base = skillData.getDamagePercent() / 100.0F;
        return marked ? base + MARK_DAMAGE_BONUS : base;
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
        if (!isUsableTarget(player, target)) {
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

    private static boolean isUsableTarget(
            Player player,
            LivingEntity target
    ) {
        return target != null
                && target.level() == player.level()
                && target.isAlive()
                && target.isPickable();
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

    private static void playConsumedMarkEffects(
            LivingEntity target
    ) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6D;
        double z = target.getZ();
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                x,
                y,
                z,
                14,
                0.35D,
                0.2D,
                0.35D,
                0.04D
        );
        serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                x,
                y,
                z,
                12,
                0.35D,
                0.2D,
                0.35D,
                0.05D
        );
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS,
                0.6F,
                1.35F
        );
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS,
                0.35F,
                1.2F
        );
    }

    private record CastPlan(
            LivingEntity target,
            Vec3 destination
    ) {}
}
