package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Core-resolved view of one declared content reference. */
public record StardewContentReferenceSnapshot(
        StardewContentKey owner,
        ResourceLocation role,
        StardewContentKey target,
        boolean required,
        boolean resolved
) {
    public StardewContentReferenceSnapshot {
        owner = Objects.requireNonNull(owner, "owner");
        role = Objects.requireNonNull(role, "role");
        target = Objects.requireNonNull(target, "target");
    }
}
