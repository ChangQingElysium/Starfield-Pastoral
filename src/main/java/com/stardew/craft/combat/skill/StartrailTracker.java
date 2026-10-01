package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.StartrailPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 银河剑 - 星轨共振
 * 普攻与技能命中：+1层；暴击命中：+3层；上限12层
 */
public final class StartrailTracker {

    public static final int MAX_STACKS = 12;

    private static final Map<UUID, Integer> STACKS = new HashMap<>();

    private StartrailTracker() {}

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
        PacketDistributor.sendToPlayer(player, new StartrailPayload(clamped));
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

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        STACKS.remove(playerId);
    }
}
