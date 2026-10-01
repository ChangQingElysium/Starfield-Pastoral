package com.stardew.craft.block.crop.giant;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;

public class GiantPumpkinBlock extends GiantCropBlock {
    public GiantPumpkinBlock(Properties properties) { super(properties); }
    @Override public DeferredItem<Item> getDropItem() { return ModItems.PUMPKIN; }
}
