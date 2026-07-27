package com.stardew.craft.api.v1.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A rejected extension registration retained for operator diagnostics. */
public record StardewExtensionIssue(
        ResourceLocation registrationId,
        Kind kind,
        String message,
        long timestampMillis
) {
    public StardewExtensionIssue {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
        if (timestampMillis < 0L) {
            throw new IllegalArgumentException(
                    "timestampMillis must be non-negative");
        }
    }

    public enum Kind {
        DUPLICATE_ID,
        LATE_REGISTRATION
    }
}
