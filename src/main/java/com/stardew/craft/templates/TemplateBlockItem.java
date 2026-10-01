package com.stardew.craft.templates;

import com.stardew.craft.item.IStardewItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class TemplateBlockItem extends BlockItem implements IStardewItem {
    public TemplateBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.building_template";
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!(getBlock() instanceof CompositeTemplateBlock)) {
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.apply")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.place")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.remove")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.composite_controls")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.composite_clear")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.composite_secondary")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (getBlock() instanceof RoofTemplateBlock) {
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.roof_fill")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (getBlock() instanceof WallCompositeTemplateBlock) {
            TemplateShape shape = ((MaterialTemplateBlock)getBlock()).templateShape();
            tooltip.add(Component.translatable(shape == TemplateShape.ROUND_WINDOW
                    ? "tooltip.stardewcraft.material_template.round_window_fill" : shape.isWindow()
                    ? "tooltip.stardewcraft.material_template.window_fill" : "tooltip.stardewcraft.material_template.facade_fill").withStyle(ChatFormatting.AQUA));
        }
        if (getBlock() instanceof SnowLayerTemplateBlock) {
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.stack_layers")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (getBlock() instanceof ChimneyTemplateBlock) {
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.chimney")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (getBlock() instanceof SmartRoofTemplateBlock || getBlock() instanceof SmartRidgeTemplateBlock) {
            tooltip.add(Component.translatable("tooltip.stardewcraft.material_template.roof_connect")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
