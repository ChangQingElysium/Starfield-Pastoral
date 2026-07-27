package com.stardew.craft.api.v1.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only diagnostics for one namespaced persistent-state container.
 *
 * <p>Issue sets may overlap. For example, an entry can be both orphaned and
 * malformed. Diagnostics never expose, mutate, migrate, or delete stored NBT.
 */
public record StardewStateContainerSnapshot(
        ResourceLocation scope,
        Set<ResourceLocation> storedIds,
        Set<ResourceLocation> registeredIds,
        Set<ResourceLocation> orphanedIds,
        Set<ResourceLocation> malformedIds,
        Set<ResourceLocation> legacyVersionIds,
        Set<ResourceLocation> futureVersionIds,
        List<String> invalidEntryNames
) {
    public StardewStateContainerSnapshot {
        scope = Objects.requireNonNull(scope, "scope");
        storedIds = immutableIds(storedIds, "storedIds");
        registeredIds = immutableIds(registeredIds, "registeredIds");
        orphanedIds = immutableIds(orphanedIds, "orphanedIds");
        malformedIds = immutableIds(malformedIds, "malformedIds");
        legacyVersionIds = immutableIds(
                legacyVersionIds, "legacyVersionIds");
        futureVersionIds = immutableIds(
                futureVersionIds, "futureVersionIds");
        invalidEntryNames = Objects.requireNonNull(
                        invalidEntryNames, "invalidEntryNames")
                .stream()
                .sorted()
                .toList();
    }

    public boolean healthy() {
        return orphanedIds.isEmpty()
                && malformedIds.isEmpty()
                && legacyVersionIds.isEmpty()
                && futureVersionIds.isEmpty()
                && invalidEntryNames.isEmpty();
    }

    private static Set<ResourceLocation> immutableIds(
            Set<ResourceLocation> values,
            String name
    ) {
        LinkedHashSet<ResourceLocation> sorted = new LinkedHashSet<>();
        Objects.requireNonNull(values, name).stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(sorted::add);
        return Collections.unmodifiableSet(sorted);
    }
}
