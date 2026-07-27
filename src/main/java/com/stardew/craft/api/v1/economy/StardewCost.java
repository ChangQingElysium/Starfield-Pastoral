package com.stardew.craft.api.v1.economy;

import java.util.List;

/** Immutable composite cost; an empty cost represents a free operation. */
public record StardewCost(List<StardewCostEntry> entries) {
    public static final StardewCost FREE = new StardewCost(List.of());

    public StardewCost {
        entries = List.copyOf(entries);
    }

    public static StardewCost of(StardewCostEntry... entries) {
        return new StardewCost(List.of(entries));
    }
}
