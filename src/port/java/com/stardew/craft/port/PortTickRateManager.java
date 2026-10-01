package com.stardew.craft.port;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 1.20.3+ {@code ServerTickRateManager} sprint ({@code /tick sprint}), the only part the mod uses
 * ({@code server.tickRateManager().requestGameToSprint(ticks)}). {@code PortMinecraftServerSprintMixin} calls
 * {@link #beginTick}/{@link #endTick} around {@code tickServer}: a sprint tick does not wait for the 50 ms tick
 * schedule (like 1.21's run loop), and the finished sprint reports to ops/console as {@code finishTickSprint}
 * does. 1.20.1 has no tick freezing, so the frozen state 1.21 saves/restores around a sprint does not apply.
 */
public final class PortTickRateManager {
    private static final Map<MinecraftServer, PortTickRateManager> MANAGERS = new WeakHashMap<>();

    private final MinecraftServer server;
    private long remainingSprintTicks;
    private long scheduledCurrentSprintTicks;
    private long sprintTickStartTime;
    private long sprintTimeSpend;
    private boolean sprintTick;

    private PortTickRateManager(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized PortTickRateManager of(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, PortTickRateManager::new);
    }

    /** 1.21 {@code ServerTickRateManager#requestGameToSprint}: returns whether a sprint was interrupted. */
    public boolean requestGameToSprint(int sprintTime) {
        boolean interrupted = remainingSprintTicks > 0L;
        sprintTimeSpend = 0L;
        scheduledCurrentSprintTicks = sprintTime;
        remainingSprintTicks = sprintTime;
        return interrupted;
    }

    public boolean isSprinting() {
        return scheduledCurrentSprintTicks > 0L;
    }

    /** 1.21 {@code checkShouldSprintThisTick} (paused integrated servers never sprint, as in 1.21). */
    public boolean beginTick() {
        sprintTick = false;
        if (!isSprinting() || PortLevels.isPaused(server)) return false;
        if (remainingSprintTicks > 0L) {
            sprintTickStartTime = System.nanoTime();
            remainingSprintTicks--;
            sprintTick = true;
            return true;
        }
        finishTickSprint();
        return false;
    }

    /** 1.21 {@code endTickWork}. */
    public void endTick() {
        if (sprintTick) sprintTimeSpend += System.nanoTime() - sprintTickStartTime;
        sprintTick = false;
    }

    private void finishTickSprint() {
        long ticks = scheduledCurrentSprintTicks - remainingSprintTicks;
        double millis = Math.max(1.0, (double) sprintTimeSpend) / 1_000_000.0;
        int ticksPerSecond = (int) (1000.0 * ticks / millis);
        String millisPerTick = String.format("%.2f", ticks == 0L ? 50.0 : millis / ticks);
        scheduledCurrentSprintTicks = 0L;
        sprintTimeSpend = 0L;
        server.createCommandSourceStack().sendSuccess(() -> Component.translatableWithFallback(
                "commands.tick.sprint.report",
                "Sprint completed with %s ticks per second, or %s ms per tick", ticksPerSecond, millisPerTick), true);
        remainingSprintTicks = 0L;
    }
}
