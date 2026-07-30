package com.stardew.craft.item;

import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;

/** Casino prize firework with the original Stardew Valley item description. */
public final class CasinoFireworkItem extends FireworkRocketItem implements IStardewItem {
    static final int ORIGINAL_SELL_PRICE = 50;
    static final String CATEGORY_KEY = "stardewcraft.type.misc";

    public CasinoFireworkItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return CATEGORY_KEY;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return ORIGINAL_SELL_PRICE;
    }
}
