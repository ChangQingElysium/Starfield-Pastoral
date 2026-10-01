package com.stardew.craft.item;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.mine.MineStepStoneBlock;
import com.stardew.craft.block.mine.MineLadderBlock;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

public final class MineStepStoneItem extends StardewBlockItem {
    public MineStepStoneItem(Block block, Properties properties) { super(block, "stardewcraft.type.building", -1, properties); }
    public static MineLadderBlock.Theme theme(ItemStack stack) {
        var theme = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(MineStepStoneBlock.THEME);
        return theme == null ? MineLadderBlock.Theme.EARTH : theme;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable("block.stardewcraft.mine_step_stone." + theme(stack).getSerializedName());
    }
}
