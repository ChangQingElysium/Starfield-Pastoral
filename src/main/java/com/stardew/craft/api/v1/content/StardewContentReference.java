package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Declared edge from one content node to another. Resolution is always
 * computed by the core catalog.
 */
public record StardewContentReference(
        ResourceLocation role,
        StardewContentKey target,
        boolean required
) {
    public StardewContentReference {
        role = Objects.requireNonNull(role, "role");
        target = Objects.requireNonNull(target, "target");
    }

    public static StardewContentReference required(
            ResourceLocation role,
            StardewContentKey target
    ) {
        return new StardewContentReference(role, target, true);
    }

    public static StardewContentReference optional(
            ResourceLocation role,
            StardewContentKey target
    ) {
        return new StardewContentReference(role, target, false);
    }
}
