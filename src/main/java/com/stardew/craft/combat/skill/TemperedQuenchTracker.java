package com.stardew.craft.combat.skill;

import com.stardew.craft.effect.ModMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TemperedQuenchTracker {
    public static final float BLAST_DAMAGE_MULTIPLIER = 0.45F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int VULNERABLE_DURATION_TICKS = 60;
    public static final int VULNERABLE_AMPLIFIER = 1;

    private static final class State {
        private final long triggerTick;
        private final UUID targetId;
        private final ResourceKey<Level> dimension;
        private final WeaponDamageSnapshot weaponSnapshot;

        private State(
                long triggerTick,
                UUID targetId,
                ResourceKey<Level> dimension,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.triggerTick = triggerTick;
            this.targetId = targetId;
            this.dimension = dimension;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> PENDING = new HashMap<>();

    private TemperedQuenchTracker() {}

    public static void start(ServerPlayer player, LivingEntity target, long nowTick, int delayTicks) {
        startInternal(player, target, nowTick, delayTicks, null);
    }

    public static void start(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            int delayTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                target,
                nowTick,
                delayTicks,
                java.util.Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    private static void startInternal(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            int delayTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || target == null) {
            return;
        }
        long triggerTick = nowTick + Math.max(1, delayTicks);
        PENDING.put(
                player.getUUID(),
                new State(
                        triggerTick,
                        target.getUUID(),
                        player.level().dimension(),
                        weaponSnapshot
                )
        );
    }

    @SuppressWarnings("null")
    public static void tick(ServerPlayer player, long nowTick) {
        State state = activeState(player);
        if (state == null) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!shouldTrigger(nowTick, state.triggerTick)) {
            return;
        }

        PENDING.remove(player.getUUID());

        LivingEntity target = resolveTarget(serverLevel, state.targetId);
        if (target == null) {
            return;
        }

        explode(
                player,
                serverLevel,
                nowTick,
                target,
                state.weaponSnapshot
        );
    }

    @SuppressWarnings("null")
    private static void explode(
            ServerPlayer player,
            ServerLevel level,
            long nowTick,
            LivingEntity target,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        Vec3 center = target.position();

        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.9f);
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7f, 0.9f);

        level.sendParticles(ParticleTypes.FLAME,
            center.x, center.y + 0.2, center.z,
            18, 0.8, 0.2, 0.8, 0.02);
        level.sendParticles(ParticleTypes.LAVA,
            center.x, center.y + 0.15, center.z,
            8, 0.5, 0.15, 0.5, 0.01);
        level.sendParticles(ParticleTypes.SMOKE,
            center.x, center.y + 0.1, center.z,
            10, 0.7, 0.1, 0.7, 0.02);

        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    createBlastContext(),
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
            );
        } else {
            WeaponSkillDamage.apply(
                    player,
                    target,
                    createBlastContext(),
                    weaponSnapshot,
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
            );
        }

        target.addEffect(new MobEffectInstance(
            ModMobEffects.VULNERABLE,
            VULNERABLE_DURATION_TICKS,
            VULNERABLE_AMPLIFIER,
            false,
            true,
            true
        ));
    }

    public static boolean isPending(Player player) {
        return activeState(player) != null;
    }

    public static void stop(Player player) {
        if (player != null) {
            PENDING.remove(player.getUUID());
        }
    }

    static SkillContext createBlastContext() {
        return SkillContext.builder()
            .skillId("tempered_quench_blast")
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(BLAST_DAMAGE_MULTIPLIER)
            .build();
    }

    static boolean shouldTrigger(long nowTick, long triggerTick) {
        return nowTick >= triggerTick;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static State activeState(Player player) {
        if (player == null) {
            return null;
        }
        State state = PENDING.get(player.getUUID());
        if (state != null && !isSameDimension(
                state.dimension,
                player.level().dimension()
        )) {
            PENDING.remove(player.getUUID());
            return null;
        }
        return state;
    }

    private static LivingEntity resolveTarget(
            ServerLevel level,
            UUID targetId
    ) {
        Entity entity = level.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive()
                ? living
                : null;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        PENDING.remove(playerId);
    }
}
