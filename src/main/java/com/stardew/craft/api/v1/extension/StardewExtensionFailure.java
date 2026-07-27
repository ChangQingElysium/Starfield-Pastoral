package com.stardew.craft.api.v1.extension;

import java.util.Objects;

/** Bounded diagnostic summary of the most recent extension invocation failure. */
public record StardewExtensionFailure(
        String exceptionType,
        String message,
        long occurredAtEpochMillis
) {
    public StardewExtensionFailure {
        exceptionType = Objects.requireNonNull(
                exceptionType, "exceptionType");
        message = Objects.requireNonNullElse(message, "");
        if (occurredAtEpochMillis < 0L) {
            throw new IllegalArgumentException(
                    "occurredAtEpochMillis must be non-negative");
        }
    }
}
