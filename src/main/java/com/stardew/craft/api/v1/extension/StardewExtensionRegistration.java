package com.stardew.craft.api.v1.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** One ordered registration in an extension-point diagnostic snapshot. */
public record StardewExtensionRegistration(
        ResourceLocation id,
        int priority,
        long invocationCount,
        long failureCount,
        long slowInvocationCount,
        long totalNanos,
        long maxNanos,
        Optional<StardewExtensionFailure> lastFailure
) {
    public StardewExtensionRegistration {
        Objects.requireNonNull(id, "id");
        lastFailure = Objects.requireNonNull(
                lastFailure, "lastFailure");
        if (invocationCount < 0L || failureCount < 0L
                || slowInvocationCount < 0L || totalNanos < 0L
                || maxNanos < 0L) {
            throw new IllegalArgumentException(
                    "extension metrics must be non-negative");
        }
    }

    /** Source-compatible constructor for registrations without runtime metrics. */
    public StardewExtensionRegistration(
            ResourceLocation id,
            int priority
    ) {
        this(id, priority, 0L, 0L, 0L, 0L, 0L, Optional.empty());
    }
}
