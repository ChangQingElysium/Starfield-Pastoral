package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.mixin.event.mixin.LevelChunkGenerationMixin;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

class FabricWorldEventsTest {
    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void realWorldAndChunkCallbacksPreserveIdentityGenerationFlagAndOneLoadNotification() throws Exception {
        EventBus bus = new EventBus();
        List<Event> events = new ArrayList<>();
        bus.addListener(Event.class, events::add);
        FabricWorldEvents.install(bus);
        FabricWorldEvents.install(bus);
        assertThrows(IllegalStateException.class, () -> FabricWorldEvents.install(new EventBus()));
        MarkedChunk chunk = EventTestObjects.allocate(MarkedChunk.class);
        chunk.generated = true;

        ServerWorldEvents.LOAD.invoker().onWorldLoad(null, null);
        ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(null, chunk);
        // This follows CHUNK_LOAD in the official source. It must not create a
        // duplicate notification just to fix a guessed generation flag.
        ServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(null, chunk);
        ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(null, chunk);
        ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(null, chunk);
        ServerWorldEvents.UNLOAD.invoker().onWorldUnload(null, null);

        assertEquals(List.of(LevelEvent.Load.class, ChunkEvent.Load.class, ChunkEvent.Unload.class,
                ChunkEvent.Load.class, LevelEvent.Unload.class), events.stream().map(Object::getClass).toList());
        ChunkEvent.Load firstLoad = (ChunkEvent.Load) events.get(1);
        assertSame(chunk, firstLoad.getChunk());
        assertTrue(firstLoad.isNewChunk());
        assertSame(chunk, ((ChunkEvent.Unload) events.get(2)).getChunk());
        assertFalse(((ChunkEvent.Load) events.get(3)).isNewChunk());
        LevelChunk unmarked = EventTestObjects.allocate(LevelChunk.class);
        assertThrows(IllegalStateException.class, () -> ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(null, unmarked));
    }

    @Test
    void payloadsUseTheExplicitWorldAndRetainLevelEventInheritance() throws Exception {
        LevelAccessor world = (LevelAccessor) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, arguments) -> null);
        MarkedChunk chunk = EventTestObjects.allocate(MarkedChunk.class);
        ChunkEvent.Load load = new ChunkEvent.Load(chunk, world, true);
        ChunkEvent.Unload unload = new ChunkEvent.Unload(chunk, world);
        EventBus bus = new EventBus();
        List<LevelEvent> inherited = new ArrayList<>();
        bus.addListener(LevelEvent.class, inherited::add);
        bus.post(load);
        bus.post(unload);

        assertSame(world, load.getLevel());
        assertSame(world, unload.getLevel());
        assertSame(chunk, load.getChunk());
        assertEquals(List.of(load, unload), inherited);
        assertFalse(load.isCancelable());
        assertSame(chunk, new ChunkEvent.Load(chunk, false).getChunk());
        assertSame(chunk, new ChunkEvent.Unload(chunk).getChunk());
    }

    @Test
    void constructorMarkerUsesTheOriginalProtoVersusImposterDistinctionAndIsConsumedOnce() throws Exception {
        LevelChunkGenerationMixin state = new LevelChunkGenerationMixin() {};
        assertFalse(state.stardewcraft$consumeNewlyGenerated());
        var method = LevelChunkGenerationMixin.class.getDeclaredMethods()[0];
        for (var candidate : LevelChunkGenerationMixin.class.getDeclaredMethods()) {
            if (candidate.isAnnotationPresent(Inject.class)) method = candidate;
        }
        Inject injection = method.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertArrayEquals(new String[]{"<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V"}, injection.method());
        assertEquals("RETURN", injection.at()[0].value());
        assertTrue(method.trySetAccessible());
        method.invoke(state, null, null, null, null);
        assertTrue(state.stardewcraft$consumeNewlyGenerated());
        assertFalse(state.stardewcraft$consumeNewlyGenerated());
        ImposterProtoChunk restored = EventTestObjects.allocate(ImposterProtoChunk.class);
        method.invoke(state, null, restored, null, null);
        assertFalse(state.stardewcraft$consumeNewlyGenerated());
    }

    private static final class MarkedChunk extends LevelChunk implements ChunkLoadState {
        private boolean generated;

        private MarkedChunk() { super((Level) null, (ChunkPos) null); }

        @Override
        public boolean stardewcraft$consumeNewlyGenerated() {
            boolean value = generated;
            generated = false;
            return value;
        }
    }
}
