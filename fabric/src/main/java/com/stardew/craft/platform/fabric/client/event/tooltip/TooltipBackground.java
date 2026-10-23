package com.stardew.craft.platform.fabric.client.event.tooltip;

import com.stardew.craft.platform.fabric.client.event.RenderTooltipEvent;
import net.minecraft.client.gui.GuiGraphics;

/** The native padded tooltip frame, with NeoForge's four independently editable gradient colors. */
public final class TooltipBackground {
    private TooltipBackground() {}
    public static void draw(GuiGraphics graphics, int x, int y, int width, int height, int z, RenderTooltipEvent.Color color) {
        int left = x - 3, top = y - 3, outerWidth = width + 6, outerHeight = height + 6;
        graphics.fill(left, top - 1, left + outerWidth, top, z, color.getBackgroundStart());
        graphics.fill(left, top + outerHeight, left + outerWidth, top + outerHeight + 1, z, color.getBackgroundEnd());
        graphics.fillGradient(left, top, left + outerWidth, top + outerHeight, z, color.getBackgroundStart(), color.getBackgroundEnd());
        graphics.fillGradient(left - 1, top, left, top + outerHeight, z, color.getBackgroundStart(), color.getBackgroundEnd());
        graphics.fillGradient(left + outerWidth, top, left + outerWidth + 1, top + outerHeight, z, color.getBackgroundStart(), color.getBackgroundEnd());
        graphics.fillGradient(left, top + 1, left + 1, top + outerHeight - 1, z, color.getBorderStart(), color.getBorderEnd());
        graphics.fillGradient(left + outerWidth - 1, top + 1, left + outerWidth, top + outerHeight - 1, z, color.getBorderStart(), color.getBorderEnd());
        graphics.fill(left, top, left + outerWidth, top + 1, z, color.getBorderStart());
        graphics.fill(left, top + outerHeight - 1, left + outerWidth, top + outerHeight, z, color.getBorderEnd());
    }
}
