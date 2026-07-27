package com.stardew.craft.api.v1.economy;

/** One component of a composite server-side cost. */
public sealed interface StardewCostEntry
        permits StardewCurrencyCost, StardewItemCost {
    long amount();
}
