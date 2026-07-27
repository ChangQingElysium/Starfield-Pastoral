package com.stardew.craft.animal.service;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Global per-level budget for managed-animal path calculations.
 *
 * <p>Goals keep their current path when the budget is exhausted and retry later. This prevents a
 * large barn or coop population from requesting every expensive path calculation in one tick.
 */
public final class AnimalPathfindingBudget {
    static final int MAX_REQUESTS_PER_TICK = 24;
    private static final Map<ServerLevel, TickBudget> BUDGETS =
            new WeakHashMap<>();

    private AnimalPathfindingBudget() {
    }

    public static boolean tryAcquire(ServerLevel level) {
        return budget(level).tryAcquire(
                level.getGameTime(), MAX_REQUESTS_PER_TICK);
    }

    private static synchronized TickBudget budget(ServerLevel level) {
        return BUDGETS.computeIfAbsent(
                level, ignored -> new TickBudget());
    }

    static final class TickBudget {
        private long tick = Long.MIN_VALUE;
        private int used;

        boolean tryAcquire(long currentTick, int limit) {
            if (tick != currentTick) {
                tick = currentTick;
                used = 0;
            }
            if (used >= limit) {
                return false;
            }
            used++;
            return true;
        }
    }
}
