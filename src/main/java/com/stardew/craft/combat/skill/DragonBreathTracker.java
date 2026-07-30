package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.DragonBreathPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 龙牙弯刀 - 龙息积攒
 * 普攻命中：+1层；暴击命中：+3层；上限20层
 */
public final class DragonBreathTracker {

    public static final int MAX_STACKS = 20;
    public static final int MAJOR_THRESHOLD = 15;

    // Stacks are a player combat resource and intentionally survive dimension
    // travel. Only the spatially active thrust below is dimension-bound.
    private static final Map<UUID, Integer> STACKS = new HashMap<>();
    private static final Map<UUID, ThrustState> ACTIVE_THRUSTS =
            new HashMap<>();

    private record ThrustState(
            long endTick,
            ResourceKey<Level> originDimension
    ) {}

    private DragonBreathTracker() {}

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
        int clamped = clampStacks(stacks);
        if (clamped == 0) {
            STACKS.remove(player.getUUID());
        } else {
            STACKS.put(player.getUUID(), clamped);
        }
        PacketDistributor.sendToPlayer(player, new DragonBreathPayload(clamped));
    }

    public static void addStacks(ServerPlayer player, int delta) {
        if (player == null || delta == 0) {
            return;
        }
        int current = getStacks(player);
        setStacks(player, stacksAfterDelta(current, delta));
    }

    public static int consumeAll(ServerPlayer player) {
        int current = getStacks(player);
        setStacks(player, 0);
        return current;
    }

    public static int consumeForMajor(ServerPlayer player) {
        int current = getStacks(player);
        int consumed = consumableMajorStacks(current);
        if (consumed == 0) {
            return 0;
        }
        setStacks(player, 0);
        return consumed;
    }

    public static boolean canCastMajor(ServerPlayer player) {
        return canCastMajor(getStacks(player));
    }

    public static void beginThrust(
            ServerPlayer player,
            long nowTick,
            int durationTicks
    ) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        ACTIVE_THRUSTS.put(
                player.getUUID(),
                new ThrustState(
                        nowTick + durationTicks,
                        player.level().dimension()
                )
        );
    }

    public static void tickThrust(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        ThrustState state = ACTIVE_THRUSTS.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!shouldRemainThrustActive(
                state.endTick,
                nowTick,
                player.isAlive() && !player.isRemoved(),
                state.originDimension.equals(player.level().dimension())
        )) {
            ACTIVE_THRUSTS.remove(player.getUUID());
        }
    }

    public static boolean hasThrustState(UUID playerId) {
        return ACTIVE_THRUSTS.containsKey(playerId);
    }

    public static boolean isThrustBoundToCurrentContext(
            ServerPlayer player
    ) {
        ThrustState state = ACTIVE_THRUSTS.get(player.getUUID());
        return state != null
                && player.isAlive()
                && !player.isRemoved()
                && state.originDimension.equals(
                        player.level().dimension()
                );
    }

    public static void clearThrust(ServerPlayer player) {
        if (player != null) {
            ACTIVE_THRUSTS.remove(player.getUUID());
        }
    }

    static int clampStacks(int stacks) {
        return Mth.clamp(stacks, 0, MAX_STACKS);
    }

    static int stacksAfterDelta(int current, int delta) {
        return clampStacks(current + delta);
    }

    static boolean canCastMajor(int stacks) {
        return clampStacks(stacks) >= MAJOR_THRESHOLD;
    }

    static int consumableMajorStacks(int stacks) {
        int clamped = clampStacks(stacks);
        return clamped >= MAJOR_THRESHOLD ? clamped : 0;
    }

    static boolean shouldRemainThrustActive(
            long endTick,
            long nowTick,
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable
                && sameDimension
                && nowTick <= endTick;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        STACKS.remove(playerId);
        ACTIVE_THRUSTS.remove(playerId);
    }
}
