package com.stardew.craft.port.net.minecraft.world.item.component;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** 1.21 {@code minecraft:dyed_color}; stored as {@code display.color} plus the HideFlags DYE bit. */
public record DyedItemColor(int rgb, boolean showInTooltip) {
    public static final int LEATHER_COLOR = -6265536;

    public static int getOrDefault(ItemStack stack, int defaultColor) {
        DyedItemColor color = PortItemData.get(stack, DataComponents.DYED_COLOR);
        return color != null ? 0xFF000000 | color.rgb() : defaultColor;
    }

    public DyedItemColor withTooltip(boolean showInTooltip) {
        return new DyedItemColor(rgb, showInTooltip);
    }
}
