package com.stardew.craft.platform.fabric.resource;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Per-resource-instance registration; the native reload engine owns all execution. */
public final class ServerResourceReloads {
    private final ReloadableServerResources resources;
    private final RegistryAccess registryAccess;
    private final Consumer<AddReloadListenerEvent> dispatch;
    private List<PreparableReloadListener> additional;
    private boolean collecting;

    public ServerResourceReloads(ReloadableServerResources resources, RegistryAccess registryAccess) {
        this(resources, registryAccess, FabricEvents.GAME::post);
    }

    ServerResourceReloads(ReloadableServerResources resources, RegistryAccess registryAccess,
            Consumer<AddReloadListenerEvent> dispatch) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.registryAccess = Objects.requireNonNull(registryAccess, "registryAccess");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    public synchronized List<PreparableReloadListener> appendTo(List<PreparableReloadListener> existing) {
        Objects.requireNonNull(existing, "existing");
        // A subscriber inspecting getServerResources().listeners() must not
        // recursively dispatch the same registration event.
        if (collecting) {
            return List.copyOf(existing);
        }
        if (additional == null) {
            collecting = true;
            try {
                var event = new AddReloadListenerEvent(resources, registryAccess);
                dispatch.accept(event);
                additional = event.getListeners();
            } finally {
                collecting = false;
            }
        }
        List<PreparableReloadListener> combined = new ArrayList<>(existing);
        combined.addAll(additional);
        return List.copyOf(combined);
    }
}
