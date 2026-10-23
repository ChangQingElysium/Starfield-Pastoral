package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import org.junit.jupiter.api.Test;

class EventBusTest {
    @Test
    void priorityAndRegistrationOrderAreStableAcrossEventInheritance() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.addListener(EventPriority.LOWEST, false, ChildEvent.class, event -> calls.add("lowest"));
        bus.addListener(EventPriority.NORMAL, false, BaseEvent.class, event -> calls.add("normal-first"));
        bus.addListener(EventPriority.HIGHEST, false, Event.class, event -> calls.add("highest"));
        bus.addListener(EventPriority.LOW, false, BaseEvent.class, event -> calls.add("low"));
        bus.addListener(EventPriority.NORMAL, false, ChildEvent.class, event -> calls.add("normal-second"));
        bus.addListener(EventPriority.HIGH, false, BaseEvent.class, event -> calls.add("high"));

        ChildEvent event = new ChildEvent();
        assertSame(event, bus.post(event));
        assertEquals(List.of("highest", "high", "normal-first", "normal-second", "low", "lowest"), calls);
    }

    @Test
    void cancellationSkipsOrdinaryListenersButReceiversCanRestoreDispatch() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.addListener(EventPriority.HIGHEST, false, CancellableEvent.class, event -> {
            calls.add("cancel");
            event.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGH, false, CancellableEvent.class, event -> calls.add("skipped"));
        bus.addListener(EventPriority.NORMAL, true, CancellableEvent.class, event -> {
            calls.add("receive-canceled");
            event.setCanceled(false);
        });
        bus.addListener(EventPriority.LOW, false, CancellableEvent.class, event -> calls.add("restored"));

        CancellableEvent event = bus.post(new CancellableEvent());
        assertEquals(List.of("cancel", "receive-canceled", "restored"), calls);
        assertFalse(event.isCanceled());
        assertThrows(IllegalStateException.class, () -> new Event().setCanceled(true));
    }

    @Test
    void annotatedPriorityAndReceiveCanceledAreHonored() {
        EventBus bus = new EventBus();
        CanceledSubscriber subscriber = new CanceledSubscriber();
        bus.register(subscriber);

        bus.post(new CancellableEvent());
        assertEquals(List.of("cancel", "observe"), subscriber.calls);
    }

    @Test
    void repeatedClassObjectAndTypedListenerRegistrationDoesNotDuplicateCalls() {
        EventBus bus = new EventBus();
        StaticSubscriber.calls = 0;
        InstanceSubscriber instance = new InstanceSubscriber();
        Consumer<BaseEvent> listener = event -> instance.calls++;
        bus.register(StaticSubscriber.class);
        bus.register(StaticSubscriber.class);
        bus.register(instance);
        bus.register(instance);
        bus.addListener(BaseEvent.class, listener);
        bus.addListener(BaseEvent.class, listener);

        bus.post(new BaseEvent());
        assertEquals(1, StaticSubscriber.calls);
        assertEquals(2, instance.calls);
        bus.unregister(listener);
        bus.unregister(instance);
        bus.unregister(StaticSubscriber.class);
        bus.post(new BaseEvent());
        assertEquals(1, StaticSubscriber.calls);
        assertEquals(2, instance.calls);
    }

    @Test
    void equalSubscriberObjectsStillHaveIndependentSubscriptions() {
        EventBus bus = new EventBus();
        EqualSubscriber first = new EqualSubscriber();
        EqualSubscriber second = new EqualSubscriber();
        bus.register(first);
        bus.register(second);
        bus.unregister(first);

        bus.post(new BaseEvent());
        assertEquals(0, first.calls);
        assertEquals(1, second.calls);
    }

    @Test
    void objectRegistrationDoesNotAlsoRegisterStaticMethods() {
        EventBus bus = new EventBus();
        StaticSubscriber.calls = 0;
        bus.register(new StaticSubscriber());
        bus.register(InstanceSubscriber.class);

        bus.post(new BaseEvent());
        assertEquals(0, StaticSubscriber.calls);
    }

    @Test
    void inheritedHandlersWorkAndOverriddenMethodsAreNotDuplicated() {
        EventBus bus = new EventBus();
        InheritedSubscriber inherited = new InheritedSubscriber();
        OverrideSubscriber overridden = new OverrideSubscriber();
        RemovedOverrideSubscriber removed = new RemovedOverrideSubscriber();
        bus.register(inherited);
        bus.register(overridden);
        bus.register(removed);

        bus.post(new BaseEvent());
        assertEquals(1, inherited.calls);
        assertEquals(2, overridden.calls);
        assertEquals(0, removed.calls);
    }

    @Test
    void malformedSubscribersAreRejectedWithoutPartiallyRegistering() {
        EventBus bus = new EventBus();
        InvalidSubscriber subscriber = new InvalidSubscriber();

        assertThrows(IllegalArgumentException.class, () -> bus.register(subscriber));
        bus.post(new BaseEvent());
        assertEquals(0, subscriber.calls);
    }

    @Test
    void dispatchUsesAnImmutableSnapshotWhileHandlersChangeSubscriptions() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        Consumer<BaseEvent> late = event -> calls.add("late");
        Consumer<BaseEvent> removed = event -> calls.add("removed");
        bus.addListener(BaseEvent.class, event -> {
            calls.add("mutator");
            bus.addListener(BaseEvent.class, late);
            bus.unregister(removed);
        });
        bus.addListener(BaseEvent.class, removed);

        bus.post(new BaseEvent());
        assertEquals(List.of("mutator", "removed"), calls);
        calls.clear();
        bus.post(new BaseEvent());
        assertEquals(List.of("mutator", "late"), calls);
    }

    @Test
    void handlerExceptionsKeepTheirCauseAndStopTheDispatch() {
        EventBus bus = new EventBus();
        FailingSubscriber subscriber = new FailingSubscriber();
        bus.register(subscriber);
        List<String> calls = new ArrayList<>();
        bus.addListener(EventPriority.LOWEST, false, BaseEvent.class, event -> calls.add("should-not-run"));

        assertSame(subscriber.failure, assertThrows(IllegalStateException.class, () -> bus.post(new BaseEvent())));
        assertTrue(calls.isEmpty());
    }

    @Test
    void subscriberMetadataUsesFabricEnvironmentTypesWithoutScanningClasses() {
        EventBusSubscriber metadata = ClientMetadata.class.getAnnotation(EventBusSubscriber.class);

        assertArrayEquals(new EnvType[]{EnvType.CLIENT}, metadata.value());
        assertEquals(EventBusSubscriber.Bus.MOD, metadata.bus());
        assertEquals("stardewcraft", metadata.modid());
    }

    static class BaseEvent extends Event {}
    static final class ChildEvent extends BaseEvent {}
    static final class CancellableEvent extends Event {
        CancellableEvent() { super(true); }
    }

    static final class StaticSubscriber {
        static int calls;
        @SubscribeEvent private static void event(BaseEvent event) { calls++; }
    }

    static class InstanceSubscriber {
        int calls;
        @SubscribeEvent private void event(BaseEvent event) { calls++; }
    }

    static final class EqualSubscriber {
        int calls;
        @SubscribeEvent private void event(BaseEvent event) { calls++; }
        @Override public boolean equals(Object other) { return other instanceof EqualSubscriber; }
        @Override public int hashCode() { return 1; }
    }

    static class ParentSubscriber {
        int calls;
        @SubscribeEvent protected void event(BaseEvent event) { calls++; }
    }

    static final class InheritedSubscriber extends ParentSubscriber {}
    static final class OverrideSubscriber extends ParentSubscriber {
        @SubscribeEvent @Override protected void event(BaseEvent event) { calls += 2; }
    }
    static final class RemovedOverrideSubscriber extends ParentSubscriber {
        @Override protected void event(BaseEvent event) { calls++; }
    }

    static final class InvalidSubscriber {
        int calls;
        @SubscribeEvent private void aValid(BaseEvent event) { calls++; }
        @SubscribeEvent private void zInvalid(String value) {}
    }

    static final class CanceledSubscriber {
        final List<String> calls = new ArrayList<>();
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        private void cancel(CancellableEvent event) { calls.add("cancel"); event.setCanceled(true); }
        @SubscribeEvent private void skip(CancellableEvent event) { calls.add("skip"); }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        private void observe(CancellableEvent event) { calls.add("observe"); }
    }

    static final class FailingSubscriber {
        final IllegalStateException failure = new IllegalStateException("expected failure");
        @SubscribeEvent private void event(BaseEvent event) { throw failure; }
    }

    @EventBusSubscriber(modid = "stardewcraft", value = EnvType.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    static final class ClientMetadata {}
}
