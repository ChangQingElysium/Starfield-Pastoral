package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/** Register once before the initial resource reload; listeners survive later reloads. */
@Environment(EnvType.CLIENT)
public final class RegisterClientReloadListenersEvent extends Event implements ModBusEvent {
    private final String namespace;
    private final Consumer<IdentifiableResourceReloadListener> registrar;
    private final Set<ResourceLocation> registeredIds = new HashSet<>();
    private int nextId;

    public RegisterClientReloadListenersEvent() {
        this("stardewcraft");
    }

    public RegisterClientReloadListenersEvent(String namespace) {
        this(namespace, ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)::registerReloadListener);
    }

    RegisterClientReloadListenersEvent(String namespace, Consumer<IdentifiableResourceReloadListener> registrar) {
        ResourceLocation.fromNamespaceAndPath(namespace, "client_reload");
        this.namespace = namespace;
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void registerReloadListener(PreparableReloadListener listener) {
        Objects.requireNonNull(listener, "listener");
        IdentifiableResourceReloadListener identifiable = listener instanceof IdentifiableResourceReloadListener existing
                ? existing : new Listener(ResourceLocation.fromNamespaceAndPath(namespace,
                        "fabric_port/client_reload/" + nextId++), listener);
        ResourceLocation id = Objects.requireNonNull(identifiable.getFabricId(), "reload listener ID");
        if (!registeredIds.add(id)) throw new IllegalStateException("Duplicate client reload listener ID: " + id);
        registrar.accept(identifiable);
    }

    private record Listener(ResourceLocation id, PreparableReloadListener delegate)
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return id;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
                Executor preparationExecutor, Executor reloadExecutor) {
            // The original listener owns preparation, barrier waiting, application,
            // exceptional completion and scheduling. Do not join or reschedule it.
            return delegate.reload(barrier, manager, preparationProfiler, reloadProfiler,
                    preparationExecutor, reloadExecutor);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }
}
