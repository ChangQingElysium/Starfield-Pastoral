package com.stardew.craft.api.v1.item;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Supplies stack-sensitive Stardew item metadata for an addon. */
@FunctionalInterface
public interface StardewItemDataProvider {
    Optional<StardewItemData> resolve(ItemStack stack);
}
