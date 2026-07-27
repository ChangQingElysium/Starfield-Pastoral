package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Namespaced identity of one node in the cross-system content catalog. */
public record StardewContentKey(
        ResourceLocation type,
        ResourceLocation id
) {
    public StardewContentKey {
        type = Objects.requireNonNull(type, "type");
        id = Objects.requireNonNull(id, "id");
    }

    @Override
    public String toString() {
        return type + "/" + id;
    }
}
