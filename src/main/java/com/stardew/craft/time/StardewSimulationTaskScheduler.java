package com.stardew.craft.time;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs delayed gameplay work against a level's pause-aware simulation game time. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class StardewSimulationTaskScheduler {

    private static final List<ScheduledTask> TASKS = new ArrayList<>();

    private StardewSimulationTaskScheduler() {}

    public static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        if (level == null || action == null) {
            return;
        }
        long dueTick = level.getGameTime() + Math.max(0, delayTicks);
        TASKS.add(new ScheduledTask(level.getServer(), level.dimension(), dueTick, action));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        List<Runnable> ready = new ArrayList<>();
        Iterator<ScheduledTask> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            if (task.server() != server) {
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(task.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            boolean paused = StardewTimePauseService.shouldPauseLevel(level);
            if (!isDue(paused, level.getGameTime(), task.dueTick())) {
                continue;
            }
            iterator.remove();
            ready.add(task.action());
        }

        for (Runnable action : ready) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error("Failed to run pause-aware Stardew simulation task", exception);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    static boolean isDue(boolean simulationPaused, long currentTick, long dueTick) {
        return !simulationPaused && currentTick >= dueTick;
    }

    private record ScheduledTask(
        MinecraftServer server,
        ResourceKey<Level> dimension,
        long dueTick,
        Runnable action
    ) {}
}
