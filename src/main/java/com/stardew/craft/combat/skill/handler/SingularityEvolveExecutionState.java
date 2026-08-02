package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.network.RiftPathPayload;
import com.stardew.craft.combat.network.ShockwaveRingPayload;
import com.stardew.craft.combat.network.SingularityCorePayload;
import com.stardew.craft.combat.network.SingularityRunePayload;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.RiftPathDamageTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One runtime-owned Singularity Evolution pull and settlement window. */
final class SingularityEvolveExecutionState
        implements SkillInstance.ExecutionState {
    private final long endTick;
    private final String skillId;
    private final boolean evolved;
    private final ResourceKey<Level> dimension;
    private final WeaponDamageSnapshot weaponSnapshot;
    private final SingularityEvolveRewardState rewardState =
            new SingularityEvolveRewardState();
    private long lastProcessedTick = Long.MIN_VALUE;
    private DashMovementTracker.Handle movementHandle;
    private RiftPathDamageTracker.Handle riftHandle;
    private boolean detachedResourcesReleased;
    private boolean settled;

    SingularityEvolveExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            String skillId,
            boolean evolved,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Singularity Evolution duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.evolved = evolved;
        this.weaponSnapshot = Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
        );
    }

    void start(
            ServerPlayer player,
            int durationTicks
    ) {
        try {
            sendInitialPresentation(player, durationTicks);
        } catch (RuntimeException exception) {
            settled = true;
            throw exception;
        }
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        ServerPlayer player = context.player();
        if (!isValidContext(
                player.isAlive() && !player.isRemoved(),
                dimension.equals(player.level().dimension())
        )) {
            cancel(player);
            return SkillTickResult.CANCEL;
        }
        long nowTick = context.nowTick();
        if (!shouldProcessTick(nowTick, lastProcessedTick)) {
            return SkillTickResult.CONTINUE;
        }
        lastProcessedTick = nowTick;
        if (isWithinPullWindow(nowTick, endTick)) {
            pullTargets(player);
            return SkillTickResult.CONTINUE;
        }

        resolveAndReleaseDetachedResources(player, nowTick);
        settled = true;
        return SkillTickResult.COMPLETE;
    }

    void cancel(ServerPlayer player) {
        if (settled) {
            return;
        }
        if (!detachedResourcesReleased) {
            cancelOwnedDetachedResources(player, null);
        }
        settled = true;
    }

    boolean claimHitRewards() {
        return rewardState.claim();
    }

    private void sendInitialPresentation(
            ServerPlayer player,
            int durationTicks
    ) {
        Vec3 pos = player.position();
        ServerLevel level = player.serverLevel();
        int color = evolved
                ? VfxColors.INFINITY_GOLD
                : VfxColors.GALAXY_PURPLE;
        PacketDistributor.sendToPlayersInDimension(
                level,
                new ShockwaveRingPayload(
                        (float) pos.x,
                        (float) pos.y,
                        (float) pos.z,
                        3.6F,
                        12,
                        color
                )
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new SingularityRunePayload(
                        (float) pos.x,
                        (float) pos.y,
                        (float) pos.z,
                        (float) SingularityEvolveSkillHandler.EFFECT_RADIUS,
                        durationTicks,
                        color
                )
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new SingularityCorePayload(
                        (float) pos.x,
                        (float) pos.y + 0.05F,
                        (float) pos.z,
                        1.15F,
                        durationTicks,
                        color
                )
        );
    }

    @SuppressWarnings("null")
    private void pullTargets(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = SingularityEvolveSkillHandler.EFFECT_RADIUS;
        AABB box = new AABB(
                center.x - radius,
                center.y - 1.5D,
                center.z - radius,
                center.x + radius,
                center.y + 2.0D,
                center.z + radius
        );
        List<LivingEntity> targets = List.copyOf(
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != player
                )
        );

        for (LivingEntity target : targets) {
            Vec3 direction = center.subtract(target.position());
            if (direction.lengthSqr() < 1.0E-4D) {
                continue;
            }
            Vec3 pull = direction.normalize().scale(
                    SingularityEvolveSkillHandler.PULL_STRENGTH
            );
            WeaponSkillMovementArbiter.revokeCurrentIfPlayer(target);
            target.setDeltaMovement(
                    target.getDeltaMovement().add(pull)
            );
            target.hurtMarked = true;
            if ((level.getGameTime() & 1L) == 0L) {
                double particleX = target.getX();
                double particleY = target.getY()
                        + target.getBbHeight() * 0.5D;
                double particleZ = target.getZ();
                level.sendParticles(
                        ParticleTypes.END_ROD,
                        particleX,
                        particleY,
                        particleZ,
                        1,
                        0.15D,
                        0.2D,
                        0.15D,
                        0.01D
                );
                if (level.random.nextFloat() < 0.6F) {
                    level.sendParticles(
                            ParticleTypes.ENCHANT,
                            particleX,
                            particleY,
                            particleZ,
                            1,
                            0.12D,
                            0.18D,
                            0.12D,
                            0.01D
                    );
                }
            }
        }
    }

    @SuppressWarnings("null")
    private void resolveAndReleaseDetachedResources(
            ServerPlayer player,
            long nowTick
    ) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = SingularityEvolveSkillHandler.EFFECT_RADIUS;
        AABB box = new AABB(
                center.x - radius,
                center.y - 1.5D,
                center.z - radius,
                center.x + radius,
                center.y + 2.0D,
                center.z + radius
        );
        List<LivingEntity> targets = List.copyOf(
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != player
                )
        );

        for (LivingEntity target : targets) {
            applyDamage(
                    player,
                    target,
                    nowTick,
                    SingularityEvolveSkillHandler
                            .EXPLOSION_DAMAGE_MULTIPLIER,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE
            );
        }

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS,
                0.8F,
                1.1F
        );
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.6F,
                1.2F
        );
        level.sendParticles(
                ParticleTypes.PORTAL,
                center.x,
                center.y + 0.8D,
                center.z,
                30,
                radius * 0.4D,
                0.6D,
                radius * 0.4D,
                0.05D
        );

        movementHandle = dashForward(player, nowTick);
        try {
            if (evolved) {
                startRift(player, level);
            }
            applySlashAfterDash(player, nowTick);
            detachedResourcesReleased = true;
        } catch (RuntimeException exception) {
            cancelOwnedDetachedResources(player, exception);
            throw exception;
        }
    }

    private void startRift(ServerPlayer player, ServerLevel level) {
        Vec3 look = getHorizontalLook(player).normalize();
        Vec3 pos = player.position();
        float yaw = (float) (
                Math.atan2(-look.x, look.z) * (180.0D / Math.PI)
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new RiftPathPayload(
                        (float) pos.x,
                        (float) pos.y,
                        (float) pos.z,
                        yaw,
                        SingularityEvolveSkillHandler.RIFT_LENGTH,
                        SingularityEvolveSkillHandler.RIFT_DURATION_TICKS,
                        VfxColors.INFINITY_GOLD
                )
        );
        riftHandle = RiftPathDamageTracker.startExact(
                player,
                pos,
                yaw,
                SingularityEvolveSkillHandler.RIFT_LENGTH,
                SingularityEvolveSkillHandler.RIFT_DURATION_TICKS,
                "singularity_rift_path",
                weaponSnapshot
        );
    }

    private void applySlashAfterDash(
            ServerPlayer player,
            long nowTick
    ) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 look = getHorizontalLook(player).normalize();
        Vec3 end = start.add(
                look.scale(SingularityEvolveSkillHandler.DASH_DISTANCE)
        );
        List<LivingEntity> targets = findTargetsOnPath(
                level,
                player,
                start,
                end,
                SingularityEvolveSkillHandler.SLASH_PATH_HALF_WIDTH
        );
        for (LivingEntity target : targets) {
            applyDamage(
                    player,
                    target,
                    nowTick,
                    SingularityEvolveSkillHandler
                            .SLASH_DAMAGE_MULTIPLIER,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT
            );
        }
    }

    private void applyDamage(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            float damageMultiplier,
            WeaponSkillDamage.AttackGatePolicy attackGatePolicy
    ) {
        SkillContext context = createDamageContext(
                skillId,
                damageMultiplier
        );
        WeaponSkillDamage.apply(
                player,
                target,
                context,
                weaponSnapshot,
                nowTick + SingularityEvolveSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS,
                attackGatePolicy,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }

    private void cancelOwnedDetachedResources(
            ServerPlayer player,
            RuntimeException primaryFailure
    ) {
        RuntimeException cleanupFailure = primaryFailure;
        try {
            RiftPathDamageTracker.cancel(player, riftHandle);
        } catch (RuntimeException exception) {
            cleanupFailure = recordCleanupFailure(
                    cleanupFailure,
                    exception
            );
        }
        try {
            DashMovementTracker.cancel(player, movementHandle);
        } catch (RuntimeException exception) {
            cleanupFailure = recordCleanupFailure(
                    cleanupFailure,
                    exception
            );
        }
        if (primaryFailure == null && cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private static RuntimeException recordCleanupFailure(
            RuntimeException primaryFailure,
            RuntimeException cleanupFailure
    ) {
        if (primaryFailure == null) {
            return cleanupFailure;
        }
        if (cleanupFailure != primaryFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
        return primaryFailure;
    }

    @SuppressWarnings("null")
    private static DashMovementTracker.Handle dashForward(
            ServerPlayer player,
            long nowTick
    ) {
        Vec3 start = player.position();
        Vec3 look = getHorizontalLook(player);
        Vec3 end = start.add(
                look.scale(SingularityEvolveSkillHandler.DASH_DISTANCE)
        );
        HitResult hit = player.level().clip(new ClipContext(
                start.add(0.0D, player.getBbHeight() * 0.5D, 0.0D),
                end.add(0.0D, player.getBbHeight() * 0.5D, 0.0D),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation().subtract(look.scale(0.4D));
        }
        return DashMovementTracker.startExact(
                player,
                nowTick,
                end,
                SingularityEvolveSkillHandler.DASH_DURATION_TICKS
        );
    }

    @SuppressWarnings("null")
    private static Vec3 getHorizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0D, look.z);
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = look;
        }
        return direction.normalize();
    }

    @SuppressWarnings("null")
    private static List<LivingEntity> findTargetsOnPath(
            ServerLevel level,
            ServerPlayer player,
            Vec3 start,
            Vec3 end,
            double halfWidth
    ) {
        Vec3 min = new Vec3(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y),
                Math.min(start.z, end.z)
        );
        Vec3 max = new Vec3(
                Math.max(start.x, end.x),
                Math.max(start.y, end.y),
                Math.max(start.z, end.z)
        );
        AABB box = new AABB(min, max).inflate(
                halfWidth,
                1.2D,
                halfWidth
        );
        return List.copyOf(level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable() && entity != player
        ));
    }

    static boolean isWithinPullWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    static boolean shouldProcessTick(
            long nowTick,
            long lastProcessedTick
    ) {
        return nowTick != lastProcessedTick;
    }

    static boolean isValidContext(
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable && sameDimension;
    }

    static SkillContext createDamageContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }
}

/** One-shot positive-hit reward gate separated for pure state testing. */
final class SingularityEvolveRewardState {
    private boolean claimed;

    synchronized boolean claim() {
        if (claimed) {
            return false;
        }
        claimed = true;
        return true;
    }
}
