package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Cross-system diagnostic view of one festival's effective mechanic layers. */
public record StardewFestivalMechanicSnapshot(
        ResourceLocation festivalId,
        ResourceLocation mechanicId,
        StardewFestivalDefinition.FestivalKind kind,
        Set<StardewFestivalMechanicCapability> capabilities,
        List<StardewFestivalMechanicRegistration> contributions,
        boolean legacyHandlerAvailable,
        List<String> issues
) {
    public StardewFestivalMechanicSnapshot {
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        kind = Objects.requireNonNull(kind, "kind");
        capabilities = Set.copyOf(capabilities);
        contributions = List.copyOf(contributions);
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.isEmpty();
    }
}
