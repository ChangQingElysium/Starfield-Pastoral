package com.stardew.craft.deco;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData;

public final class DecorationIconItems {
    private DecorationIconItems() {
    }

    @SuppressWarnings("null")
    public static ItemStack createWallpaperIcon(String styleId) {
        ItemStack stack = new ItemStack(ModItems.WALLPAPER_ICON.get());
        PortItemData.set(stack, DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(resolveCmd(DecorationType.WALLPAPER, styleId)));
        return stack;
    }

    @SuppressWarnings("null")
    public static ItemStack createFlooringIcon(String styleId) {
        ItemStack stack = new ItemStack(ModItems.FLOORING_ICON.get());
        PortItemData.set(stack, DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(resolveCmd(DecorationType.FLOORING, styleId)));
        return stack;
    }

    public static int resolveCmd(DecorationType type, String styleId) {
        int idx = DecorationStyleRegistry.getVisualIndex(type, styleId);
        return Math.max(0, idx) + 1;
    }
}
