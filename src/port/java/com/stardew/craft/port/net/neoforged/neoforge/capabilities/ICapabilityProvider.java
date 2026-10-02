package com.stardew.craft.port.net.neoforged.neoforge.capabilities;

import org.jetbrains.annotations.Nullable;

/** PORT(1.20.1): NeoForge capability provider for an object of type {@code O}. */
@FunctionalInterface
public interface ICapabilityProvider<O, C, T> {
    @Nullable
    T getCapability(O object, C context);
}
