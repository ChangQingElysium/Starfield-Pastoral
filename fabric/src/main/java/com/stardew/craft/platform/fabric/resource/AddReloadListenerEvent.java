package com.stardew.craft.platform.fabric.resource;

import com.stardew.craft.platform.fabric.event.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Collects this server resource instance's native asynchronous reload listeners. */
public final class AddReloadListenerEvent extends Event {
    private final ReloadableServerResources serverResources;
    private final RegistryAccess registryAccess;
    private final List<PreparableReloadListener> listeners = new ArrayList<>();

    public AddReloadListenerEvent(ReloadableServerResources serverResources, RegistryAccess registryAccess) {
        this.serverResources = Objects.requireNonNull(serverResources, "serverResources");
        this.registryAccess = Objects.requireNonNull(registryAccess, "registryAccess");
    }

    public void addListener(PreparableReloadListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public List<PreparableReloadListener> getListeners() {
        return List.copyOf(listeners);
    }

    public ReloadableServerResources getServerResources() {
        return serverResources;
    }

    public RegistryAccess getRegistryAccess() {
        return registryAccess;
    }
}
