package com.stardew.craft.block.crop.giant;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;

public class GiantCauliflowerBlock extends GiantCropBlock {
    public GiantCauliflowerBlock(Properties properties) { super(properties); }
    @Override public DeferredItem<Item> getDropItem() { return ModItems.CAULIFLOWER; }
}
