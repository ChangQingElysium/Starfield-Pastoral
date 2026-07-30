package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.InsectEyeStancePayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 昆虫头部 - 复眼架势：1.5秒内首击必暴
 */
public final class InsectEyeStanceTracker {
    public static final float DAMAGE_MULTIPLIER = 1.05F;

    private static final class State {
        private long endTick;
        private final ResourceKey<Level> originDimension;
        private final String weaponId;
        private final String skillId;
        private final int cooldownTicks;
        private boolean cooldownApplied;
        private boolean firstHitPending;

        private State(
                long endTick,
                ResourceKey<Level> originDimension,
                String weaponId,
                String skillId,
                int cooldownTicks
        ) {
            this.endTick = endTick;
            this.originDimension = originDimension;
            this.weaponId = weaponId;
            this.skillId = skillId;
            this.cooldownTicks = cooldownTicks;
            this.cooldownApplied = false;
            this.firstHitPending = true;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private InsectEyeStanceTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks, String weaponId, String skillId, int cooldownTicks) {
        if (player == null || durationTicks <= 0 || weaponId == null || skillId == null) {
            return;
        }
        ACTIVE.put(
            player.getUUID(),
            new State(
                nowTick + durationTicks,
                player.level().dimension(),
                weaponId,
                skillId,
                cooldownTicks
            )
        );
        PacketDistributor.sendToPlayer(player, new InsectEyeStancePayload(true, durationTicks));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) return false;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return false;
        }
        boolean sameDimension = state.originDimension.equals(player.level().dimension());
        if (!shouldRemainActive(state.endTick, nowTick, sameDimension)) {
            finish(player, state, nowTick);
            return false;
        }
        return true;
    }

    public static SkillContext getSkillContext(ServerPlayer player, long nowTick) {
        if (!isActive(player, nowTick)) {
            return null;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return null;
        }

        boolean guaranteedCrit = state.firstHitPending;
        if (state.firstHitPending) {
            state.firstHitPending = false;
        }

        return createSkillContext(state.skillId, guaranteedCrit);
    }

    public static void tick(ServerPlayer player, long nowTick) {
        isActive(player, nowTick);
    }

    private static void applyCooldown(ServerPlayer player, State state, long nowTick) {
        if (!state.cooldownApplied && state.cooldownTicks > 0) {
            WeaponSkillCooldowns.setCooldown(player, state.weaponId, state.skillId, nowTick, state.cooldownTicks);
            state.cooldownApplied = true;
        }
    }

    private static void finish(ServerPlayer player, State state, long nowTick) {
        try {
            applyCooldown(player, state, nowTick);
            PacketDistributor.sendToPlayer(player, new InsectEyeStancePayload(false, 0));
        } finally {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static void cancel(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state != null) {
            finish(player, state, nowTick);
        }
    }

    static SkillContext createSkillContext(String skillId, boolean guaranteedCrit) {
        return SkillContext.builder()
            .skillId(skillId)
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(DAMAGE_MULTIPLIER)
            .guaranteedCrit(guaranteedCrit)
            .build();
    }

    static boolean shouldRemainActive(
            long endTick,
            long nowTick,
            boolean sameDimension
    ) {
        return sameDimension && nowTick <= endTick;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
