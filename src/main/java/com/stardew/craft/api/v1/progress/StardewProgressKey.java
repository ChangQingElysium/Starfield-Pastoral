package com.stardew.craft.api.v1.progress;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable identity for one progress entry inside a namespaced domain. */
public record StardewProgressKey(ResourceLocation domain, ResourceLocation id) {
    public StardewProgressKey {
        domain = Objects.requireNonNull(domain, "domain");
        id = Objects.requireNonNull(id, "id");
    }
}
