package com.stardew.craft.api.v1.requirement;

import java.util.List;

/** Immutable evaluation of one AND-composed condition list. */
public record StardewRequirementReport(
        List<StardewRequirement> requirements
) {
    public StardewRequirementReport {
        requirements = List.copyOf(requirements);
    }

    public boolean satisfied() {
        return requirements.stream().noneMatch(StardewRequirement::blocks);
    }

    public List<StardewRequirement> blocking() {
        return requirements.stream()
                .filter(StardewRequirement::blocks)
                .toList();
    }
}
