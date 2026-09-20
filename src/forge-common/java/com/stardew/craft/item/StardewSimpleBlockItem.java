package com.stardew.craft.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The non-interactive building-block portion of StardewBlockItem.
 *
 * <p>The first Forge block slice contains only ordinary building materials:
 * their original item has no description key and no special placement hook,
 * so this class preserves the exact placement and Stardew metadata contract
 * without prematurely pulling the carpet-entity subsystem into the slice.</p>
 */
public class StardewSimpleBlockItem extends BlockItem implements IStardewItem {
    private final String itemTypeKey;
    private final int sellPrice;

    public StardewSimpleBlockItem(Block block, String itemTypeKey, int sellPrice, Item.Properties properties) {
        super(block, properties);
        this.itemTypeKey = itemTypeKey;
        this.sellPrice = sellPrice;
    }

    @Override
    public String getItemTypeKey() {
        return itemTypeKey;
    }

    @Override
    public int getSellPrice(net.minecraft.world.item.ItemStack stack) {
        return sellPrice <= 0 ? -1 : sellPrice;
    }
}
