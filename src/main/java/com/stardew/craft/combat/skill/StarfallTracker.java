package com.stardew.craft.combat.skill;

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
import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.network.ShockwaveRingPayload;
import com.stardew.craft.combat.network.StarfallMeteorPayload;
import com.stardew.craft.combat.network.StarfallShockwavePostPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 银河剑 - 星落打击：延迟多段落击
 */
public final class StarfallTracker {

    public enum Status {
        ACTIVE,
        COMPLETED,
        INVALIDATED
    }

    public static final int STRIKE_INTERVAL_TICKS = 10;
    public static final int DEFAULT_STRIKES = 3;
    public static final int MAX_EXTRA_HITS = 3;
    public static final double DEFAULT_RADIUS = 4.0D;
    public static final float DEFAULT_DAMAGE_MULTIPLIER = 0.70F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final class State {
        private long nextStrikeTick;
        private int remainingStrikes;
        private final int extraHits;
        private final double radius;
        private final float damageMultiplier;
        private final String skillId;
        private final ResourceKey<Level> dimension;
        private final WeaponDamageSnapshot weaponSnapshot;

        private State(
                long nextStrikeTick,
                int remainingStrikes,
                int extraHits,
                double radius,
                float damageMultiplier,
                String skillId,
                ResourceKey<Level> dimension,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.nextStrikeTick = nextStrikeTick;
            this.remainingStrikes = remainingStrikes;
            this.extraHits = extraHits;
            this.radius = radius;
            this.damageMultiplier = damageMultiplier;
            this.skillId = skillId;
            this.dimension = dimension;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private StarfallTracker() {}

    public static void start(ServerPlayer player, long nowTick, int strikes, int extraHits, double radius,
                             float damageMultiplier, String skillId) {
        startInternal(
                player,
                nowTick,
                strikes,
                extraHits,
                radius,
                damageMultiplier,
                skillId,
                null
        );
    }

    public static void start(
            ServerPlayer player,
            long nowTick,
            int strikes,
            int extraHits,
            double radius,
            float damageMultiplier,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                nowTick,
                strikes,
                extraHits,
                radius,
                damageMultiplier,
                skillId,
                java.util.Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    private static void startInternal(
            ServerPlayer player,
            long nowTick,
            int strikes,
            int extraHits,
            double radius,
            float damageMultiplier,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || strikes <= 0) {
            return;
        }
        ACTIVE.put(
                player.getUUID(),
                new State(
                        nowTick + STRIKE_INTERVAL_TICKS,
                        strikes,
                        Math.min(
                                MAX_EXTRA_HITS,
                                Math.max(0, extraHits)
                        ),
                        radius,
                        damageMultiplier,
                        skillId,
                        player.level().dimension(),
                        weaponSnapshot
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
        if (!shouldStrike(nowTick, state.nextStrikeTick)) {
            return Status.ACTIVE;
        }

        strike(player, nowTick, state);
        state.remainingStrikes -= 1;
        if (state.remainingStrikes <= 0) {
            ACTIVE.remove(player.getUUID());
            return Status.COMPLETED;
        } else {
            state.nextStrikeTick = nextStrikeTick(nowTick);
        }
        return Status.ACTIVE;
    }

    @SuppressWarnings("null")
    private static void strike(ServerPlayer player, long nowTick, State state) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        AABB box = new AABB(
            center.x - state.radius, center.y - 1.5, center.z - state.radius,
            center.x + state.radius, center.y + 2.0, center.z + state.radius
        );

        List<LivingEntity> targets = List.copyOf(
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable()
                                && entity != player
                )
        );

        int hits = hitsPerTarget(state.extraHits);
        for (LivingEntity target : targets) {
            for (int i = 0; i < hits; i++) {
                SkillContext context = createStrikeContext(
                        state.skillId,
                        state.damageMultiplier
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
        }

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.4f);

        level.sendParticles(ParticleTypes.END_ROD,
            center.x, center.y + 1.2, center.z,
            18, state.radius * 0.35, 0.6, state.radius * 0.35, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT,
            center.x, center.y + 0.8, center.z,
            12, state.radius * 0.35, 0.4, state.radius * 0.35, 0.02);
        level.sendParticles(ParticleTypes.CRIT,
            center.x, center.y + 0.4, center.z,
            16, state.radius * 0.45, 0.35, state.radius * 0.45, 0.08);

        PacketDistributor.sendToPlayersInDimension(level,
            new ShockwaveRingPayload((float) center.x, (float) center.y, (float) center.z, (float) state.radius, 8,
                VfxColors.GALAXY_PURPLE));

        PacketDistributor.sendToPlayersInDimension(level,
            new StarfallMeteorPayload((float) center.x, (float) center.y, (float) center.z, 6.0f, 14,
                VfxColors.GALAXY_PURPLE));

        PacketDistributor.sendToPlayersInDimension(level,
            new StarfallShockwavePostPayload((float) center.x, (float) center.y + 0.2f, (float) center.z, 0.28f, 0.9f, 8));
    }

    public static boolean hasState(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
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

    static boolean shouldStrike(long nowTick, long nextStrikeTick) {
        return nowTick >= nextStrikeTick;
    }

    static long nextStrikeTick(long nowTick) {
        return nowTick + STRIKE_INTERVAL_TICKS;
    }

    static int hitsPerTarget(int extraHits) {
        return 1 + Math.max(0, extraHits);
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

    static SkillContext createStrikeContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }

}
