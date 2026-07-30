package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.DragontoothShivBreathPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DragontoothShivBreathTracker {
    public static final int ACTIVE_DURATION_TICKS = 120;

    private static final class State {
        private final long endTick;
        private State(long endTick) {
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DragontoothShivBreathTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.put(player.getUUID(), new State(nowTick + durationTicks));
        PacketDistributor.sendToPlayer(player, new DragontoothShivBreathPayload(true, durationTicks));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) return false;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return false;
        if (!player.isAlive()
                || player.isRemoved()
                || nowTick > state.endTick) {
            clear(player);
            return false;
        }
        return true;
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) return;
        State state = ACTIVE.get(player.getUUID());
        if (state != null && (!player.isAlive()
                || player.isRemoved()
                || nowTick > state.endTick)) {
            clear(player);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        if (ACTIVE.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new DragontoothShivBreathPayload(false, 0)
            );
        }
    }

    public static boolean hasState(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
