package com.stardew.craft.server.performance;

import com.stardew.craft.StardewCraft;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Records a stalled integrated server too, without terminating it or touching its world off-thread. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ServerStallDiagnostics {
    private static final long SAMPLE_INTERVAL = TimeUnit.SECONDS.toNanos(10);
    private static volatile Monitor active;

    private ServerStallDiagnostics() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        Monitor previous = active;
        if (previous != null) previous.executor.shutdownNow();
        Monitor monitor = new Monitor(event.getServer());
        active = monitor;
        monitor.executor.scheduleWithFixedDelay(monitor::sample, 2, 2, TimeUnit.SECONDS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickStarted(ServerTickEvent.Pre event) {
        heartbeat(event.getServer(), "tick");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickFinished(ServerTickEvent.Post event) {
        heartbeat(event.getServer(), "between ticks / queued tasks");
    }

    private static void heartbeat(MinecraftServer server, String phase) {
        Monitor monitor = active;
        if (monitor != null && monitor.server == server) {
            monitor.heartbeat = new Heartbeat(System.nanoTime(), server.getTickCount(), phase);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Monitor monitor = active;
        if (monitor != null && monitor.server == event.getServer()) {
            heartbeat(event.getServer(), "stopping / saving");
            monitor.stopping = true;
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        Monitor monitor = active;
        if (monitor != null && monitor.server == event.getServer()) {
            active = null;
            monitor.executor.shutdownNow();
        }
    }

    /** Called on the server thread; the sampler only reads this immutable description. */
    public static void teleportStarted(ServerPlayer player, ServerLevel target,
                                       double x, double y, double z) {
        Monitor monitor = active;
        if (monitor == null || monitor.server != player.server) return;
        monitor.lastTeleport = "tick=" + player.server.getTickCount() + ", player=" + player.getUUID()
                + ", from=" + player.level().dimension().location() + " " + player.position()
                + ", to=" + target.dimension().location() + " [" + x + ", " + y + ", " + z + "]";
    }

    public static void teleportReturned(MinecraftServer server) {
        Monitor monitor = active;
        if (monitor != null && monitor.server == server) monitor.lastTeleport += ", returned=true";
    }

    private record Heartbeat(long nanos, int tick, String phase) {}

    private static final class Monitor {
        private final MinecraftServer server;
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Stardew server stall diagnostics");
            thread.setDaemon(true);
            return thread;
        });
        private volatile Heartbeat heartbeat = new Heartbeat(System.nanoTime(), 0, "started");
        private volatile String lastTeleport = "none";
        private volatile boolean stopping;
        private volatile boolean pauseProbePending;
        private long pauseProbeStarted;
        private Heartbeat sampledHeartbeat;
        private int samples;

        private Monitor(MinecraftServer server) {
            this.server = server;
        }

        private void sample() {
            try {
                Heartbeat current = heartbeat;
                if (sampledHeartbeat != current) {
                    sampledHeartbeat = current;
                    samples = 0;
                }
                if (active != this || samples >= 3) return;
                long since = current.nanos();
                if (!stopping && com.stardew.craft.port.PortLevels.isPaused(server)) {
                    // Paused integrated servers skip tick events but still drain their task queue.
                    // Probe that queue once, so a normal pause stays quiet while a stuck pause-save is captured.
                    if (!pauseProbePending) {
                        pauseProbeStarted = System.nanoTime();
                        pauseProbePending = true;
                        server.tell(new TickTask(0, () -> {
                            heartbeat = new Heartbeat(System.nanoTime(), server.getTickCount(), "paused / queued tasks");
                            pauseProbePending = false;
                        }));
                        return;
                    }
                    since = Math.max(since, pauseProbeStarted);
                }
                long elapsed = System.nanoTime() - since;
                if (elapsed < SAMPLE_INTERVAL * (samples + 1)) return;
                // No chunk queries, player-list iteration, blocking server calls or world locks here.
                ThreadInfo info = ManagementFactory.getThreadMXBean()
                        .getThreadInfo(server.getRunningThread().getId(), 128);
                if (info == null || heartbeat != current) return;
                samples++;
                StringBuilder stack = new StringBuilder();
                for (StackTraceElement frame : info.getStackTrace()) stack.append("\n\tat ").append(frame);
                StardewCraft.LOGGER.error(
                        "[SERVER_STALL] No tick progress for {} ms; tick={}; phase={}; sample={}/3; "
                                + "threadState={}; waitingOn={}; lockOwner={}; lastTeleport={}{}",
                        TimeUnit.NANOSECONDS.toMillis(elapsed), current.tick(), current.phase(), samples,
                        info.getThreadState(), info.getLockName(), info.getLockOwnerName(), lastTeleport, stack);
            } catch (RuntimeException exception) {
                // A diagnostic failure must not silently cancel all subsequent scheduled samples.
                StardewCraft.LOGGER.warn("[SERVER_STALL] Could not capture server stack", exception);
            }
        }
    }
}
