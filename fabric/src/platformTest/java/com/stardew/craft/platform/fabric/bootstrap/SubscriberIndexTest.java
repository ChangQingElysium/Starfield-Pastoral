package com.stardew.craft.platform.fabric.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.EventBusSubscriber;
import com.stardew.craft.platform.fabric.event.EventPriority;
import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.SubscribeEvent;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import org.junit.jupiter.api.Test;

class SubscriberIndexTest {
    static final List<String> INVOCATIONS = new ArrayList<>();
    static class GameEvent extends Event {}
    static class RegistrationEvent extends Event implements ModBusEvent {}
    static class ChildRegistrationEvent extends RegistrationEvent {}
    static class CancelableGameEvent extends Event {
        CancelableGameEvent() { super(true); }
    }

    // FML 4 routes by the declared event parameter, ignoring the legacy bus field.
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    static class Common {
        @SubscribeEvent static void onEvent(GameEvent event) { INVOCATIONS.add("game"); }
    }

    @EventBusSubscriber(value = EnvType.CLIENT)
    static class NestedClient {
        @SubscribeEvent static void onEvent(ChildRegistrationEvent event) { INVOCATIONS.add("client-mod"); }
    }

    @EventBusSubscriber(value = EnvType.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    static class Mixed {
        @SubscribeEvent static void game(GameEvent event) { INVOCATIONS.add("mixed-game"); }
        @SubscribeEvent static void mod(RegistrationEvent event) { INVOCATIONS.add("mixed-mod"); }
        // A base Event parameter remains GAME, even when a concrete posted subclass is MOD.
        @SubscribeEvent static void all(Event event) { INVOCATIONS.add("base-game"); }
    }

    @EventBusSubscriber
    static class Canceling {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        static void cancel(CancelableGameEvent event) { INVOCATIONS.add("cancel"); event.setCanceled(true); }
        @SubscribeEvent(priority = EventPriority.HIGH)
        static void skipped(CancelableGameEvent event) { INVOCATIONS.add("skipped"); }
        @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
        static void restore(CancelableGameEvent event) { INVOCATIONS.add("restore"); event.setCanceled(false); }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        static void last(CancelableGameEvent event) { INVOCATIONS.add("last"); }
        @SubscribeEvent static void mod(RegistrationEvent event) { INVOCATIONS.add("mod"); }
    }

    @EventBusSubscriber static class InvalidInstance {
        @SubscribeEvent void handler(GameEvent event) {}
    }
    @EventBusSubscriber static class InvalidArgument {
        @SubscribeEvent static void handler(String event) {}
    }
    @EventBusSubscriber static class InvalidArity {
        @SubscribeEvent static void handler(GameEvent first, GameEvent second) {}
    }
    @EventBusSubscriber static class InvalidReturn {
        @SubscribeEvent static boolean handler(GameEvent event) { return true; }
    }
    static class ManualInstance {
        @SubscribeEvent void handler(GameEvent event) {}
    }

    @Test
    void bytecodeIndexUsesEventHierarchyAndPhysicalSideAndIgnoresLegacyAnnotationBus() throws Exception {
        var common = onlyEntry(Common.class);
        assertEquals(List.of(EnvType.CLIENT, EnvType.SERVER), common.environments());
        assertEquals(1, common.staticHandlers());
        assertEquals(EventBusSubscriber.Bus.GAME, common.bus());
        var client = onlyEntry(NestedClient.class);
        assertEquals(NestedClient.class.getName(), client.className());
        assertEquals(List.of(EnvType.CLIENT), client.environments());
        assertEquals(EventBusSubscriber.Bus.MOD, client.bus());
    }

    @Test
    void dedicatedServerNeverEvenResolvesAClientClassAndRegistrationIsIdempotent() throws Exception {
        var index = contents(Common.class, NestedClient.class, Mixed.class);
        var game = new EventBus();
        var mod = new EventBus();
        var loader = new SubscriberIndex();
        List<String> resolved = new ArrayList<>();
        SubscriberIndex.ClassResolver resolver = name -> {
            resolved.add(name);
            if (!name.equals(Common.class.getName())) fail("Client linkage on a dedicated server: " + name);
            return resolve(name);
        };
        loader.register(index, EnvType.SERVER, game, mod, resolver);
        loader.register(index, EnvType.SERVER, game, mod, resolver);
        INVOCATIONS.clear();
        game.post(new GameEvent());
        assertEquals(List.of("game"), INVOCATIONS);
        assertEquals(List.of(Common.class.getName()), resolved);
    }

    @Test
    void physicalClientReceivesCommonAndClientEventsOnlyOnTheClassifiedBuses() throws Exception {
        var game = new EventBus();
        var mod = new EventBus();
        new SubscriberIndex().register(contents(Common.class, NestedClient.class), EnvType.CLIENT, game, mod,
                SubscriberIndexTest::resolve);
        INVOCATIONS.clear();
        mod.post(new GameEvent());
        game.post(new ChildRegistrationEvent());
        assertTrue(INVOCATIONS.isEmpty());
        game.post(new GameEvent());
        mod.post(new ChildRegistrationEvent());
        assertEquals(List.of("game", "client-mod"), INVOCATIONS);
    }

    @Test
    void mixedClassGetsTwoEntriesWithDisjointHandlersAndBaseParameterStaysGame() throws Exception {
        var entries = entries(Mixed.class);
        assertEquals(2, entries.size());
        assertEquals(List.of(EventBusSubscriber.Bus.GAME, EventBusSubscriber.Bus.MOD),
                entries.stream().map(SubscriberIndex.Entry::bus).toList());
        assertEquals(List.of(2, 1), entries.stream().map(SubscriberIndex.Entry::staticHandlers).toList());
        var index = contents(Mixed.class);
        var game = new EventBus();
        var mod = new EventBus();
        var loader = new SubscriberIndex();
        loader.register(index, EnvType.CLIENT, game, mod, SubscriberIndexTest::resolve);
        loader.register(index, EnvType.CLIENT, game, mod, SubscriberIndexTest::resolve);
        INVOCATIONS.clear();
        game.post(new GameEvent());
        mod.post(new ChildRegistrationEvent());
        assertEquals(List.of("base-game", "mixed-game", "mixed-mod"), INVOCATIONS);
        INVOCATIONS.clear();
        game.post(new ChildRegistrationEvent());
        assertEquals(List.of("base-game"), INVOCATIONS);
    }

    @Test
    void automaticFilteringPreservesCancellationPriorityAndExplicitManualRegistration() throws Exception {
        var game = new EventBus();
        var mod = new EventBus();
        new SubscriberIndex().register(contents(Canceling.class), EnvType.SERVER, game, mod,
                SubscriberIndexTest::resolve);
        INVOCATIONS.clear();
        assertFalse(game.post(new CancelableGameEvent()).isCanceled());
        assertEquals(List.of("cancel", "restore", "last"), INVOCATIONS);
        INVOCATIONS.clear();
        game.post(new RegistrationEvent());
        assertTrue(INVOCATIONS.isEmpty());
        mod.post(new RegistrationEvent());
        assertEquals(List.of("mod"), INVOCATIONS);
        // Explicit registration intentionally subscribes every declared handler to this bus.
        game.register(Canceling.class);
        game.register(Canceling.class);
        INVOCATIONS.clear();
        game.post(new RegistrationEvent());
        game.post(new CancelableGameEvent());
        assertEquals(List.of("mod", "cancel", "restore", "last"), INVOCATIONS);
        game.unregister(Canceling.class);
        INVOCATIONS.clear();
        game.post(new CancelableGameEvent());
        assertTrue(INVOCATIONS.isEmpty());
    }

    @Test
    void invalidAnnotatedAutomaticHandlersAreRejectedButManualInstanceClassesAreNotIndexed() throws Exception {
        for (Class<?> type : List.of(InvalidInstance.class, InvalidArgument.class, InvalidArity.class, InvalidReturn.class)) {
            assertThrows(IllegalArgumentException.class, () -> entries(type), type.getName());
        }
        assertTrue(entries(ManualInstance.class).isEmpty());
    }

    @Test
    void missingClassesDuplicateBusEntriesLegacySchemaAndIncompleteIndexesFailLoudly() throws Exception {
        var common = onlyEntry(Common.class);
        assertThrows(IllegalArgumentException.class, () -> new SubscriberIndex.Contents(2, 2, List.of(common, common)));
        assertThrows(IllegalArgumentException.class, () -> new SubscriberIndex.Contents(2, 0, List.of(common)));
        assertThrows(IllegalArgumentException.class, () -> new SubscriberIndex.Contents(2, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SubscriberIndex.Contents(1, 1, List.of(common)));
        var index = new SubscriberIndex.Contents(2, 1, List.of(common));
        assertThrows(IllegalStateException.class, () -> new SubscriberIndex().register(index,
                EnvType.SERVER, new EventBus(), new EventBus(), name -> { throw new ClassNotFoundException(name); }));
    }

    private static Class<?> resolve(String name) throws ClassNotFoundException {
        return Class.forName(name, false, SubscriberIndexTest.class.getClassLoader());
    }

    private static SubscriberIndex.Contents contents(Class<?>... types) throws Exception {
        List<SubscriberIndex.Entry> result = new ArrayList<>();
        for (Class<?> type : types) result.addAll(entries(type));
        return new SubscriberIndex.Contents(2, result.stream().mapToInt(SubscriberIndex.Entry::staticHandlers).sum(), result);
    }

    private static SubscriberIndex.Entry onlyEntry(Class<?> type) throws Exception {
        var entries = entries(type);
        assertEquals(1, entries.size());
        return entries.getFirst();
    }

    private static List<SubscriberIndex.Entry> entries(Class<?> type) throws Exception {
        try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(bytes);
            return SubscriberIndexGenerator.read(bytes.readAllBytes());
        }
    }
}
