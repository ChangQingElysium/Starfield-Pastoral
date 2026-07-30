package com.stardew.craft.combat.skill;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DarkSwordBloodDebtTracker {

    private static final class State {
        private final long endTick;

        private State(long endTick) {
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    public static final int ACTIVE_DURATION_TICKS = 100;
    public static final float LIFESTEAL_RATIO = 0.20F;

    private DarkSwordBloodDebtTracker() {}

    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.put(player.getUUID(), new State(nowTick + durationTicks));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) return false;
        State state = ACTIVE.get(player.getUUID());
        return state != null && nowTick <= state.endTick;
    }

    public static float getLifestealRatio(ServerPlayer player, long nowTick) {
        return isActive(player, nowTick) ? LIFESTEAL_RATIO : 0.0f;
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) return;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        if (nowTick > state.endTick) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static boolean isTracked(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
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
}
