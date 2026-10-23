package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FabricPlayerEventsTest {
    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void realCallbacksUseTheNewPlayerAndPreserveTheOriginalRespawnBoolean() throws Exception {
        EventBus bus = new EventBus();
        List<PlayerEvent> events = new ArrayList<>();
        bus.addListener(PlayerEvent.class, events::add);
        FabricPlayerEvents.install(bus);
        FabricPlayerEvents.install(bus);
        assertThrows(IllegalStateException.class, () -> FabricPlayerEvents.install(new EventBus()));
        ServerPlayer oldPlayer = EventTestObjects.allocate(ServerPlayer.class);
        ServerPlayer newPlayer = EventTestObjects.allocate(ServerPlayer.class);

        ServerPlayerEvents.JOIN.invoker().onJoin(oldPlayer);
        ServerPlayerEvents.LEAVE.invoker().onLeave(oldPlayer);
        ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(oldPlayer, newPlayer, false);
        ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(oldPlayer, newPlayer, false);
        ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(oldPlayer, newPlayer, true);
        ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(oldPlayer, newPlayer, true);

        assertEquals(List.of(PlayerEvent.PlayerLoggedInEvent.class, PlayerEvent.PlayerLoggedOutEvent.class,
                PlayerEvent.Clone.class, PlayerEvent.PlayerRespawnEvent.class,
                PlayerEvent.Clone.class, PlayerEvent.PlayerRespawnEvent.class),
                events.stream().map(Object::getClass).toList());
        assertSame(oldPlayer, events.get(0).getEntity());
        assertSame(oldPlayer, events.get(1).getEntity());
        assertSame(newPlayer, events.get(2).getEntity());
        assertSame(oldPlayer, ((PlayerEvent.Clone) events.get(2)).getOriginal());
        assertTrue(((PlayerEvent.Clone) events.get(2)).isWasDeath());
        assertFalse(((PlayerEvent.PlayerRespawnEvent) events.get(3)).isEndConquered());
        assertFalse(((PlayerEvent.Clone) events.get(4)).isWasDeath());
        assertTrue(((PlayerEvent.PlayerRespawnEvent) events.get(5)).isEndConquered());
    }

    @Test
    void playerTickIsAlsoDispatchedToPlayerLifecycleBaseSubscribers() throws Exception {
        EventBus bus = new EventBus();
        List<PlayerEvent> events = new ArrayList<>();
        bus.addListener(PlayerEvent.class, events::add);
        PlayerTickEvent.Post tick = new PlayerTickEvent.Post(EventTestObjects.allocate(ServerPlayer.class));

        bus.post(tick);
        assertEquals(List.of(tick), events);
        assertFalse(tick.isCancelable());
    }

    @Test void trackingPreservesPlayerAndTargetAndWaitsUntilAfterTheEarlyFabricCallback() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        var target = EventTestObjects.allocate(ServerPlayer.class);
        List<com.stardew.craft.platform.fabric.event.entity.EntityEvent> events = new ArrayList<>();
        java.util.function.Consumer<com.stardew.craft.platform.fabric.event.entity.EntityEvent> listener = events::add;
        FabricEvents.GAME.addListener(com.stardew.craft.platform.fabric.event.entity.EntityEvent.class, listener);
        try {
            net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.invoker().onStartTracking(target, player);
            assertTrue(events.isEmpty(), "The spawn-before callback must not send target-dependent custom payloads");
            FabricPlayerEvents.postTrackingStarted(target, player);
            FabricPlayerEvents.postTrackingStopped(target, player);
            assertEquals(2, events.size());
            assertSame(player, events.getFirst().getEntity());
            assertSame(target, ((PlayerEvent.StartTracking) events.getFirst()).getTarget());
            assertSame(target, ((PlayerEvent.StopTracking) events.getLast()).getTarget());
            assertInstanceOf(com.stardew.craft.platform.fabric.event.entity.living.LivingEvent.class, events.getFirst());
            assertFalse(events.getFirst().isCancelable());
        } finally {
            FabricEvents.GAME.unregister(listener);
        }
    }

    @Test
    void completedDimensionProducerPreservesKeysAndIgnoresTheEarlyFabricCallback() throws Exception {
        ServerPlayer player = EventTestObjects.allocate(ServerPlayer.class);
        List<PlayerEvent.PlayerChangedDimensionEvent> events = new ArrayList<>();
        java.util.function.Consumer<PlayerEvent.PlayerChangedDimensionEvent> listener = events::add;
        FabricEvents.GAME.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, listener);
        try {
            FabricPlayerEvents.postDimensionChanged(player,
                    net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.OVERWORLD);
            assertTrue(events.isEmpty());
            assertThrows(NullPointerException.class, () -> FabricPlayerEvents.postDimensionChanged(null,
                    net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER));
            assertTrue(events.isEmpty());
            // This callback also covers cross-world respawns and occurs too early
            // in normal transfers. It must not be wired to the project event.
            ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.invoker().afterChangeWorld(player, null, null);
            ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, player, false);
            assertTrue(events.isEmpty());

            FabricPlayerEvents.postDimensionChanged(player,
                    net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER);
            assertEquals(1, events.size());
            assertSame(player, events.get(0).getEntity());
            assertSame(net.minecraft.world.level.Level.OVERWORLD, events.get(0).getFrom());
            assertSame(net.minecraft.world.level.Level.NETHER, events.get(0).getTo());
            assertFalse(events.get(0).isCancelable());
        } finally {
            FabricEvents.GAME.unregister(listener);
        }
    }
}
