package com.stardew.craft.platform.fabric.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.FabricServerEvents;
import com.stardew.craft.platform.fabric.event.ServerAboutToStartEvent;
import com.stardew.craft.platform.fabric.event.ServerStoppedEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.junit.jupiter.api.Test;

public class FabricServerLifecycleTest {
    private static final EventBus BUS = new EventBus();

    /** Shared by lifecycle tests so the real singleton bridge has one bus and production installation order. */
    public static EventBus installActualBridges() {
        FabricServerLifecycle.install();
        FabricServerEvents.install(BUS);
        return BUS;
    }

    @Test void realFabricStopCallbacksKeepTheCurrentServerUntilDefaultPhaseCleanupFinishes() throws Exception {
        EventBus bus = installActualBridges();
        FabricServerLifecycle.install();
        MinecraftServer first = serverIdentity(), second = serverIdentity();
        List<MinecraftServer> observed = new ArrayList<>(), starting = new ArrayList<>();
        // Observe the actual GAME event delivered by the real FabricServerEvents callback.
        bus.addListener(ServerAboutToStartEvent.class, event -> {
            if (event.getServer() == first || event.getServer() == second) starting.add(FabricServerLifecycle.getCurrentServer());
        });
        bus.addListener(ServerStoppedEvent.class, event -> {
            if (event.getServer() == first || event.getServer() == second) observed.add(FabricServerLifecycle.getCurrentServer());
        });
        for (MinecraftServer server : List.of(first, second)) {
            ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(server);
            assertSame(server, FabricServerLifecycle.getCurrentServer());
            ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
            assertNull(FabricServerLifecycle.getCurrentServer());
        }
        assertEquals(List.of(first, second), starting);
        assertEquals(List.of(first, second), observed,
                "Neo ServerStoppedEvent subscribers can still use the current server during cleanup");
    }

    @Test void aLateStopFromAnOlderServerCannotClearTheReplacementServerReference() throws Exception {
        installActualBridges();
        MinecraftServer old = serverIdentity(), replacement = serverIdentity();
        ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(old);
        ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(replacement);
        ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(old);
        assertSame(replacement, FabricServerLifecycle.getCurrentServer());
        ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(replacement);
        assertNull(FabricServerLifecycle.getCurrentServer());
    }

    /** Identity only: no server constructor, world, files, threads or gameplay run. */
    private static MinecraftServer serverIdentity() throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe");
        var field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (MinecraftServer) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), DedicatedServer.class);
    }
}
