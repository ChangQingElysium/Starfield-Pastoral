package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.network.AccretionDiskPayload;
import com.stardew.craft.combat.network.BlackHolePostPayload;
import com.stardew.craft.combat.network.SingularityCorePayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * 无限之刃 - 永恒坍缩：黑洞牵引 + 多段斩击 + 终击
 */
public final class EternalCollapseTracker {
    public enum Status {
        ACTIVE,
        COMPLETED,
        INVALIDATED
    }

    public static final double PULL_STRENGTH = 0.10D;
    public static final int MINIMUM_STRIKE_INTERVAL_TICKS = 4;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final class State {
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
        private final WeaponDamageSnapshot weaponSnapshot;

        private State(
                Vec3 center,
                long endTick,
                long nextStrikeTick,
                int remainingStrikes,
                double radius,
                float strikeMultiplier,
                float critBonus,
                boolean finalStrike,
                float finalMultiplier,
                String skillId,
                ResourceKey<Level> dimension,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.center = center;
            this.endTick = endTick;
            this.nextStrikeTick = nextStrikeTick;
            this.remainingStrikes = remainingStrikes;
            this.radius = radius;
            this.strikeMultiplier = strikeMultiplier;
            this.critBonus = critBonus;
            this.finalStrike = finalStrike;
            this.finalMultiplier = finalMultiplier;
            this.skillId = skillId;
            this.dimension = dimension;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private EternalCollapseTracker() {}

    @SuppressWarnings("null")
    public static void start(
            ServerPlayer player,
            Vec3 center,
            long nowTick,
            int durationTicks,
            int strikes,
            double radius,
            float strikeMultiplier,
            float critBonus,
            boolean finalStrike,
            float finalMultiplier,
            String skillId
    ) {
        startInternal(
                player,
                center,
                nowTick,
                durationTicks,
                strikes,
                radius,
                strikeMultiplier,
                critBonus,
                finalStrike,
                finalMultiplier,
                skillId,
                null
        );
    }

    public static void start(
            ServerPlayer player,
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
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                center,
                nowTick,
                durationTicks,
                strikes,
                radius,
                strikeMultiplier,
                critBonus,
                finalStrike,
                finalMultiplier,
                skillId,
                java.util.Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    private static void startInternal(
            ServerPlayer player,
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
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || strikes <= 0 || durationTicks <= 0
                || skillId == null) {
            return;
        }
        Vec3 useCenter = center == null ? player.position() : center;
        long interval = strikeInterval(durationTicks, strikes);
        ACTIVE.put(
                player.getUUID(),
                new State(
                        useCenter,
                        nowTick + durationTicks,
                        nowTick + interval,
                        strikes,
                        radius,
                        strikeMultiplier,
                        critBonus,
                        finalStrike,
                        finalMultiplier,
                        skillId,
                        player.level().dimension(),
                        weaponSnapshot
                )
        );

        ServerLevel level = player.serverLevel();
        PacketDistributor.sendToPlayersInDimension(
                level,
                new AccretionDiskPayload(
                        (float) useCenter.x,
                        (float) useCenter.y,
                        (float) useCenter.z,
                        (float) radius,
                        durationTicks,
                        VfxColors.INFINITY_GOLD
                )
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new SingularityCorePayload(
                        (float) useCenter.x,
                        (float) useCenter.y + 0.05F,
                        (float) useCenter.z,
                        1.4F,
                        durationTicks,
                        VfxColors.INFINITY_GOLD
                )
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new BlackHolePostPayload(
                        (float) useCenter.x,
                        (float) useCenter.y + 0.5F,
                        (float) useCenter.z,
                        0.35F,
                        0.9F,
                        durationTicks
                )
        );
    }

    public static Status tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return Status.INVALIDATED;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return Status.INVALIDATED;
        }
        if (!isValidContext(
                player.isAlive() && !player.isRemoved(),
                isSameDimension(
                        state.dimension,
                        player.level().dimension()
                )
        )) {
            ACTIVE.remove(player.getUUID());
            return Status.INVALIDATED;
        }

        pullTargets(player, state);

        if (shouldStrike(
                nowTick,
                state.nextStrikeTick,
                state.remainingStrikes
        )) {
            strike(
                    player,
                    nowTick,
                    state,
                    state.strikeMultiplier,
                    state.critBonus
            );
            state.remainingStrikes -= 1;
            if (state.remainingStrikes > 0) {
                long interval = strikeInterval(
                        state.endTick - nowTick,
                        state.remainingStrikes
                );
                state.nextStrikeTick = nowTick + interval;
            }
        }

        if (nowTick >= state.endTick) {
            try {
                if (state.finalStrike) {
                    strike(
                            player,
                            nowTick,
                            state,
                            state.finalMultiplier,
                            state.critBonus
                    );
                }
            } finally {
                ACTIVE.remove(player.getUUID());
            }
            return Status.COMPLETED;
        }
        return Status.ACTIVE;
    }

    @SuppressWarnings("null")
    private static void pullTargets(ServerPlayer player, State state) {
        ServerLevel level = player.serverLevel();
        Vec3 center = state.center;
        AABB box = effectBounds(center, state.radius);
        List<LivingEntity> targets = snapshotTargets(
                level,
                player,
                box
        );

        for (LivingEntity target : targets) {
            Vec3 direction = center.subtract(target.position());
            if (direction.lengthSqr() < 1.0E-4D) {
                continue;
            }
            Vec3 pull = direction.normalize().scale(PULL_STRENGTH);
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
    private static void strike(
            ServerPlayer player,
            long nowTick,
            State state,
            float damageMultiplier,
            float critBonus
    ) {
        ServerLevel level = player.serverLevel();
        Vec3 center = state.center;
        List<LivingEntity> targets = snapshotTargets(
                level,
                player,
                effectBounds(center, state.radius)
        );

        for (LivingEntity target : targets) {
            SkillContext context = createStrikeContext(
                    state.skillId,
                    damageMultiplier,
                    critBonus
            );
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            }
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
                state.radius * 0.35D,
                0.5D,
                state.radius * 0.35D,
                0.02D
        );
        level.sendParticles(
                ParticleTypes.SMOKE,
                center.x,
                center.y + 0.5D,
                center.z,
                10,
                state.radius * 0.35D,
                0.3D,
                state.radius * 0.35D,
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

    public static boolean hasState(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
    }

    public static void cancel(ServerPlayer player) {
        if (player != null) {
            ACTIVE.remove(player.getUUID());
        }
    }

    static boolean isValidContext(
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable && sameDimension;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
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

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
