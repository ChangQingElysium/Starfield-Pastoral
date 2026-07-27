package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;

/** Immutable diagnostic view of one mechanic contribution. */
public record StardewFestivalMechanicRegistration(
        ResourceLocation id,
        int priority,
        ResourceLocation mechanicId,
        Set<StardewFestivalMechanicCapability> capabilities
) {
    public StardewFestivalMechanicRegistration {
        id = Objects.requireNonNull(id, "id");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        capabilities = Set.copyOf(capabilities);
    }
}
