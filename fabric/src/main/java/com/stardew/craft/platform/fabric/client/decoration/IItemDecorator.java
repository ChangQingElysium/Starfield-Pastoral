package com.stardew.craft.platform.fabric.client.decoration;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface IItemDecorator {
    /** Runs after the native bars/count/cooldown. True requests render-state reset before the next callback. */
    boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y);
}
