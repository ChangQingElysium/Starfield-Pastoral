package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.network.AccretionDiskPayload;
import com.stardew.craft.combat.network.BlackHolePostPayload;
import com.stardew.craft.combat.network.SingularityCorePayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Eternal Collapse field, its pull loop, and scheduled strikes. */
final class EternalCollapseExecutionState
        implements SkillInstance.ExecutionState {
    static final double PULL_STRENGTH = 0.10D;
    static final int MINIMUM_STRIKE_INTERVAL_TICKS = 4;
    static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private final Vec3 center;
    private final long endTick;
    private long nextStrikeTick;
    private int remainingStrikes;
    private final double radius;
    private final float strikeMultiplier;
    private final float critBonus;
    private final boolean finalStrike;
    private final float finalMultiplier;
    private final String skillId;
    private final ResourceKey<Level> dimension;
    private boolean settled;
    private boolean advancing;

    EternalCollapseExecutionState(
            Vec3 center,
            long nowTick,
            int durationTicks,
            int strikes,
            double radius,
            float strikeMultiplier,
            float critBonus,
            boolean finalStrike,
            float finalMultiplier,
            String skillId,
            ResourceKey<Level> dimension
    ) {
        if (durationTicks <= 0 || strikes <= 0) {
            throw new IllegalArgumentException(
                    "Eternal Collapse duration and strikes must be positive"
            );
        }
        this.center = Objects.requireNonNull(center, "center");
        this.endTick = nowTick + durationTicks;
        this.nextStrikeTick = nowTick
                + strikeInterval(durationTicks, strikes);
        this.remainingStrikes = strikes;
        this.radius = radius;
        this.strikeMultiplier = strikeMultiplier;
        this.critBonus = critBonus;
        this.finalStrike = finalStrike;
        this.finalMultiplier = finalMultiplier;
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    void startPresentation(ServerPlayer player, int durationTicks) {
        ServerLevel level = player.serverLevel();
        PacketDistributor.sendToPlayersInDimension(
                level,
                new AccretionDiskPayload(
                        (float) center.x,
                        (float) center.y,
                        (float) center.z,
                        (float) radius,
                        durationTicks,
                        VfxColors.INFINITY_GOLD
                )
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new SingularityCorePayload(
                        (float) center.x,
                        (float) center.y + 0.05F,
                        (float) center.z,
                        1.4F,
                        durationTicks,
                        VfxColors.INFINITY_GOLD
                )
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new BlackHolePostPayload(
                        (float) center.x,
                        (float) center.y + 0.5F,
                        (float) center.z,
                        0.35F,
                        0.9F,
                        durationTicks
                )
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (advancing) {
            return SkillTickResult.CONTINUE;
        }
        if (!isValidContext(
                context.player().isAlive()
                        && !context.player().isRemoved(),
                dimension.equals(context.player().level().dimension())
        )) {
            cancel();
            return SkillTickResult.CANCEL;
        }

        advancing = true;
        try {
            pullTargets(context.player());

            if (shouldStrike(
                    context.nowTick(),
                    nextStrikeTick,
                    remainingStrikes
            )) {
                strike(
                        context,
                        strikeMultiplier,
                        critBonus
                );
                remainingStrikes -= 1;
                if (remainingStrikes > 0) {
                    long interval = strikeInterval(
                            endTick - context.nowTick(),
                            remainingStrikes
                    );
                    nextStrikeTick = context.nowTick() + interval;
                }
            }

            if (context.nowTick() >= endTick) {
                try {
                    if (finalStrike) {
                        strike(context, finalMultiplier, critBonus);
                    }
                } finally {
                    settled = true;
                }
                return SkillTickResult.COMPLETE;
            }
            return SkillTickResult.CONTINUE;
        } finally {
            advancing = false;
        }
    }

    void cancel() {
        settled = true;
    }

    static boolean isValidContext(
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable && sameDimension;
    }

    static boolean shouldStrike(
            long nowTick,
            long nextStrikeTick,
            int remainingStrikes
    ) {
        return remainingStrikes > 0 && nowTick >= nextStrikeTick;
    }

    static long strikeInterval(
            long remainingTicks,
            int remainingStrikes
    ) {
        return Math.max(
                MINIMUM_STRIKE_INTERVAL_TICKS,
                remainingTicks / Math.max(1, remainingStrikes)
        );
    }

    static SkillContext createStrikeContext(
            String skillId,
            float damageMultiplier,
            float critBonus
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .critChanceBonus(critBonus)
                .build();
    }

    @SuppressWarnings("null")
    private void pullTargets(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> targets = snapshotTargets(
                level,
                player,
                effectBounds(center, radius)
        );

        for (LivingEntity target : targets) {
            Vec3 direction = center.subtract(target.position());
            if (direction.lengthSqr() < 1.0E-4D) {
                continue;
            }
            Vec3 pull = direction.normalize().scale(PULL_STRENGTH);
            WeaponSkillMovementArbiter.revokeCurrentIfPlayer(target);
            target.setDeltaMovement(
                    target.getDeltaMovement().add(pull)
            );
            target.hurtMarked = true;
            if ((level.getGameTime() & 1L) == 0L) {
                double x = target.getX();
                double y = target.getY()
                        + target.getBbHeight() * 0.5D;
                double z = target.getZ();
                level.sendParticles(
                        ParticleTypes.PORTAL,
                        x,
                        y,
                        z,
                        1,
                        0.18D,
                        0.22D,
                        0.18D,
                        0.01D
                );
                if (level.random.nextFloat() < 0.5F) {
                    level.sendParticles(
                            ParticleTypes.END_ROD,
                            x,
                            y,
                            z,
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
    private void strike(
            SkillExecutionContext executionContext,
            float damageMultiplier,
            float criticalChanceBonus
    ) {
        ServerPlayer player = executionContext.player();
        ServerLevel level = player.serverLevel();
        List<LivingEntity> targets = snapshotTargets(
                level,
                player,
                effectBounds(center, radius)
        );
        WeaponDamageSnapshot weaponSnapshot =
                executionContext.weaponSnapshot();

        for (LivingEntity target : targets) {
            SkillContext context = createStrikeContext(
                    skillId,
                    damageMultiplier,
                    criticalChanceBonus
            );
            WeaponSkillDamage.apply(
                    player,
                    target,
                    context,
                    weaponSnapshot,
                    executionContext.nowTick()
                            + HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }

        level.playSound(
                null,
                new BlockPos(
                        (int) center.x,
                        (int) center.y,
                        (int) center.z
                ),
                SoundEvents.WITHER_SPAWN,
                SoundSource.PLAYERS,
                0.5F,
                1.3F
        );
        level.sendParticles(
                ParticleTypes.PORTAL,
                center.x,
                center.y + 0.8D,
                center.z,
                16,
                radius * 0.35D,
                0.5D,
                radius * 0.35D,
                0.02D
        );
        level.sendParticles(
                ParticleTypes.SMOKE,
                center.x,
                center.y + 0.5D,
                center.z,
                10,
                radius * 0.35D,
                0.3D,
                radius * 0.35D,
                0.02D
        );
    }

    private static AABB effectBounds(Vec3 center, double radius) {
        return new AABB(
                center.x - radius,
                center.y - 1.5D,
                center.z - radius,
                center.x + radius,
                center.y + 2.0D,
                center.z + radius
        );
    }

    private static List<LivingEntity> snapshotTargets(
            ServerLevel level,
            ServerPlayer player,
            AABB bounds
    ) {
        return List.copyOf(level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable() && entity != player
        ));
    }
}
