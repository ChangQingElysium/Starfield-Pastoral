package com.stardew.craft.block.crop.giant;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;

public class GiantQiFruitBlock extends GiantCropBlock {
    public GiantQiFruitBlock(Properties properties) { super(properties); }
    @Override public DeferredItem<Item> getDropItem() { return ModItems.VANILLA_CATEGORY_ITEMS.get("qi_fruit"); }
}
