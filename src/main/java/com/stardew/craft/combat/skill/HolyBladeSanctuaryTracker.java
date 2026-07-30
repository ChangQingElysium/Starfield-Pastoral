package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.HolyBladeRingPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
public final class HolyBladeSanctuaryTracker {
    public static final int PULSE_INTERVAL_TICKS = 20;
    public static final float PULSE_DAMAGE_MULTIPLIER = 0.75F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int HEAL_AMOUNT = 4;
    public static final int RING_DURATION_TICKS = 12;

    private static final class SanctuaryState {
        private final float maxRadius;
        private final long endTick;
        private long nextPulseTick;

        private SanctuaryState(float maxRadius, long endTick, long nextPulseTick) {
            this.maxRadius = maxRadius;
            this.endTick = endTick;
            this.nextPulseTick = nextPulseTick;
        }
    }

    private static final Map<UUID, SanctuaryState> ACTIVE = new HashMap<>();

    private HolyBladeSanctuaryTracker() {}

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, long nowTick, int durationTicks, float maxRadius) {
        if (player == null || durationTicks <= 0 || maxRadius <= 0.0f) {
            return;
        }
        SanctuaryState state = new SanctuaryState(
            maxRadius,
            nowTick + durationTicks,
            nowTick
        );
        ACTIVE.put(player.getUUID(), state);

        HolyBladeEffects.playDomainActivate(player);
    }

    public static void tick(ServerPlayer player, long nowTick) {
        SanctuaryState state = activeState(player, nowTick);
        if (state == null) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (shouldPulse(nowTick, state.nextPulseTick)) {
            state.nextPulseTick += PULSE_INTERVAL_TICKS;

            Vec3 center = player.position();
            float radius = state.maxRadius;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new HolyBladeRingPayload(
                    (float) center.x,
                    (float) center.y,
                    (float) center.z,
                    state.maxRadius,
                    RING_DURATION_TICKS
                ));
            List<LivingEntity> targets = getTargetsInRadius(serverLevel, center, radius, player);

            for (LivingEntity target : targets) {
                SkillContext context = SkillContext.builder()
                    .skillId("holy_domain")
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(PULSE_DAMAGE_MULTIPLIER)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS
                );

                HolyBladeEffects.playDomainPulse(serverLevel, target);
            }

            HolyBladeEffects.playHeal(player, HEAL_AMOUNT);
        }
    }

    public static boolean isActive(Player player, long nowTick) {
        return activeState(player, nowTick) != null;
    }

    public static void stop(Player player) {
        if (player != null) {
            ACTIVE.remove(player.getUUID());
        }
    }

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static boolean shouldPulse(long nowTick, long nextPulseTick) {
        return nowTick >= nextPulseTick;
    }

    private static SanctuaryState activeState(Player player, long nowTick) {
        if (player == null) {
            return null;
        }
        SanctuaryState state = ACTIVE.get(player.getUUID());
        if (state != null && isExpired(nowTick, state.endTick)) {
            ACTIVE.remove(player.getUUID());
            return null;
        }
        return state;
    }

    private static List<LivingEntity> getTargetsInRadius(ServerLevel level, Vec3 center, float radius, Player owner) {
        AABB box = new AABB(
            center.x - radius, center.y - radius * 0.6, center.z - radius,
            center.x + radius, center.y + radius * 0.6, center.z + radius
        );
        return level.getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable() && entity.isAlive() && entity != owner);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
