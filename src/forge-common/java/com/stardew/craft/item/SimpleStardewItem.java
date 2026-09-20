package com.stardew.craft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Exact Forge-line port of the shared simple resource/seed item implementation. */
public class SimpleStardewItem extends Item implements IStardewItem {
    private final String typeKey;
    private final int sellPrice;

    public SimpleStardewItem(String typeKey, int sellPrice, Properties properties) {
        super(properties);
        this.typeKey = typeKey;
        this.sellPrice = sellPrice;
    }

    @Override
    public String getItemTypeKey() {
        return typeKey;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice <= 0 ? -1 : sellPrice;
    }
}
