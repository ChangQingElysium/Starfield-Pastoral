package com.stardew.craft.server.performance;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ServerPerformanceEvents {
    private static final long LOGIN_TIMEOUT_NANOS = 5L * 60L * 1_000_000_000L;
    private static final int MAX_PENDING_LOGINS = 256;
    private static final Map<UUID, Long> LOGIN_STARTS = new HashMap<>();
    private static long serverTickStart;
    private static boolean serverTickActive;

    private ServerPerformanceEvents() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        clearState();
        ServerPerformanceRecorder.disable();
        ServerPerformanceRecorder.reset();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(ServerTickEvent.Pre event) {
        if (!ServerPerformanceRecorder.isEnabled()) return;
        serverTickStart = System.nanoTime();
        serverTickActive = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        if (!serverTickActive) return;
        ServerPerformanceRecorder.record(
                PerformanceTiming.SERVER_TICK,
                Math.max(0L, System.nanoTime() - serverTickStart));
        serverTickStart = 0L;
        serverTickActive = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoginStart(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ServerPerformanceRecorder.isEnabled()) return;
        beginLogin(event.getEntity().getUUID(), System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoginEnd(PlayerEvent.PlayerLoggedInEvent event) {
        Long startedAt = LOGIN_STARTS.remove(event.getEntity().getUUID());
        if (startedAt != null) {
            ServerPerformanceRecorder.record(
                    PerformanceTiming.PLAYER_LOGIN_EVENT,
                    Math.max(0L, System.nanoTime() - startedAt));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOGIN_STARTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearState();
        ServerPerformanceRecorder.disable();
        ServerPerformanceRecorder.reset();
    }

    private static void beginLogin(UUID playerId, long now) {
        Objects.requireNonNull(playerId, "playerId");
        purgeStaleLogins(now);
        if (!LOGIN_STARTS.containsKey(playerId) && LOGIN_STARTS.size() >= MAX_PENDING_LOGINS) {
            evictOldestLogin(now);
        }
        LOGIN_STARTS.put(playerId, now);
    }

    private static void purgeStaleLogins(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = LOGIN_STARTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() >= LOGIN_TIMEOUT_NANOS) iterator.remove();
        }
    }

    private static void evictOldestLogin(long now) {
        UUID oldest = null;
        long longestElapsed = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> entry : LOGIN_STARTS.entrySet()) {
            long elapsed = now - entry.getValue();
            if (oldest == null || elapsed > longestElapsed) {
                oldest = entry.getKey();
                longestElapsed = elapsed;
            }
        }
        if (oldest != null) LOGIN_STARTS.remove(oldest);
    }

    private static void clearState() {
        LOGIN_STARTS.clear();
        serverTickStart = 0L;
        serverTickActive = false;
    }
}
