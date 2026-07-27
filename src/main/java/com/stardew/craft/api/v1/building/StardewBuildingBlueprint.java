package com.stardew.craft.api.v1.building;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A namespaced blueprint ID paired with its immutable definition. */
public record StardewBuildingBlueprint(
        ResourceLocation id,
        StardewBuildingBlueprintDefinition definition
) {
    public StardewBuildingBlueprint {
        id = Objects.requireNonNull(id, "id");
        definition = Objects.requireNonNull(definition, "definition");
    }
}
