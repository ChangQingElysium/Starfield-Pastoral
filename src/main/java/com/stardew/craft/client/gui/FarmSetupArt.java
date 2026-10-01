package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Hand-pixelled stationery. Nine-slice borders and icons retain their pixel density. */
final class FarmSetupArt {
    static final int INK = 0xFF623E2A;
    static final int MUTED = 0xFF805D3C;
    static final int LIGHT = 0xFFFFF0C7;
    static final int GOOD = 0xFF795030;
    static final int BAD = 0xFFA34335;
    private FarmSetupArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/farm_setup/" + name + ".png");
    }
    static void sprite(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture(name), x, y, 0, 0, w, h, w, h);
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


    static void tile(GuiGraphics g, String name, int x, int y, int w, int h, int size) {
        for (int yy = 0; yy < h; yy += size) for (int xx = 0; xx < w; xx += size)
            g.blit(texture(name), x + xx, y + yy, 0, 0, Math.min(size, w - xx), Math.min(size, h - yy), size, size);
    }
    static void paper(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 2, 0x40362318);
        box(g, "note", x, y, w, h, 4);
        tile(g, "stationery", x + 4, y + 4, w - 8, h - 8, 128);
    }
    static void landscape(GuiGraphics g, int x, int y, int w) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int visible = Math.min(320, w);
        g.blit(texture("landscape"), x + (w - visible) / 2, y, (320 - visible) / 2, 0, visible, 48, 320, 48);
    }
    static void field(GuiGraphics g, int x, int y, int w, int h, boolean focus) {
        g.fill(x, y, x + w, y + h, 0xFFFFECC5);
        g.fill(x, y + h - 2, x + w, y + h - 1, focus ? 0xFF8C6335 : 0xFFCBA16B);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFF6DB);
        g.fill(x, y + h - 5, x + 1, y + h - 1, 0xFFCBA16B);
        g.fill(x + w - 1, y + h - 5, x + w, y + h - 1, 0xFFCBA16B);
    }
    static void panel(GuiGraphics g, FarmSetupLayout l, int titleWidth) {
        g.fill(l.x() + 4, l.y() + 6, l.x() + l.width() + 3, l.y() + l.height() + 4, 0x50351C17);
        box(g, "folio", l.x(), l.y() + 6, l.width(), l.height() - 6, 6);
        tile(g, "canvas", l.x() + 6, l.y() + 12, l.width() - 12, l.height() - 18, 64);
        box(g, "plaque", l.x() + 10, l.y(), Math.min(l.width() - 28, titleWidth + 28), 28, 4);
        if (l.columns()) {
            sprite(g, "sprout", l.x() + l.width() - 42, l.y() + 12, 20, 20);
            g.fill(l.x() + l.width() - 110, l.y() + 26, l.x() + l.width() - 51, l.y() + 27, 0xFFD2A265);
        }
    }
}
