package com.stardew.craft.platform.fabric.registry;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** One real static registry in the current initialization pass; no dynamic or synthetic registry is posted. */
public final class RegistryRegistrationEvent extends Event implements ModBusEvent {
    private final Registry<?> registry;

    RegistryRegistrationEvent(Registry<?> registry) { this.registry = java.util.Objects.requireNonNull(registry, "registry"); }

    public ResourceKey<? extends Registry<?>> getRegistryKey() { return registry.key(); }
    public Registry<?> getRegistry() { return registry; }
}
