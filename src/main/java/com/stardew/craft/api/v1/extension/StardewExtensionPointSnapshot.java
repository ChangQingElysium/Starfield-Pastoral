package com.stardew.craft.api.v1.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable diagnostic view of one ordered extension point. */
public record StardewExtensionPointSnapshot(
        ResourceLocation id,
        long revision,
        StardewExtensionLifecycle lifecycle,
        List<StardewExtensionRegistration> registrations,
        List<StardewExtensionIssue> issues
) {
    public StardewExtensionPointSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lifecycle, "lifecycle");
        if (revision < 0L) {
            throw new IllegalArgumentException(
                    "revision must be non-negative");
        }
        registrations = List.copyOf(registrations);
        issues = List.copyOf(issues);
    }

    /** Source- and binary-compatible constructor for pre-lifecycle snapshots. */
    public StardewExtensionPointSnapshot(
            ResourceLocation id,
            long revision,
            List<StardewExtensionRegistration> registrations
    ) {
        this(
                id,
                revision,
                StardewExtensionLifecycle.REGISTERING,
                registrations,
                List.of());
    }
}
