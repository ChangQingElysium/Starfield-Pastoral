package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.mixin.event.mixin.MinecraftServerTimeAccessor;
import com.stardew.craft.platform.mixin.event.mixin.PlayerTickMixin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Inject;

class FabricServerEventsTest {
    @Test
    void realFabricInvokersDispatchLifecycleAndTickEventsOnceInOrder() {
        EventBus bus = com.stardew.craft.platform.fabric.server.FabricServerLifecycleTest.installActualBridges();
        List<Class<?>> events = new ArrayList<>();
        bus.addListener(Event.class, event -> events.add(event.getClass()));
        FabricServerEvents.install(bus);
        FabricServerEvents.install(bus);
        assertThrows(IllegalStateException.class, () -> FabricServerEvents.install(new EventBus()));

        // The callback payload is deliberately unused: these tests invoke the
        // actual Fabric event arrays without constructing a server or a world.
        ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(null);
        ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(null);
        ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(null);
        ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(null);
        ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(null);
        ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(null);
        ServerTickEvents.START_WORLD_TICK.invoker().onStartTick(null);
        ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(null);

        assertEquals(List.of(ServerAboutToStartEvent.class, ServerStartingEvent.class, ServerStartedEvent.class,
                ServerStoppingEvent.class, ServerStoppedEvent.class, ServerTickEvent.Pre.class,
                ServerTickEvent.Post.class, LevelTickEvent.Pre.class, LevelTickEvent.Post.class), events);
    }

    @Test
    void tickBudgetRemainsALiveSupplierRatherThanAnInventedConstant() {
        AtomicBoolean budget = new AtomicBoolean(true);
        ServerTickEvent server = new ServerTickEvent.Pre(budget::get, null);
        LevelTickEvent level = new LevelTickEvent.Post(budget::get, null);

        assertTrue(server.hasTime());
        assertTrue(level.hasTime());
        budget.set(false);
        assertFalse(server.hasTime());
        assertFalse(level.hasTime());
    }

    @Test
    void playerTickUsesTheRealPlayerTickMethodAndBudgetUsesTheRealServerMethod() throws Exception {
        // @Mixin has CLASS retention, so inspect its class-file metadata.
        String mixinBytecode = classBytes(PlayerTickMixin.class);
        assertTrue(mixinBytecode.contains("Lorg/spongepowered/asm/mixin/Mixin;"));
        assertTrue(mixinBytecode.contains("Lnet/minecraft/world/entity/player/Player;"));
        List<String> locations = Arrays.stream(PlayerTickMixin.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Inject.class)).filter(annotation -> annotation != null)
                .peek(annotation -> assertArrayEquals(new String[]{"tick"}, annotation.method()))
                .flatMap(annotation -> Arrays.stream(annotation.at())).map(at -> at.value()).sorted().toList();
        assertEquals(List.of("HEAD", "TAIL"), locations);
        Invoker invoker = MinecraftServerTimeAccessor.class.getDeclaredMethod("stardewcraft$haveTime").getAnnotation(Invoker.class);
        assertEquals("haveTime", invoker.value());
    }

    @Test
    void commonBridgeBytecodeContainsNoClientClassReferences() throws IOException {
        for (Class<?> type : List.of(EventBus.class, FabricEvents.class, FabricServerEvents.class,
                ServerEvent.class, ServerTickEvent.class, LevelTickEvent.class, PlayerTickEvent.class,
                PlayerTickMixin.class, MinecraftServerTimeAccessor.class, FabricWorldEvents.class,
                FabricPlayerEvents.class, LevelEvent.class, ChunkEvent.class, PlayerEvent.class,
                com.stardew.craft.platform.mixin.event.mixin.LevelChunkGenerationMixin.class,
                com.stardew.craft.platform.mixin.event.mixin.ServerPlayerDimensionChangeMixin.class)) {
            String bytecode = classBytes(type);
            assertFalse(bytecode.contains("net/minecraft/client/"), type.getName());
            assertFalse(bytecode.contains("net/fabricmc/fabric/api/client/"), type.getName());
        }
    }

    private static String classBytes(Class<?> type) throws IOException {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
