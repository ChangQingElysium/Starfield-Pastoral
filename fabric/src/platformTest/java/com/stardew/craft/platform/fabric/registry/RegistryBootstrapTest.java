package com.stardew.craft.platform.fabric.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.MappedRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class RegistryBootstrapTest {
    @BeforeAll static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void busBindingIsLazyAndTheExplicitDependencyOrderControlsRealRegistrations() {
        var bus = new EventBus();
        var first = registry("first");
        var second = registry("second");
        var one = DeferredRegister.create(first, "bootstrap_test");
        var two = DeferredRegister.create(second, "bootstrap_test");
        var a = one.register("a", () -> new Value(1));
        var b = two.register("b", () -> new Value(a.get().number() + 1));
        two.register(bus);
        one.register(bus);
        assertFalse(a.isBound());
        assertFalse(b.isBound());
        List<Object> events = new ArrayList<>();
        bus.addListener(RegistryRegistrationEvent.class, event -> events.add(event.getRegistry()));
        RegistryBootstrap.bootstrap(bus, first.key(), second.key());
        assertEquals(List.of(first, second), events);
        assertEquals(2, b.get().number());
        // Native non-intrusive registry references bind their values at freeze.
        // The deferred wrappers above expose successful registrations earlier.
        first.freeze();
        second.freeze();
        assertSame(a.get(), first.get(a.getId()));
        assertSame(b.get(), second.get(b.getId()));
    }

    @Test void duplicateBindingsAndCompletedPassesAreIdempotent() {
        var bus = new EventBus();
        var registry = registry("once");
        var register = DeferredRegister.create(registry, "bootstrap_test");
        var calls = new AtomicInteger();
        register.register("value", () -> new Value(calls.incrementAndGet()));
        register.register(bus);
        register.register(bus);
        RegistryBootstrap.bootstrap(bus);
        RegistryBootstrap.bootstrap(bus);
        assertEquals(1, calls.get());
        assertEquals(1, registry.size());
        assertThrows(IllegalStateException.class, () -> register.register(new EventBus()));
    }

    @Test void onlyCollectedRegistryKeysArePostedAndRemainingCollectedKeysAreStillCompleted() {
        var bus = new EventBus();
        var registry = registry("collected");
        var absent = registry("not_bound");
        var register = DeferredRegister.create(registry, "bootstrap_test");
        var holder = register.register("value", () -> new Value(2));
        register.register(bus);
        List<Object> keys = new ArrayList<>();
        bus.addListener(RegistryRegistrationEvent.class, event -> keys.add(event.getRegistryKey()));
        RegistryBootstrap.bootstrap(bus, absent.key());
        assertEquals(List.of(registry.key()), keys);
        assertTrue(holder.isBound());
    }

    @Test void duplicateIdsAcrossRegistersFailBeforeAnyFactoryOrMutation() {
        var bus = new EventBus();
        var registry = registry("duplicate");
        var one = DeferredRegister.create(registry, "bootstrap_test");
        var two = DeferredRegister.create(registry, "bootstrap_test");
        var calls = new AtomicInteger();
        one.register("same", () -> new Value(calls.incrementAndGet()));
        two.register("same", () -> new Value(calls.incrementAndGet()));
        one.register(bus);
        two.register(bus);
        assertThrows(IllegalArgumentException.class, () -> RegistryBootstrap.bootstrap(bus));
        assertEquals(0, calls.get());
        assertEquals(0, registry.size());
        assertThrows(IllegalStateException.class, () -> RegistryBootstrap.bootstrap(bus));
    }

    @Test void registryIdentityConflictsFailBeforeConstruction() {
        var bus = new EventBus();
        var first = registry("identity");
        var second = new MappedRegistry<Value>(first.key(), Lifecycle.stable());
        var one = DeferredRegister.create(first, "bootstrap_test");
        var two = DeferredRegister.create(second, "bootstrap_test");
        one.register("one", () -> fail("first factory"));
        two.register("two", () -> fail("second factory"));
        one.register(bus);
        two.register(bus);
        assertThrows(IllegalStateException.class, () -> RegistryBootstrap.bootstrap(bus));
    }

    @Test void suppliersAndEventFailuresKeepThePassFailedAndBlockLateBindings() {
        var bus = new EventBus();
        var registry = registry("failure");
        var register = DeferredRegister.create(registry, "bootstrap_test");
        var failure = new IllegalStateException("factory fixture");
        register.register("value", () -> { throw failure; });
        register.register(bus);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> RegistryBootstrap.bootstrap(bus)));
        assertThrows(IllegalStateException.class, () -> RegistryBootstrap.bootstrap(bus));
        var late = DeferredRegister.create(registry("late"), "bootstrap_test");
        assertThrows(IllegalStateException.class, () -> late.register(bus));
    }

    private static MappedRegistry<Value> registry(String path) {
        return new MappedRegistry<>(ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("bootstrap_test", path)), Lifecycle.stable());
    }
    private record Value(int number) {}
}
