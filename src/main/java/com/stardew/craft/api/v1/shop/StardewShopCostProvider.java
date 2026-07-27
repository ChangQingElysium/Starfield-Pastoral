package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.economy.StardewCost;

/** Ordered transformer for the server-authoritative cost of a shop row. */
@FunctionalInterface
public interface StardewShopCostProvider {
    StardewCost resolve(
            StardewShopCostContext context,
            StardewCost proposed
    );
}
