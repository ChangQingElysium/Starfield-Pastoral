package com.stardew.craft.platform.fabric.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Dispatches only project events. It does not discover mod classes or emulate
 * loader setup events. A generated index chooses subscribers and their side.
 */
public final class EventBus implements IEventBus {
    private volatile List<Subscription> subscriptions = List.of();
    private long nextOrder;

    /** A class registers static handlers; an object registers instance handlers. */
    @Override
    public void register(Object subscriber) {
        register(subscriber, ignored -> true);
    }

    /** The automatic subscriber index supplies this filter; explicit registration remains unfiltered. */
    @Override
    public synchronized void register(Object subscriber, Predicate<Class<? extends Event>> eventTypes) {
        Objects.requireNonNull(subscriber, "subscriber");
        Objects.requireNonNull(eventTypes, "event types");
        boolean staticHandlers = subscriber instanceof Class<?>;
        Class<?> type = staticHandlers ? (Class<?>) subscriber : subscriber.getClass();
        List<Subscription> additions = new ArrayList<>();
        Set<Signature> seen = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Method[] methods = current.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
            for (Method method : methods) {
                if (method.isBridge() || method.isSynthetic()) continue;
                Signature signature = new Signature(method.getName(), List.of(method.getParameterTypes()));
                if (!seen.add(signature)) continue;
                SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
                if (annotation == null || Modifier.isStatic(method.getModifiers()) != staticHandlers) continue;
                validate(method);
                Class<? extends Event> eventType = method.getParameterTypes()[0].asSubclass(Event.class);
                if (!eventTypes.test(eventType) || contains(subscriber, method)) continue;
                additions.add(new Subscription(subscriber, method, eventType, annotation.priority(),
                        annotation.receiveCanceled(), nextOrder++, event -> invoke(method, staticHandlers ? null : subscriber, event)));
            }
        }
        append(additions);
    }

    @Override
    public <E extends Event> void addListener(Class<E> eventType, Consumer<? super E> listener) {
        addListener(EventPriority.NORMAL, false, eventType, listener);
    }

    /** Event type is explicit: lambda generic arguments are erased at runtime. */
    @Override
    public synchronized <E extends Event> void addListener(EventPriority priority, boolean receiveCanceled,
            Class<E> eventType, Consumer<? super E> listener) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        if (contains(listener, eventType)) return;
        append(List.of(new Subscription(listener, eventType, eventType, priority, receiveCanceled,
                nextOrder++, event -> listener.accept(eventType.cast(event)))));
    }

    @Override
    public synchronized void unregister(Object subscriber) {
        subscriptions = subscriptions.stream().filter(subscription -> subscription.owner != subscriber).toList();
    }

    @Override
    public <E extends Event> E post(E event) {
        Objects.requireNonNull(event, "event");
        for (Subscription subscription : subscriptions) {
            if (subscription.eventType.isInstance(event) && (!event.isCanceled() || subscription.receiveCanceled)) {
                subscription.handler.accept(event);
            }
        }
        return event;
    }

    private boolean contains(Object owner, Object key) {
        return subscriptions.stream().anyMatch(subscription -> subscription.owner == owner && subscription.key.equals(key));
    }

    private void append(List<Subscription> additions) {
        if (additions.isEmpty()) return;
        List<Subscription> updated = new ArrayList<>(subscriptions);
        updated.addAll(additions);
        updated.sort(Comparator.comparing(Subscription::priority).thenComparingLong(Subscription::order));
        subscriptions = List.copyOf(updated);
    }

    private static void validate(Method method) {
        if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])
                || method.getReturnType() != void.class || Modifier.isAbstract(method.getModifiers())) {
            throw new IllegalArgumentException("Event handler must be concrete void method(Event): " + method);
        }
        if (!method.trySetAccessible()) throw new IllegalArgumentException("Event handler is inaccessible: " + method);
    }

    private static void invoke(Method method, Object receiver, Event event) {
        try {
            method.invoke(receiver, event);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Event handler became inaccessible: " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Event handler failed: " + method, cause);
        }
    }

    private record Signature(String name, List<Class<?>> parameters) {}
    private record Subscription(Object owner, Object key, Class<? extends Event> eventType,
            EventPriority priority, boolean receiveCanceled, long order, Consumer<Event> handler) {}
}
