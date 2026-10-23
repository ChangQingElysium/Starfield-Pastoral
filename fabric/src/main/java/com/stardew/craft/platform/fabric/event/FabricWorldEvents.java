package com.stardew.craft.platform.fabric.event;

import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;

/** Logical-server world/chunk callbacks from Fabric API 0.116.17. */
public final class FabricWorldEvents {
    private static IEventBus installedBus;

    private FabricWorldEvents() {}

    public static void install() {
        install(FabricEvents.GAME);
    }

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("World events already have an installed bus");
        // LOAD runs just after the server puts the created world into its world map.
        // UNLOAD runs immediately before ServerLevel.close during shutdown.
        ServerWorldEvents.LOAD.register((server, level) -> bus.post(new LevelEvent.Load(level)));
        ServerWorldEvents.UNLOAD.register((server, level) -> bus.post(new LevelEvent.Unload(level)));
        // Fabric emits CHUNK_LOAD at the end of FULL promotion, before CHUNK_GENERATE.
        // Reading a constructor marker preserves the generation flag without a
        // second load notification, a delayed event, or an always-false guess.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!((Object) chunk instanceof ChunkLoadState state)) {
                throw new IllegalStateException("LevelChunkGenerationMixin must be installed before chunk events");
            }
            bus.post(new ChunkEvent.Load(chunk, level, state.stardewcraft$consumeNewlyGenerated()));
        });
        // At this callback the chunk's loaded flag is false, but it is still
        // available to cleanup code. This is not a chunk-save/NBT notification.
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> bus.post(new ChunkEvent.Unload(chunk, level)));
        installedBus = bus;
    }
}
