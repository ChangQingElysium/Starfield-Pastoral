package com.stardew.craft.item;
import com.stardew.craft.block.mine.MineLadderBlock;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
public final class MineEntranceItem extends StardewBlockItem {
    public MineEntranceItem(Block block, Properties properties) { super(block, "stardewcraft.type.building", -1, properties); }
    public static MineLadderBlock.Theme theme(ItemStack stack) {
        var theme=stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(MineLadderBlock.THEME);
        return theme==null?MineLadderBlock.Theme.EARTH:theme;
    }
    public static boolean shaft(ItemStack stack) { return Boolean.TRUE.equals(stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(MineLadderBlock.SHAFT)); }
    public static int modelIndex(ItemStack stack) { return shaft(stack)?(theme(stack)==MineLadderBlock.Theme.DESERT_DARK?9:8):theme(stack).ordinal(); }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(shaft(stack)?"block.stardewcraft.mine_ladder.shaft."+(theme(stack)==MineLadderBlock.Theme.DESERT_DARK?"desert_dark":"desert"):
                "block.stardewcraft.mine_ladder."+theme(stack).getSerializedName());
    }
}
