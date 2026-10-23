package com.stardew.craft.platform.fabric.event;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** A typed project event bus. Fabric callbacks must explicitly publish each supported event. */
public interface IEventBus {
    void register(Object subscriber);
    void register(Object subscriber, Predicate<Class<? extends Event>> eventTypes);
    void unregister(Object subscriber);
    <E extends Event> E post(E event);
    <E extends Event> void addListener(Class<E> eventType, Consumer<? super E> listener);
    <E extends Event> void addListener(EventPriority priority, boolean receiveCanceled,
            Class<E> eventType, Consumer<? super E> listener);
}
