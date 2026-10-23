package com.stardew.craft.platform.fabric.event.client;

import com.stardew.craft.platform.fabric.event.ChunkEvent;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.IEventBus;
import com.stardew.craft.platform.fabric.event.LevelEvent;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** Exact client lifecycle points are supplied by the three independent client-only mixins. */
@Environment(EnvType.CLIENT)
public final class FabricClientWorldEvents {
    private FabricClientWorldEvents() {}

    public static void levelLoaded(LevelAccessor level) { levelLoaded(FabricEvents.GAME, level); }
    public static void levelUnloading(@Nullable LevelAccessor level) { levelUnloading(FabricEvents.GAME, level); }
    public static void chunkLoaded(LevelAccessor level, @Nullable LevelChunk chunk) { chunkLoaded(FabricEvents.GAME, level, chunk); }
    public static void chunkUnloading(LevelAccessor level, LevelChunk chunk) { chunkUnloading(FabricEvents.GAME, level, chunk); }

    static void levelLoaded(IEventBus bus, LevelAccessor level) {
        bus.post(new LevelEvent.Load(Objects.requireNonNull(level, "loaded client level")));
    }

    static void levelUnloading(IEventBus bus, @Nullable LevelAccessor level) {
        if (level != null) bus.post(new LevelEvent.Unload(level));
    }

    static void chunkLoaded(IEventBus bus, LevelAccessor level, @Nullable LevelChunk chunk) {
        // A rejected out-of-range packet returns null. Client packets never generate a new chunk.
        if (chunk != null) bus.post(new ChunkEvent.Load(chunk, Objects.requireNonNull(level, "client level"), false));
    }

    static void chunkUnloading(IEventBus bus, LevelAccessor level, LevelChunk chunk) {
        bus.post(new ChunkEvent.Unload(Objects.requireNonNull(chunk, "unloaded client chunk"),
                Objects.requireNonNull(level, "client level")));
    }
}
