package com.stardew.craft.api.v1.economy;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A balance-backed component of a composite cost. */
public record StardewCurrencyCost(
        ResourceLocation currency,
        long amount
) implements StardewCostEntry {
    public StardewCurrencyCost {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
