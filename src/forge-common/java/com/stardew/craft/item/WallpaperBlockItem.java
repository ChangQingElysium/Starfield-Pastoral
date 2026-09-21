package com.stardew.craft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Block item used by the stable-ID wallpaper styles. */
public class WallpaperBlockItem extends StardewSimpleBlockItem {
    public WallpaperBlockItem(Block block, String itemTypeKey, Item.Properties properties) {
        super(block, itemTypeKey, -1, properties);
    }

    @Override
    public String getDescriptionId() {
        return "block.stardewcraft.wallpaper_block";
    }
}
