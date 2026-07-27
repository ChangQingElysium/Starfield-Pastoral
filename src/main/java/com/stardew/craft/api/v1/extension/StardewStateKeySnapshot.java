package com.stardew.craft.api.v1.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Read-only diagnostic entry for one registered namespaced-state key. */
public record StardewStateKeySnapshot(
        ResourceLocation scope,
        ResourceLocation id,
        int currentVersion
) {
    public StardewStateKeySnapshot {
        scope = Objects.requireNonNull(scope, "scope");
        id = Objects.requireNonNull(id, "id");
        if (currentVersion < 0) {
            throw new IllegalArgumentException(
                    "currentVersion must be non-negative");
        }
    }
}
