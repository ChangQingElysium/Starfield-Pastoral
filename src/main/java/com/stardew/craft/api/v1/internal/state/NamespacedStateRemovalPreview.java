package com.stardew.craft.api.v1.internal.state;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable operator preview for removing one unhealthy raw state entry. */
public record NamespacedStateRemovalPreview(
        ResourceLocation scope,
        String entryName,
        Set<Issue> issues,
        String confirmationToken,
        Tag sourceEntry
) {
    public NamespacedStateRemovalPreview {
        scope = Objects.requireNonNull(scope, "scope");
        entryName = Objects.requireNonNull(entryName, "entryName");
        if (entryName.isBlank()) {
            throw new IllegalArgumentException(
                    "entryName must not be blank");
        }
        EnumSet<Issue> copied = EnumSet.copyOf(
                Objects.requireNonNull(issues, "issues"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException(
                    "Removal preview requires at least one issue");
        }
        issues = Collections.unmodifiableSet(copied);
        confirmationToken = Objects.requireNonNull(
                confirmationToken, "confirmationToken");
        if (confirmationToken.isBlank()) {
            throw new IllegalArgumentException(
                    "confirmationToken must not be blank");
        }
        sourceEntry = Objects.requireNonNull(
                sourceEntry, "sourceEntry").copy();
    }

    @Override
    public Tag sourceEntry() {
        return sourceEntry.copy();
    }

    public enum Issue {
        ORPHANED,
        MALFORMED,
        INVALID_NAME
    }
}
