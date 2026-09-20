package com.stardew.craft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Stardew 1.6 Butterfly Powder object metadata; its 20,000g value is a shop purchase price. */
public final class ButterflyPowderItem extends Item implements IStardewItem {
    public ButterflyPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.misc";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    @Override
    public int getBaseSellPrice(ItemStack stack) {
        return 0;
    }
}
