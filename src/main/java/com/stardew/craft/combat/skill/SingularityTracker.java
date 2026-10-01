package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.SingularityPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 无限之刃 - 无限核心
 * 普攻与技能命中：+1层；暴击命中：+3层；上限20层
 */
public final class SingularityTracker {

    public static final int MAX_STACKS = 20;
    public static final int EVOLVE_THRESHOLD = 12;

    private static final Map<UUID, Integer> STACKS = new HashMap<>();

    private SingularityTracker() {}

    public static int getStacks(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return STACKS.getOrDefault(player.getUUID(), 0);
    }

    public static void setStacks(ServerPlayer player, int stacks) {
        if (player == null) {
            return;
        }
        int clamped = Mth.clamp(stacks, 0, MAX_STACKS);
        STACKS.put(player.getUUID(), clamped);
        PacketDistributor.sendToPlayer(player, new SingularityPayload(clamped));
    }

    public static void addStacks(ServerPlayer player, int delta) {
        if (player == null || delta == 0) {
            return;
        }
        int current = getStacks(player);
        setStacks(player, current + delta);
    }

    public static int consumeAll(ServerPlayer player) {
        int current = getStacks(player);
        setStacks(player, 0);
        return current;
    }

    public static boolean isEvolved(ServerPlayer player) {
        return getStacks(player) >= EVOLVE_THRESHOLD;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        STACKS.remove(playerId);
    }
}
