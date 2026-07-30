package com.stardew.craft.api.v1.interaction;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One resolved, non-mutating interaction hint. */
public record StardewInteractionHint(
        StardewInteractionHintType type,
        boolean done,
        ResourceLocation identity
) {
    public StardewInteractionHint {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(identity, "identity");
    }
}
