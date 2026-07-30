package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.FireRingEffectPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TemperedFireRingTracker {
    public static final float DAMAGE_MULTIPLIER = 0.6F;
    public static final float MINIMUM_RADIUS = 0.25F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final class RingState {
        private final Vec3 center;
        private final float maxRadius;
        private final int durationTicks;
        private final long startTick;
        private final float damageMultiplier;
        private final ResourceKey<Level> dimension;
        private final WeaponDamageSnapshot weaponSnapshot;
        private float lastRadius;
        private final Set<UUID> hitTargets = new HashSet<>();

        private RingState(
                Vec3 center,
                float maxRadius,
                int durationTicks,
                long startTick,
                float damageMultiplier,
                ResourceKey<Level> dimension,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.center = center;
            this.maxRadius = maxRadius;
            this.durationTicks = durationTicks;
            this.startTick = startTick;
            this.damageMultiplier = damageMultiplier;
            this.dimension = dimension;
            this.weaponSnapshot = weaponSnapshot;
            this.lastRadius = 0.0f;
        }
    }

    private record BilletCastState(
            ResourceKey<Level> dimension,
            long endTick,
            WeaponDamageSnapshot weaponSnapshot
    ) {}

    private static final Map<UUID, List<RingState>> ACTIVE = new HashMap<>();
    private static final Map<UUID, BilletCastState> BILLET_CASTS = new HashMap<>();

    private TemperedFireRingTracker() {}

    public static void beginBilletCast(
            ServerPlayer player,
            long nowTick,
            int durationTicks
    ) {
        beginBilletCastInternal(player, nowTick, durationTicks, null);
    }

    public static void beginBilletCast(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        beginBilletCastInternal(
                player,
                nowTick,
                durationTicks,
                java.util.Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    private static void beginBilletCastInternal(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        BILLET_CASTS.put(
                player.getUUID(),
                new BilletCastState(
                        player.level().dimension(),
                        nowTick + durationTicks,
                        weaponSnapshot
                )
        );
    }

    public static void cancelBilletCast(Player player) {
        if (player != null) {
            BILLET_CASTS.remove(player.getUUID());
        }
    }

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, Vec3 center, long nowTick, float maxRadius, int durationTicks) {
        startInternal(
                player,
                center,
                nowTick,
                maxRadius,
                durationTicks,
                null
        );
    }

    public static void start(
            ServerPlayer player,
            Vec3 center,
            long nowTick,
            float maxRadius,
            int durationTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                center,
                nowTick,
                maxRadius,
                durationTicks,
                java.util.Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    @SuppressWarnings("null")
    private static void startInternal(
            ServerPlayer player,
            Vec3 center,
            long nowTick,
            float maxRadius,
            int durationTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        BilletCastState castState = activeBilletCast(player, nowTick);
        if (player == null
                || center == null
                || maxRadius <= 0.0f
                || durationTicks <= 0
                || castState == null) {
            return;
        }
        WeaponDamageSnapshot resolvedWeaponSnapshot =
                weaponSnapshot != null
                        ? weaponSnapshot
                        : castState.weaponSnapshot;
        ACTIVE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
            .add(new RingState(
                    center,
                    maxRadius,
                    durationTicks,
                    nowTick,
                    DAMAGE_MULTIPLIER,
                    player.level().dimension(),
                    resolvedWeaponSnapshot
            ));

        if (player.level() instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new FireRingEffectPayload((float) center.x, (float) center.y, (float) center.z, maxRadius, durationTicks));
        }
    }

    public static void tick(ServerPlayer player, long nowTick) {
        clearInvalidBilletCast(player, nowTick);
        List<RingState> rings = ACTIVE.get(player.getUUID());
        if (rings == null || rings.isEmpty()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Iterator<RingState> iterator = rings.iterator();
        while (iterator.hasNext()) {
            RingState ring = iterator.next();
            if (!isSameDimension(
                    ring.dimension,
                    player.level().dimension()
            )) {
                iterator.remove();
                continue;
            }
            long elapsed = nowTick - ring.startTick;
            if (elapsed < 0) {
                continue;
            }

            float radius = radiusAt(
                    elapsed,
                    ring.durationTicks,
                    ring.maxRadius
            );

            if (radius > ring.lastRadius + 0.01f) {
                damageNewTargets(player, serverLevel, ring, radius);
                ring.lastRadius = radius;
            }

            if (elapsed > ring.durationTicks + 2) {
                iterator.remove();
            }
        }

        if (rings.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        }
    }

    @SuppressWarnings("null")
    private static void damageNewTargets(Player owner, ServerLevel level, RingState ring, float radius) {
        AABB box = new AABB(
            ring.center.x - radius, ring.center.y - 1.0, ring.center.z - radius,
            ring.center.x + radius, ring.center.y + 2.0, ring.center.z + radius
        );
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable() && entity.isAlive() && entity != owner
        );

        for (LivingEntity target : targets) {
            double distSqr = target.distanceToSqr(ring.center.x, ring.center.y, ring.center.z);
            if (!isWithinRadius(distSqr, radius)) {
                continue;
            }
            if (!ring.hitTargets.add(target.getUUID())) {
                continue;
            }

            long nowTick = level.getGameTime();
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            SkillContext context = createDamageContext(
                    ring.damageMultiplier
            );
            if (ring.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        owner,
                        target,
                        context,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS
                );
            } else {
                WeaponSkillDamage.apply(
                        owner,
                        target,
                        context,
                        ring.weaponSnapshot,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS
                );
            }
        }
    }

    static float radiusAt(
            long elapsedTicks,
            int durationTicks,
            float maximumRadius
    ) {
        float progress = durationTicks <= 0
                ? 1.0F
                : elapsedTicks / (float) durationTicks;
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.max(MINIMUM_RADIUS, maximumRadius * progress);
    }

    static SkillContext createDamageContext(float damageMultiplier) {
        return SkillContext.builder()
                .skillId("tempered_billet")
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    static boolean isWithinRadius(
            double distanceSquared,
            float radius
    ) {
        return distanceSquared <= radius * radius;
    }

    static boolean isCastActive(
            long endTick,
            long nowTick,
            boolean sameDimension
    ) {
        return sameDimension && nowTick <= endTick;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static boolean hasValidBilletCast(
            ServerPlayer player,
            long nowTick
    ) {
        return activeBilletCast(player, nowTick) != null;
    }

    private static BilletCastState activeBilletCast(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return null;
        }
        BilletCastState state = BILLET_CASTS.get(player.getUUID());
        if (state == null) {
            return null;
        }
        boolean active = isCastActive(
                state.endTick,
                nowTick,
                isSameDimension(
                        state.dimension,
                        player.level().dimension()
                )
        );
        if (!active) {
            BILLET_CASTS.remove(player.getUUID());
        }
        return active ? state : null;
    }

    private static void clearInvalidBilletCast(
            ServerPlayer player,
            long nowTick
    ) {
        hasValidBilletCast(player, nowTick);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
        BILLET_CASTS.remove(playerId);
    }
}
