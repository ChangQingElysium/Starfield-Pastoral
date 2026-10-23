package com.stardew.craft.platform.fabric.event;

import com.stardew.craft.platform.mixin.event.mixin.MinecraftServerTimeAccessor;
import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Real Fabric API lifecycle callbacks; no client classes are referenced by this bridge. */
public final class FabricServerEvents {
    private static IEventBus installedBus;

    private FabricServerEvents() {}

    public static void install() {
        install(FabricEvents.GAME);
    }

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Server events already have an installed bus");
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                bus.post(new RegisterCommandsEvent(dispatcher, selection, context)));
        // Fabric API 0.116.17: MinecraftServerMixin injects SERVER_STARTING
        // immediately before runServer calls initServer (Yarn: setupServer).
        // initServer calls loadLevel -> createLevels, so region installation is safe here.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> bus.post(new ServerAboutToStartEvent(server)));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Fabric has no callback matching NeoForge's later initServer hook.
            // Preserve the post-world-load boundary and ordering explicitly.
            bus.post(new ServerStartingEvent(server));
            bus.post(new ServerStartedEvent(server));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> bus.post(new ServerStoppingEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> bus.post(new ServerStoppedEvent(server)));
        ServerTickEvents.START_SERVER_TICK.register(server -> bus.post(new ServerTickEvent.Pre(
                () -> ((MinecraftServerTimeAccessor) server).stardewcraft$haveTime(), server)));
        ServerTickEvents.END_SERVER_TICK.register(server -> bus.post(new ServerTickEvent.Post(
                () -> ((MinecraftServerTimeAccessor) server).stardewcraft$haveTime(), server)));
        ServerTickEvents.START_WORLD_TICK.register(level -> bus.post(new LevelTickEvent.Pre(
                () -> ((MinecraftServerTimeAccessor) level.getServer()).stardewcraft$haveTime(), level)));
        ServerTickEvents.END_WORLD_TICK.register(level -> bus.post(new LevelTickEvent.Post(
                () -> ((MinecraftServerTimeAccessor) level.getServer()).stardewcraft$haveTime(), level)));
        installedBus = bus;
    }
}
