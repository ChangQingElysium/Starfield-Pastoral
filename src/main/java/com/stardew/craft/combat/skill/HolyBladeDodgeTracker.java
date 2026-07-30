package com.stardew.craft.combat.skill;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HolyBladeDodgeTracker {

    private static final class DodgeState {
        private final float chance;
        private final long endTick;

        private DodgeState(float chance, long endTick) {
            this.chance = chance;
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, DodgeState> ACTIVE = new HashMap<>();

    private HolyBladeDodgeTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks, float chance) {
        if (player == null || durationTicks <= 0 || chance <= 0.0f) {
            return;
        }
        ACTIVE.put(player.getUUID(), new DodgeState(chance, nowTick + durationTicks));
    }

    public static float getDodgeChance(Player player, long nowTick) {
        if (player == null) {
            return 0.0f;
        }
        DodgeState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return 0.0f;
        }
        if (isExpired(nowTick, state.endTick)) {
            ACTIVE.remove(player.getUUID());
            return 0.0f;
        }
        return state.chance;
    }

    public static boolean isActive(Player player, long nowTick) {
        return getDodgeChance(player, nowTick) > 0.0f;
    }

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
