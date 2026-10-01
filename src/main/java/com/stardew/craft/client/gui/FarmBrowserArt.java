package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Original cloth labels: keep the turned ends and stitched hems at native pixel density. */
public final class FarmBrowserArt {
    private FarmBrowserArt() { }

    public static void permission(GuiGraphics g, String state, int x, int y, int width, int height) {
        var texture = new ResourceLocation("stardewcraft", "textures/gui/farm_browser/access_" + state + ".png");
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int[] sourceX = {0, 8, 56}, sourceY = {0, 6, 18};
        int[] spansX = {8, 48, 8}, spansY = {6, 12, 6};
        int[] destX = {x, x + 8, x + width - 8}, destY = {y, y + 6, y + height - 6};
        int[] widths = {8, width - 16, 8}, heights = {6, height - 12, 6};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            for (int dy = 0; dy < heights[row]; dy += spansY[row]) {
                for (int dx = 0; dx < widths[col]; dx += spansX[col]) {
                    g.blit(texture, destX[col] + dx, destY[row] + dy, sourceX[col], sourceY[row],
                            Math.min(spansX[col], widths[col] - dx), Math.min(spansY[row], heights[row] - dy), 64, 24);
                }
            }
        }
    }
}
