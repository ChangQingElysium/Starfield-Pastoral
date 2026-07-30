package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class LavaKatanaReverbTracker {

    public enum Status {
        ACTIVE,
        COMPLETED,
        INVALIDATED
    }

    public static final String FINISHER_SKILL_ID =
            "lava_katana_finisher";
    public static final int ACTIVE_DURATION_TICKS = 80;
    public static final double TARGET_RANGE = 8.0D;
    public static final int MINIMUM_HEAT = 5;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final float FINISHER_BASE_SCALAR = 1.5F;
    public static final float FINISHER_HEAT_SCALAR = 0.05F;

    private static final class State {
        private final ResourceKey<Level> dimension;
        private final long endTick;
        private final WeaponDamageSnapshot weaponSnapshot;

        private State(
            ResourceKey<Level> dimension,
            long endTick,
            WeaponDamageSnapshot weaponSnapshot
        ) {
            this.dimension = dimension;
            this.endTick = endTick;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private LavaKatanaReverbTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks) {
        if (player == null) {
            return;
        }
        start(
            player,
            nowTick,
            durationTicks,
            WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "lava_katana"
                ),
                player.getMainHandItem()
            )
        );
    }

    public static void start(
        ServerPlayer player,
        long nowTick,
        int durationTicks,
        WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.put(
            player.getUUID(),
            new State(
                player.level().dimension(),
                nowTick + durationTicks,
                Objects.requireNonNull(
                    weaponSnapshot,
                    "weaponSnapshot"
                )
            )
        );
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) return false;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return false;
        }
        Status status = statusForSnapshot(
            isSameDimension(
                state.dimension,
                player.level().dimension()
            ),
            nowTick,
            state.endTick
        );
        if (status == Status.INVALIDATED) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        return status == Status.ACTIVE;
    }

    public static boolean hasState(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    public static Status tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return Status.INVALIDATED;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return Status.INVALIDATED;
        }
        Status status = statusForSnapshot(
            isSameDimension(
                state.dimension,
                player.level().dimension()
            ),
            nowTick,
            state.endTick
        );
        if (status == Status.INVALIDATED) {
            ACTIVE.remove(player.getUUID());
            return Status.INVALIDATED;
        }
        if (status == Status.ACTIVE) {
            return Status.ACTIVE;
        }

        try {
            finish(player, nowTick, state);
        } finally {
            ACTIVE.remove(player.getUUID());
        }
        return Status.COMPLETED;
    }

    @SuppressWarnings("null")
    private static void finish(
        ServerPlayer player,
        long nowTick,
        State state
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Set<UUID> marked = LavaKatanaMarkTracker.getMarkedTargets(player.getUUID());
        if (marked.isEmpty()) {
            return;
        }

        for (UUID targetId : marked) {
            if (targetId == null) {
                continue;
            }
            net.minecraft.world.entity.Entity entity = level.getEntity(targetId);
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            if (!LavaKatanaMarkTracker.isMarkedBy(target, player, nowTick)) {
                continue;
            }

            int remainingTicks = LavaKatanaMarkTracker.getRemainingTicks(target, nowTick);
            int remainingJumps = remainingBurnJumps(remainingTicks);
            if (remainingJumps <= 0) {
                LavaKatanaMarkTracker.clearMark(target);
                continue;
            }

            int heat = LavaKatanaMarkTracker.getHeat(target);
            float finisherMultiplier =
                finisherDamageMultiplier(remainingTicks, heat);

            WeaponStats weaponStats =
                weaponStatsFromSnapshot(state.weaponSnapshot);
            if (weaponStats.getAverageDamage() <= 0.0f) {
                LavaKatanaMarkTracker.clearMark(target);
                continue;
            }

            target.invulnerableTime = 0;
            target.hurtTime = 0;
            WeaponSkillDamage.apply(
                player,
                target,
                createFinisherContext(finisherMultiplier),
                state.weaponSnapshot,
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
            );

            playFinisherImpact(level, target);
            LavaKatanaMarkTracker.clearMark(target);
        }
    }

    @SuppressWarnings("null")
    private static void playFinisherImpact(ServerLevel level, LivingEntity target) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
            x, y, z,
            1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
            x, y, z,
            28, 0.55, 0.3, 0.55, 0.08);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
            x, y, z,
            30, 0.6, 0.32, 0.6, 0.08);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
            x, y, z,
            18, 0.45, 0.25, 0.45, 0.12);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
            x, y, z,
            16, 0.5, 0.25, 0.5, 0.02);
        level.playSound(null, target.blockPosition(),
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, target.blockPosition(),
            SoundEvents.BLAZE_SHOOT,
            SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, target.blockPosition(),
            SoundEvents.FIRECHARGE_USE,
            SoundSource.PLAYERS, 0.9f, 0.7f);
    }

    public static List<LivingEntity> findMarkedTargetsInRange(ServerLevel level, Player owner, long nowTick, double range) {
        AABB box = new AABB(
            owner.getX() - range, owner.getY() - range, owner.getZ() - range,
            owner.getX() + range, owner.getY() + range, owner.getZ() + range
        );
        return level.getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable()
                && entity.isAlive()
                && entity != owner
                && LavaKatanaMarkTracker.isMarkedBy(entity, owner, nowTick));
    }

    public static void cancel(ServerPlayer player) {
        if (player != null) {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick <= endTick;
    }

    static Status statusForSnapshot(
        boolean sameDimension,
        long nowTick,
        long endTick
    ) {
        if (!sameDimension) {
            return Status.INVALIDATED;
        }
        return isWithinActiveWindow(nowTick, endTick)
            ? Status.ACTIVE
            : Status.COMPLETED;
    }

    static boolean isSameDimension(
        ResourceKey<Level> expected,
        ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static int remainingBurnJumps(int remainingTicks) {
        return (int) Math.max(
            0L,
            ((long) remainingTicks
                + LavaKatanaMarkTracker.BURN_INTERVAL_TICKS
                - 1L)
                / LavaKatanaMarkTracker.BURN_INTERVAL_TICKS
        );
    }

    static float finisherDamageMultiplier(
        int remainingTicks,
        int heat
    ) {
        int nonNegativeHeat = Math.max(0, heat);
        float baseJumpRatio =
            LavaKatanaMarkTracker.BASE_BURN_RATIO
                + nonNegativeHeat
                * LavaKatanaMarkTracker.HEAT_BONUS_REVERB_RATIO;
        return remainingBurnJumps(remainingTicks)
            * baseJumpRatio
            * (FINISHER_BASE_SCALAR
                + FINISHER_HEAT_SCALAR * nonNegativeHeat);
    }

    static SkillContext createFinisherContext(float damageMultiplier) {
        return SkillContext.builder()
            .skillId(FINISHER_SKILL_ID)
            .tier(SkillContext.SkillTier.MAJOR)
            .damageMultiplier(damageMultiplier)
            .build();
    }

    static WeaponStats weaponStatsFromSnapshot(
        WeaponDamageSnapshot weaponSnapshot
    ) {
        return WeaponStats.fromItemStack(
            Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
            ).weapon()
        );
    }
}
