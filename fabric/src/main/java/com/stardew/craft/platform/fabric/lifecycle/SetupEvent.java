package com.stardew.craft.platform.fabric.lifecycle;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Synchronous event publication followed by a real FIFO work queue on the initialization thread. */
public abstract class SetupEvent extends Event implements ModBusEvent {
    private final Thread owner = Thread.currentThread();
    private final Deque<Work<?>> queue = new ArrayDeque<>();
    private State state = State.COLLECTING;

    public CompletableFuture<Void> enqueueWork(Runnable action) {
        Objects.requireNonNull(action, "action");
        return enqueueWork(() -> { action.run(); return null; });
    }

    public synchronized <T> CompletableFuture<T> enqueueWork(Supplier<T> action) {
        if (state == State.COMPLETE || state == State.FAILED) throw new IllegalStateException("Setup work registration is closed");
        var future = new CompletableFuture<T>();
        queue.addLast(new Work<>(Objects.requireNonNull(action, "action"), future));
        return future;
    }

    public final void dispatchAndDrain(IEventBus bus) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Setup must drain on its initialization thread");
        synchronized (this) {
            if (state != State.COLLECTING) throw new IllegalStateException("Setup event has already been dispatched");
            state = State.DISPATCHING;
        }
        try {
            Objects.requireNonNull(bus, "bus").post(this);
            synchronized (this) { state = State.DRAINING; }
            while (true) {
                Work<?> work;
                synchronized (this) {
                    work = queue.pollFirst();
                    if (work == null) { state = State.COMPLETE; return; }
                }
                work.execute();
            }
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                state = State.FAILED;
                for (Work<?> work : queue) work.future.completeExceptionally(failure);
                queue.clear();
            }
            throw failure;
        }
    }

    private enum State { COLLECTING, DISPATCHING, DRAINING, COMPLETE, FAILED }
    private record Work<T>(Supplier<T> action, CompletableFuture<T> future) {
        void execute() {
            if (future.isCancelled()) return;
            try { future.complete(action.get()); }
            catch (RuntimeException | Error failure) { future.completeExceptionally(failure); throw failure; }
        }
    }
}
