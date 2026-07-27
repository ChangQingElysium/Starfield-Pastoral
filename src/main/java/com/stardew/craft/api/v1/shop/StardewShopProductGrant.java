package com.stardew.craft.api.v1.shop;

/** One prepared non-item shop product delivery, invoked only after successful payment. */
@FunctionalInterface
public interface StardewShopProductGrant {
    boolean grant(StardewShopProductContext context);
}
