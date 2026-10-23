package com.stardew.craft.platform.fabric.resource;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.junit.jupiter.api.Test;

class ServerResourceReloadsTest {
    private static final ThreadLocal<String> EXECUTOR = new ThreadLocal<>();

    @Test
    void gameEventCollectsNativeListenerIdentitiesAndActualRegistryContext() throws ReflectiveOperationException {
        ReloadableServerResources resources = resourceIdentity();
        EventBus bus = new EventBus();
        PreparableReloadListener vanilla = listener("vanilla", new ArrayList<>());
        PreparableReloadListener custom = listener("custom", new ArrayList<>());
        bus.addListener(AddReloadListenerEvent.class, event -> {
            assertSame(resources, event.getServerResources());
            assertSame(RegistryAccess.EMPTY, event.getRegistryAccess());
            assertFalse(event.isCancelable());
            var before = event.getListeners();
            event.addListener(custom);
            assertTrue(before.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> event.getListeners().clear());
        });
        var bridge = new ServerResourceReloads(resources, RegistryAccess.EMPTY, bus::post);
        List<PreparableReloadListener> original = List.of(vanilla);
        var combined = bridge.appendTo(original);
        assertEquals(List.of(vanilla, custom), combined);
        assertSame(custom, combined.getLast());
        assertEquals(List.of(vanilla), original);
        assertThrows(UnsupportedOperationException.class, combined::clear);
    }

