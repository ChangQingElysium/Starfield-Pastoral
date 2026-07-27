package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only server runtime view without exposing an addon-owned entity instance. */
public record StardewNpcRuntimeSnapshot(
        StardewNpcContentSnapshot content,
        ResourceLocation dimension,
        Optional<UUID> entityUuid
) {
    public StardewNpcRuntimeSnapshot {
        content = Objects.requireNonNull(content, "content");
        dimension = Objects.requireNonNull(dimension, "dimension");
        entityUuid = Objects.requireNonNull(entityUuid, "entityUuid");
    }

    public boolean entityPresent() {
        return entityUuid.isPresent();
    }
}
