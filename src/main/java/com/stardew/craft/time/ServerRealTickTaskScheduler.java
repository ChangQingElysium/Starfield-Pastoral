package com.stardew.craft.time;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Delayed work driven by the real server tick counter.
 *
 * <p>This is intentionally independent of Stardew simulation pause. Collapse
 * screens can pause the shared world, but their presentation deadlines still
 * have to elapse. {@code MinecraftServer.tell(TickTask)} is only a task-queue
 * priority hint and may run a future-labelled task immediately.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ServerRealTickTaskScheduler {
    private static final List<ScheduledTask> TASKS = new ArrayList<>();

    private ServerRealTickTaskScheduler() {
    }

    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        if (server == null || action == null) {
            return;
        }
        TASKS.add(new ScheduledTask(
                server,
                server.getTickCount() + Math.max(0, delayTicks),
                action));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        List<Runnable> ready = new ArrayList<>();
        Iterator<ScheduledTask> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            if (task.server() != server) {
                continue;
            }
            if (!isDue(currentTick, task.dueTick())) {
                continue;
            }
            iterator.remove();
            ready.add(task.action());
        }
        for (Runnable action : ready) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error("Failed to run real-tick server task", exception);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.removeIf(task -> task.server() == event.getServer());
    }

    static boolean isDue(int currentTick, int dueTick) {
        return currentTick - dueTick >= 0;
    }

    private record ScheduledTask(
            MinecraftServer server,
            int dueTick,
            Runnable action
    ) {
    }
}
