package com.stardew.craft.api.v1.shop;

import java.util.List;

/** Supplies dynamic rows whenever a shop's server-authoritative inventory is rebuilt. */
@FunctionalInterface
public interface StardewShopInventoryProvider {
    List<StardewShopEntry> provide(StardewShopInventoryContext context);
}
