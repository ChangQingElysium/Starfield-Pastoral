package com.stardew.craft.platform.fabric.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SetupEventTest {
    @Test void queuedWorkRunsAfterEveryListenerInFifoOrderAndCompletesRealFutures() {
        var bus = new EventBus();
        var event = new FMLCommonSetupEvent();
        List<String> order = new ArrayList<>();
        CompletableFuture<String> early = event.enqueueWork(() -> { order.add("early-work"); return "value"; });
        AtomicReference<CompletableFuture<Void>> later = new AtomicReference<>();
        bus.addListener(FMLCommonSetupEvent.class, setup -> {
            order.add("first-listener");
            later.set(setup.enqueueWork(() -> { order.add("listener-work"); }));
            assertFalse(early.isDone());
        });
        bus.addListener(FMLCommonSetupEvent.class, setup -> order.add("second-listener"));
        event.dispatchAndDrain(bus);
        assertEquals(List.of("first-listener", "second-listener", "early-work", "listener-work"), order);
        assertEquals("value", early.join());
        assertNull(later.get().join());
    }

    @Test void workEnqueuedByWorkJoinsTheEndOfTheSameQueue() {
        var event = new FMLClientSetupEvent();
        List<Integer> order = new ArrayList<>();
        event.enqueueWork(() -> { order.add(1); event.enqueueWork(() -> { order.add(3); }); });
        event.enqueueWork(() -> { order.add(2); });
        event.dispatchAndDrain(new EventBus());
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test void actionFailureFailsItsFutureAndAllPendingWorkWithoutSwallowingTheCause() {
        var event = new FMLCommonSetupEvent();
        var failure = new IllegalStateException("setup fixture");
        var failed = event.enqueueWork(() -> { throw failure; });
        var pending = event.enqueueWork(() -> fail("pending work"));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> event.dispatchAndDrain(new EventBus())));
        assertSame(failure, assertThrows(CompletionException.class, failed::join).getCause());
        assertSame(failure, assertThrows(CompletionException.class, pending::join).getCause());
        assertThrows(IllegalStateException.class, () -> event.enqueueWork(() -> {}));
    }

    @Test void listenerFailureFailsEnqueuedFuturesWithoutRunningThem() {
        var bus = new EventBus();
        var event = new FMLCommonSetupEvent();
        var pending = event.enqueueWork(() -> fail("pending action"));
        var failure = new IllegalArgumentException("listener fixture");
        bus.addListener(FMLCommonSetupEvent.class, setup -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> event.dispatchAndDrain(bus)));
        assertSame(failure, assertThrows(CompletionException.class, pending::join).getCause());
    }

    @Test void completedSetupCannotDispatchAgainOrAcceptMoreWork() {
        var event = new FMLCommonSetupEvent();
        event.dispatchAndDrain(new EventBus());
        assertThrows(IllegalStateException.class, () -> event.dispatchAndDrain(new EventBus()));
        assertThrows(IllegalStateException.class, () -> event.enqueueWork(() -> {}));
    }

    @Test void cancelingAQueuedFuturePreventsItsAction() {
        var event = new FMLCommonSetupEvent();
        var future = event.enqueueWork(() -> fail("canceled action"));
        assertTrue(future.cancel(false));
        event.dispatchAndDrain(new EventBus());
        assertTrue(future.isCancelled());
    }

    @Test void drainingOnAnotherThreadFailsWithoutConsumingTheQueue() throws InterruptedException {
        var event = new FMLCommonSetupEvent();
        var future = event.enqueueWork(() -> 7);
        var error = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try { event.dispatchAndDrain(new EventBus()); }
            catch (Throwable failure) { error.set(failure); }
        });
        thread.start();
        thread.join();
        assertInstanceOf(IllegalStateException.class, error.get());
        assertFalse(future.isDone());
        event.dispatchAndDrain(new EventBus());
        assertEquals(7, future.join());
    }
}
