package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Original workshop sprites. Edges retain their authored pixel width. */
final class WorkbenchArt {
    static final int INK = 0xFF3C4240;
    static final int MUTED = 0xFF626452;
    static final int LIGHT = 0xFFF4EBD5;
    static final int GOOD = 0xFF365F49;
    static final int BAD = 0xFF8A3C39;

    private WorkbenchArt() { }

    static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/workbench/" + name + ".png");
    }

    static void sprite(GuiGraphics g, String name, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture(name), x, y, 0, 0, size, size, size, size);
    }

    static void box(GuiGraphics g, String name, int x, int y, int w, int h, int border) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int b = Math.min(border, Math.min(w, h) / 2);
        if (b < 1) return;
        int[] destX = {x, x + b, x + w - b};
        int[] destY = {y, y + b, y + h - b};
        int[] destW = {b, w - 2 * b, b};
        int[] destH = {b, h - 2 * b, b};
        int[] source = {0, border, 32 - border};
        int[] span = {border, 32 - 2 * border, border};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            if (destW[col] > 0 && destH[row] > 0) {
                for (int oy = 0; oy < destH[row]; oy += span[row]) {
                    for (int ox = 0; ox < destW[col]; ox += span[col]) {
                        g.blit(texture(name), destX[col] + ox, destY[row] + oy,
                                source[col], source[row], Math.min(span[col], destW[col] - ox),
                                Math.min(span[row], destH[row] - oy), 32, 32);
                    }
                }
            }
        }
    }

    static void panel(GuiGraphics g, WorkbenchLayout l) {
        g.fill(l.x() + 3, l.y() + 4, l.x() + l.width() + 3, l.y() + l.height() + 4, 0x50000000);
        box(g, "frame", l.x(), l.y(), l.width(), l.height(), 6);
        for (int y = l.y() + 6; y < l.y() + l.height() - 6; y += 16) {
            for (int x = l.x() + 6; x < l.x() + l.width() - 6; x += 16) {
                g.blit(texture("paper"), x, y, 0, 0,
                        Math.min(16, l.x() + l.width() - 6 - x),
                        Math.min(16, l.y() + l.height() - 6 - y), 16, 16);
            }
        }
        for (int y = l.y() + 7; y < l.y() + 30; y += 16) {
            for (int x = l.x() + 7; x < l.x() + l.width() - 7; x += 16) {
                g.blit(texture("header"), x, y, 0, 0, Math.min(16, l.x() + l.width() - 7 - x),
                        Math.min(16, l.y() + 30 - y), 16, 16);
            }
        }
        g.fill(l.x() + 9, l.y() + 8, l.x() + l.width() - 9, l.y() + 9, 0xFF688278);
        g.fill(l.x() + 7, l.y() + 30, l.x() + l.width() - 7, l.y() + 31, 0xFFBBA879);
    }
}
