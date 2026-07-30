package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.TemplarJudgementImpactPayload;
import com.stardew.craft.combat.network.TemplarMarkPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TemplarJudgementTracker {
    public static final String SKILL_ID = "templar_judgement";
    public static final int DURATION_TICKS = 100;
    public static final float SHARE_RATIO = 0.35F;
    public static final float MAX_HEALTH_DAMAGE_CAP_RATIO = 0.25F;
    public static final float SETTLEMENT_DAMAGE_MULTIPLIER = 1.6F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private record TargetRef(ResourceKey<Level> dimension, UUID entityId) {}

    private static final class State {
        private final ResourceKey<Level> casterDimension;
        private final long endTick;
        private final List<TargetRef> targets;

        private State(
                ResourceKey<Level> casterDimension,
                long endTick,
                List<TargetRef> targets
        ) {
            this.casterDimension = casterDimension;
            this.endTick = endTick;
            this.targets = targets;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private TemplarJudgementTracker() {}

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, long nowTick, int durationTicks, List<LivingEntity> targets) {
        if (player == null || durationTicks <= 0 || targets == null || targets.isEmpty()) {
            return;
        }
        List<TargetRef> targetRefs = new ArrayList<>();
        for (LivingEntity target : targets) {
            targetRefs.add(new TargetRef(
                    target.level().dimension(),
                    target.getUUID()
            ));
        }
        ACTIVE.put(
                player.getUUID(),
                new State(
                        player.level().dimension(),
                        nowTick + durationTicks,
                        List.copyOf(targetRefs)
                )
        );

        if (player.level() instanceof ServerLevel serverLevel) {
            for (LivingEntity target : targets) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                    new TemplarMarkPayload(target.getId(), durationTicks));
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    6, 0.4, 0.2, 0.4, 0.01);
            }
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.7f);
        }
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        return state != null
                && state.casterDimension.equals(player.level().dimension())
                && isWithinActiveWindow(nowTick, state.endTick);
    }

    public static boolean hasState(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    public static List<LivingEntity> getMarkedTargets(Player player) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null
                || !state.casterDimension.equals(player.level().dimension())
                || player.getServer() == null) {
            return List.of();
        }
        List<LivingEntity> targets = new ArrayList<>();
        for (TargetRef targetRef : state.targets) {
            ServerLevel targetLevel = player.getServer().getLevel(
                    targetRef.dimension()
            );
            if (targetLevel == null) {
                continue;
            }
            var entity = targetLevel.getEntity(targetRef.entityId());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                targets.add(living);
            }
        }
        return targets;
    }

    @SuppressWarnings("null")
    public static void tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!state.casterDimension.equals(player.level().dimension())) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (isWithinActiveWindow(nowTick, state.endTick)) {
            return;
        }

        List<LivingEntity> targets = getMarkedTargets(player);
        if (!targets.isEmpty()) {
            for (LivingEntity target : targets) {
                SkillContext context = SkillContext.builder()
                    .skillId(SKILL_ID)
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(SETTLEMENT_DAMAGE_MULTIPLIER)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        nowTick + HIT_CONTEXT_LIFETIME_TICKS
                );

                if (target.level() instanceof ServerLevel serverLevel) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                        new TemplarJudgementImpactPayload(target.getId()));
                    serverLevel.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6f, 0.9f);
                }
            }
        }

        ACTIVE.remove(player.getUUID());
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    public static float cappedSharedDamage(
            float incomingDamage,
            float maximumHealth
    ) {
        float total = incomingDamage * SHARE_RATIO;
        float cap = maximumHealth * MAX_HEALTH_DAMAGE_CAP_RATIO;
        return cap > 0.0F ? Math.min(total, cap) : total;
    }
}
