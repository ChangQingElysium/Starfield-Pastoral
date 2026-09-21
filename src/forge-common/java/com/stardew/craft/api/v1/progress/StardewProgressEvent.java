package com.stardew.craft.api.v1.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable committed progress transition. Listeners cannot cancel the owning operation. */
public record StardewProgressEvent(
        StardewProgressEventType type,
        ServerLevel level,
        Optional<UUID> actor,
        Optional<StardewProgressSnapshot> before,
        StardewProgressSnapshot after,
        ResourceLocation cause
) {
    public StardewProgressEvent {
        type = Objects.requireNonNull(type, "type");
        level = Objects.requireNonNull(level, "level");
        actor = Objects.requireNonNull(actor, "actor");
        before = Objects.requireNonNull(before, "before");
        after = Objects.requireNonNull(after, "after");
        cause = Objects.requireNonNull(cause, "cause");
        if (before.isPresent() && !before.get().key().equals(after.key())) {
            throw new IllegalArgumentException("Progress event snapshots must use the same key");
        }
    }
}
