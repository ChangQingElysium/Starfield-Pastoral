package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable diagnostic view of a festival activity registration. */
public record StardewFestivalActivityRegistration(
        ResourceLocation id,
        int priority,
        ResourceLocation mechanicId,
        ResourceLocation activityId
) {
    public StardewFestivalActivityRegistration {
        id = Objects.requireNonNull(id, "id");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        activityId = Objects.requireNonNull(activityId, "activityId");
    }
}
