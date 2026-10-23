package com.stardew.craft.platform.fabric.registry;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Ordered static registry events for the registers actually bound to a mod bus. */
public final class RegistryBootstrap {
    private static final Map<IEventBus, RegistryBootstrap> BUSES = new IdentityHashMap<>();
    private final IEventBus bus;
    private final Thread owner = Thread.currentThread();
    private final Map<ResourceKey<?>, List<DeferredRegister<?>>> pending = new LinkedHashMap<>();
    private State state = State.COLLECTING;
    private Throwable failure;

    private RegistryBootstrap(IEventBus bus) { this.bus = bus; }

    static synchronized void bind(IEventBus bus, DeferredRegister<?> register) {
        RegistryBootstrap bootstrap = BUSES.computeIfAbsent(bus, RegistryBootstrap::new);
        bootstrap.checkThread();
        if (bootstrap.state != State.COLLECTING) throw new IllegalStateException("Registry binding is closed", bootstrap.failure);
        bootstrap.pending.computeIfAbsent(register.getRegistryKey(), ignored -> new ArrayList<>()).add(register);
    }

    public static void bootstrap(IEventBus bus, ResourceKey<?>... dependencyOrder) {
        RegistryBootstrap bootstrap;
        synchronized (RegistryBootstrap.class) {
            bootstrap = BUSES.computeIfAbsent(Objects.requireNonNull(bus, "bus"), RegistryBootstrap::new);
        }
        bootstrap.run(List.of(dependencyOrder));
    }

    private synchronized void run(List<ResourceKey<?>> dependencyOrder) {
        checkThread();
        if (state == State.COMPLETE) return;
        if (state != State.COLLECTING) throw new IllegalStateException("Registry bootstrap is already running or failed", failure);
        state = State.RUNNING;
        try {
            var ordered = new LinkedHashSet<ResourceKey<?>>();
            for (var key : dependencyOrder) if (pending.containsKey(key)) ordered.add(key);
            ordered.addAll(pending.keySet());
            Map<ResourceKey<?>, Registry<?>> registries = preflight(ordered);
            for (var key : ordered) {
                bus.post(new RegistryRegistrationEvent(registries.get(key)));
                for (var register : pending.get(key)) {
                    if (!register.isRegistered()) throw new IllegalStateException("Registry event did not complete pending register "
                            + key.location() + " for namespace " + register.getNamespace());
                }
            }
            state = State.COMPLETE;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            state = State.FAILED;
            throw exception;
        }
    }

    private Map<ResourceKey<?>, Registry<?>> preflight(Iterable<ResourceKey<?>> keys) {
        Map<ResourceKey<?>, Registry<?>> registries = new LinkedHashMap<>();
        for (var key : keys) {
            Registry<?> registry = null;
            var ids = new LinkedHashSet<ResourceLocation>();
            for (var register : pending.get(key)) {
                Registry<?> actual = register.getRegistry();
                if (registry != null && registry != actual) throw new IllegalStateException("Two distinct static registries share key " + key.location());
                registry = actual;
                for (var holder : register.getEntries()) {
                    ResourceLocation id = holder.getId();
                    if (!ids.add(id) || actual.containsKey(id)) throw new IllegalArgumentException("Duplicate registry ID " + key.location() + " / " + id);
                }
            }
            registries.put(key, Objects.requireNonNull(registry));
        }
        return registries;
    }

    private enum State { COLLECTING, RUNNING, COMPLETE, FAILED }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Registry bootstrap belongs to its initialization thread");
    }
}