    @Test
    void repeatedListenersQueriesDoNotConstructAdditionalReloaders() throws ReflectiveOperationException {
        AtomicInteger registrations = new AtomicInteger();
        var bridge = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY, event -> {
            registrations.incrementAndGet();
            event.addListener(listener("custom", new ArrayList<>()));
        });
        var first = bridge.appendTo(List.of());
        var second = bridge.appendTo(List.of());
        assertEquals(1, registrations.get());
        assertSame(first.getFirst(), second.getFirst());
    }

    @Test
    void eachNewResourceInstanceGetsFreshListeners() throws ReflectiveOperationException {
        AtomicInteger registrations = new AtomicInteger();
        java.util.function.Consumer<AddReloadListenerEvent> collect = event -> {
            registrations.incrementAndGet();
            event.addListener(listener("custom", new ArrayList<>()));
        };
        var first = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY, collect);
        var second = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY, collect);
        assertNotSame(first.appendTo(List.of()).getFirst(), second.appendTo(List.of()).getFirst());
        assertEquals(2, registrations.get());
    }

    @Test
    void otherReturnInjectionsComposeBeforeOrAfterThisBridge() throws ReflectiveOperationException {
        PreparableReloadListener vanilla = listener("vanilla", new ArrayList<>());
        PreparableReloadListener dataMaps = listener("data_maps", new ArrayList<>());
        PreparableReloadListener custom = listener("custom", new ArrayList<>());
        var bridge = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY,
                event -> event.addListener(custom));
        assertEquals(List.of(vanilla, dataMaps, custom), bridge.appendTo(List.of(vanilla, dataMaps)));
        var withLaterDataMaps = new ArrayList<>(bridge.appendTo(List.of(vanilla)));
        withLaterDataMaps.add(dataMaps);
        assertEquals(List.of(vanilla, custom, dataMaps), withLaterDataMaps);
    }

    @Test
    void nativeReloadEngineKeepsPreparationBarrierApplyOrderingAndExecutors() throws ReflectiveOperationException {
        List<String> calls = new ArrayList<>();
        PreparableReloadListener vanilla = listener("vanilla", calls);
        var bridge = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY,
                event -> event.addListener(listener("custom", calls)));
        QueuedExecutor preparation = new QueuedExecutor("prepare");
        QueuedExecutor application = new QueuedExecutor("apply");
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
            var reload = SimpleReloadInstance.of(manager, bridge.appendTo(List.of(vanilla)),
                    preparation, application, CompletableFuture.completedFuture(Unit.INSTANCE));
            while (calls.isEmpty()) preparation.runOne();
            application.runAll();
            assertEquals(1, calls.size());
            assertTrue(calls.getFirst().startsWith("prepare:"));
            assertFalse(reload.done().isDone());
            preparation.runAll();
            assertEquals(2, calls.size());
            assertFalse(reload.done().isDone());
            application.runAll();
            reload.done().join();
            assertEquals(List.of("prepare:vanilla", "prepare:custom", "apply:vanilla", "apply:custom"), calls);
        }
    }

    @Test
    void nativeReloadFuturePropagatesPreparationFailure() throws ReflectiveOperationException {
        IllegalStateException failure = new IllegalStateException("Preparation fixture failure");
        PreparableReloadListener failing = new SimplePreparableReloadListener<String>() {
            @Override protected String prepare(ResourceManager manager, ProfilerFiller profiler) { throw failure; }
            @Override protected void apply(String value, ResourceManager manager, ProfilerFiller profiler) { fail("Preparation failed"); }
        };
        assertReloadFailure(failing, failure);
    }

    @Test
    void nativeReloadFuturePropagatesApplyFailure() throws ReflectiveOperationException {
        IllegalStateException failure = new IllegalStateException("Apply fixture failure");
        PreparableReloadListener failing = new SimplePreparableReloadListener<String>() {
            @Override protected String prepare(ResourceManager manager, ProfilerFiller profiler) { return "prepared"; }
            @Override protected void apply(String value, ResourceManager manager, ProfilerFiller profiler) { throw failure; }
        };
        assertReloadFailure(failing, failure);
    }

    @Test
    void registrationFailureIsNotHiddenAsAnEmptySuccessfulReload() throws ReflectiveOperationException {
        IllegalArgumentException failure = new IllegalArgumentException("Registration fixture failure");
        var bridge = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY, event -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> bridge.appendTo(List.of())));
    }

    @Test
    void inspectingListenersDuringRegistrationDoesNotRecursivelyDispatch() throws ReflectiveOperationException {
        AtomicInteger registrations = new AtomicInteger();
        ServerResourceReloads[] bridge = new ServerResourceReloads[1];
        PreparableReloadListener vanilla = listener("vanilla", new ArrayList<>());
        bridge[0] = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY, event -> {
            registrations.incrementAndGet();
            assertEquals(List.of(vanilla), bridge[0].appendTo(List.of(vanilla)));
            event.addListener(listener("custom", new ArrayList<>()));
        });
        assertEquals(2, bridge[0].appendTo(List.of(vanilla)).size());
        assertEquals(1, registrations.get());
    }

    @Test
    void originalListenerReceivesExactArgumentsAndOwnsTheReturnedFuture() throws ReflectiveOperationException {
        CompletableFuture<Void> originalFuture = new CompletableFuture<>();
        Executor background = task -> { throw new AssertionError("Not a synchronous bridge"); };
        Executor application = task -> { throw new AssertionError("Not a synchronous bridge"); };
        var barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T prepared) { return CompletableFuture.completedFuture(prepared); }
        };
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
            PreparableReloadListener listener = (stage, resources, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor) -> {
                assertSame(barrier, stage);
                assertSame(manager, resources);
                assertSame(InactiveProfiler.INSTANCE, prepareProfiler);
                assertSame(InactiveProfiler.INSTANCE, applyProfiler);
                assertSame(background, prepareExecutor);
                assertSame(application, applyExecutor);
                return originalFuture;
            };
            var bridge = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY,
                    event -> event.addListener(listener));
            var forwarded = bridge.appendTo(List.of()).getFirst().reload(barrier, manager,
                    InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, application);
            assertSame(originalFuture, forwarded);
            assertFalse(forwarded.isDone());
            originalFuture.cancel(false);
            assertTrue(forwarded.isCancelled());
        }
    }

    private static void assertReloadFailure(PreparableReloadListener listener, RuntimeException failure)
            throws ReflectiveOperationException {
        var bridge = new ServerResourceReloads(resourceIdentity(), RegistryAccess.EMPTY,
                event -> event.addListener(listener));
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
            var reload = SimpleReloadInstance.of(manager, bridge.appendTo(List.of()),
                    Runnable::run, Runnable::run, CompletableFuture.completedFuture(Unit.INSTANCE));
            Throwable result = assertThrows(CompletionException.class, () -> reload.done().join());
            while (result instanceof CompletionException) result = result.getCause();
            assertSame(failure, result);
        }
    }

    private static PreparableReloadListener listener(String name, List<String> calls) {
        return new SimplePreparableReloadListener<String>() {
            @Override
            protected String prepare(ResourceManager manager, ProfilerFiller profiler) {
                assertEquals("prepare", EXECUTOR.get());
                calls.add("prepare:" + name);
                return name;
            }

            @Override
            protected void apply(String prepared, ResourceManager manager, ProfilerFiller profiler) {
                assertEquals("apply", EXECUTOR.get());
                assertEquals(name, prepared);
                calls.add("apply:" + name);
            }
        };
    }

    private static final class QueuedExecutor implements Executor {
        private final String name;
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        private QueuedExecutor(String name) { this.name = name; }
        @Override public void execute(Runnable task) { tasks.add(task); }

        private void runOne() {
            Runnable task = tasks.remove();
            EXECUTOR.set(name);
            try { task.run(); } finally { EXECUTOR.remove(); }
        }

        private void runAll() {
            int remaining = 1000;
            while (!tasks.isEmpty()) {
                assertTrue(remaining-- > 0, "Unexpectedly recursive executor scheduling");
                runOne();
            }
        }
    }

    /** Identity-only resources fixture; the native reload engine is exercised separately. */
    private static ReloadableServerResources resourceIdentity() throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Test fixture allocator is unavailable");
        Object allocator = field.get(null);
        return (ReloadableServerResources) allocatorType.getMethod("allocateInstance", Class.class)
                .invoke(allocator, ReloadableServerResources.class);
    }
}
