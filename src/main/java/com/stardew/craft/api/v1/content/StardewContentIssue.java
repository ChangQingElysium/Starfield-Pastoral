package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/** Catalog-level provider, ownership or projection diagnostic. */
public record StardewContentIssue(
        Severity severity,
        ResourceLocation source,
        @Nullable StardewContentKey key,
        String message
) {
    public StardewContentIssue {
        severity = Objects.requireNonNull(severity, "severity");
        source = Objects.requireNonNull(source, "source");
        message = Objects.requireNonNull(message, "message");
    }

    public enum Severity {
        WARNING,
        ERROR
    }
}
