package com.stardew.craft.platform.fabric.client.event;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.junit.jupiter.api.Test;

class ClientReloadListenersTest {
    @Test
    void asynchronousPreparationBarrierAndApplyRemainOwnedByTheOriginalListener() {
        List<IdentifiableResourceReloadListener> listeners = new ArrayList<>();
        var event = new RegisterClientReloadListenersEvent("fixture", listeners::add);
        var preparation = new QueueExecutor();
        var application = new QueueExecutor();
        var releaseBarrier = new CompletableFuture<Void>();
        List<String> stages = new ArrayList<>();
        ResourceManager resources = proxy(ResourceManager.class);
        ProfilerFiller preparationProfiler = proxy(ProfilerFiller.class);
        ProfilerFiller applicationProfiler = proxy(ProfilerFiller.class);
        PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T prepared) {
                stages.add("barrier");
                return releaseBarrier.thenApply(ignored -> prepared);
            }
        };
        var originalFuture = new AtomicReference<CompletableFuture<Void>>();
        event.registerReloadListener((receivedBarrier, receivedResources, receivedPreparationProfiler,
                receivedApplicationProfiler, receivedPreparation, receivedApplication) -> {
            assertSame(barrier, receivedBarrier);
            assertSame(resources, receivedResources);
            assertSame(preparationProfiler, receivedPreparationProfiler);
            assertSame(applicationProfiler, receivedApplicationProfiler);
            assertSame(preparation, receivedPreparation);
            assertSame(application, receivedApplication);
            var future = CompletableFuture.supplyAsync(() -> {
                stages.add("prepare");
                return "prepared resource";
            }, receivedPreparation).thenCompose(receivedBarrier::wait).thenAcceptAsync(prepared -> {
                assertEquals("prepared resource", prepared);
                stages.add("apply");
            }, receivedApplication);
            originalFuture.set(future);
            return future;
        });

        var result = listeners.getFirst().reload(barrier, resources, preparationProfiler, applicationProfiler,
                preparation, application);
        assertSame(originalFuture.get(), result);
        assertTrue(stages.isEmpty());
        assertFalse(result.isDone());
        preparation.runNext();
        assertEquals(List.of("prepare", "barrier"), stages);
        assertTrue(application.tasks.isEmpty());
        releaseBarrier.complete(null);
        assertFalse(result.isDone());
        application.runNext();
        result.join();
        assertEquals(List.of("prepare", "barrier", "apply"), stages);
    }

    @Test
    void failedReloadFutureIsNotSwallowedOrReplaced() {
        List<IdentifiableResourceReloadListener> listeners = new ArrayList<>();
        var event = new RegisterClientReloadListenersEvent("fixture", listeners::add);
        var failed = CompletableFuture.<Void>failedFuture(new IllegalStateException("bad resource"));
        event.registerReloadListener((a, b, c, d, e, f) -> failed);
        assertSame(failed, listeners.getFirst().reload(null, null, null, null, null, null));
        assertTrue(failed.isCompletedExceptionally());
    }

    @Test
    void anonymousRegistrationsRemainDistinctAndIdentifiableDependenciesRemainIntact() {
        List<IdentifiableResourceReloadListener> listeners = new ArrayList<>();
        var event = new RegisterClientReloadListenersEvent("fixture", listeners::add);
        PreparableReloadListener anonymous = (a, b, c, d, e, f) -> CompletableFuture.completedFuture(null);
        event.registerReloadListener(anonymous);
        event.registerReloadListener(anonymous);
        assertEquals("fixture:fabric_port/client_reload/0", listeners.get(0).getFabricId().toString());
        assertEquals("fixture:fabric_port/client_reload/1", listeners.get(1).getFabricId().toString());
        assertEquals(anonymous.getName(), listeners.getFirst().getName());

        IdentifiableResourceReloadListener named = new IdentifiableResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.parse("fixture:named"); }
            @Override public List<ResourceLocation> getFabricDependencies() { return List.of(listeners.getFirst().getFabricId()); }
            @Override public CompletableFuture<Void> reload(PreparationBarrier a, ResourceManager b, ProfilerFiller c,
                    ProfilerFiller d, Executor e, Executor f) { return CompletableFuture.completedFuture(null); }
        };
        event.registerReloadListener(named);
        assertSame(named, listeners.getLast());
        assertEquals(List.of(listeners.getFirst().getFabricId()), listeners.getLast().getFabricDependencies());
        assertThrows(IllegalStateException.class, () -> event.registerReloadListener(named));
        assertEquals(3, listeners.size());
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null));
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable action) { tasks.addLast(action); }
        void runNext() { tasks.removeFirst().run(); }
    }
}
