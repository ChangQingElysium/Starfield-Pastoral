package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.DwarfDaggerRushPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DwarfDaggerRushTracker {
    public static final int THRUST_COOLDOWN_REFRESH_TICKS = 0;

    private static final class State {
        private final long endTick;
        private State(long endTick) {
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DwarfDaggerRushTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.put(player.getUUID(), new State(nowTick + durationTicks));
        PacketDistributor.sendToPlayer(player, new DwarfDaggerRushPayload(true, durationTicks));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) return false;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return false;
        if (!isWithinActiveWindow(nowTick, state.endTick)) {
            ACTIVE.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new DwarfDaggerRushPayload(false, 0));
            return false;
        }
        return true;
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        State removed = ACTIVE.remove(player.getUUID());
        if (removed != null) {
            PacketDistributor.sendToPlayer(player, new DwarfDaggerRushPayload(false, 0));
        }
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
