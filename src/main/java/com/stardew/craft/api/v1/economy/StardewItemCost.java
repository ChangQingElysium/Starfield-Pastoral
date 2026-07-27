package com.stardew.craft.api.v1.economy;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * An exact item-ID component of a composite cost.
 *
 * <p>Data components on the consumed stacks are preserved if the payment is refunded.
 */
public record StardewItemCost(
        ResourceLocation item,
        long amount
) implements StardewCostEntry {
    public StardewItemCost {
        Objects.requireNonNull(item, "item");
        if (amount <= 0L || amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "amount must be between 1 and Integer.MAX_VALUE");
        }
    }
}
