package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.IridiumNeedleCritPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

public final class IridiumNeedleCritTracker {

    public static final int MAX_STACKS = 3;
    public static final int GUARANTEED_CRIT_THRESHOLD = MAX_STACKS - 1;
    private static final Map<UUID, Integer> STACKS = new HashMap<>();

    private IridiumNeedleCritTracker() {}

    public static int getStacks(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return STACKS.getOrDefault(player.getUUID(), 0);
    }

    public static boolean shouldGuaranteeCrit(ServerPlayer player) {
        return guaranteesCritAtStacks(getStacks(player));
    }

    public static void recordHit(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int current = getStacks(player);
        int next = nextStacks(current);
        if (next == 0) {
            STACKS.remove(player.getUUID());
        } else {
            STACKS.put(player.getUUID(), next);
        }
        sync(player, next);
    }

    static boolean guaranteesCritAtStacks(int stacks) {
        return stacks >= GUARANTEED_CRIT_THRESHOLD;
    }

    static int nextStacks(int current) {
        return (current + 1) % MAX_STACKS;
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        STACKS.remove(player.getUUID());
        sync(player, 0);
    }

    private static void sync(ServerPlayer player, int stacks) {
        ServerPlayer target = Objects.requireNonNull(player, "player");
        PacketDistributor.sendToPlayer(target, new IridiumNeedleCritPayload(stacks));
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        STACKS.remove(playerId);
    }
}
