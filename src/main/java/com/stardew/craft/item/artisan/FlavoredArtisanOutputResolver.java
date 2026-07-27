package com.stardew.craft.item.artisan;

import net.minecraft.world.item.ItemStack;

/** Applies source-dependent data to fixed artisan outputs. */
public final class FlavoredArtisanOutputResolver {
    private FlavoredArtisanOutputResolver() {
    }

    public static ItemStack apply(PreserveType type, ItemStack ingredient, ItemStack output) {
        if (type == PreserveType.WINE || type == PreserveType.JUICE) {
            return FlavoredArtisanDrinkItem.createFlavored(type, ingredient, output);
        }
        return PreservesItem.createFlavored(type, ingredient, output);
    }
}
