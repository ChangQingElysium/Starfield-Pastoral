package com.stardew.craft.item;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.mine.MineLampBlock;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

public final class MineLampItem extends StardewBlockItem {
    public MineLampItem(Block block, Properties properties) { super(block, "stardewcraft.type.furniture", -1, properties); }
    public static MineLampBlock.Theme theme(ItemStack stack) {
        MineLampBlock.Theme theme = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(MineLampBlock.THEME);
        return theme == null ? MineLampBlock.Theme.EARTH : theme;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable("block.stardewcraft.mine_lamp." + theme(stack).getSerializedName());
    }
}
