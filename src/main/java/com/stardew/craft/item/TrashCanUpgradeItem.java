package com.stardew.craft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Internal GenericTool-style item used to display a backpack trash-can upgrade in Clint's shop. */
public final class TrashCanUpgradeItem extends SimpleStardewItem {
    private final int reclaimPercent;

    public TrashCanUpgradeItem(int reclaimPercent, Properties properties) {
        super("stardewcraft.type.hidden", -1, properties.stacksTo(1));
        this.reclaimPercent = reclaimPercent;
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(
                "stardewcraft.trash_can.upgrade.description", reclaimPercent).withStyle(ChatFormatting.GRAY));
    }
}
