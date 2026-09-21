package com.stardew.craft.api.v1.network;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One bounded capability advertised during the StardewCraft configuration handshake. */
public record StardewNetworkCapability(
        ResourceLocation id,
        int version,
        StardewNetworkCapabilityRequirement requirement
) {
    public StardewNetworkCapability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requirement, "requirement");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
