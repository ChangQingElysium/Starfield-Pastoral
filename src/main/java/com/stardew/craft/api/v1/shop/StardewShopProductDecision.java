package com.stardew.craft.api.v1.shop;

/** Result of asking a shop product handler whether it owns one purchase. */
public enum StardewShopProductDecision {
    /** This handler does not own the product; try the next handler. */
    PASS,
    /** This handler owns the product and currently permits the purchase. */
    ACCEPT,
    /** This handler owns the product but currently rejects the purchase. */
    REJECT
}
