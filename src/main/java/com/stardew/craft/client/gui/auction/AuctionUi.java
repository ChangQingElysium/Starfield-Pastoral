package com.stardew.craft.client.gui.auction;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Native-density auction materials. A screen draws exactly one outer case. */
final class AuctionUi {
    static final int INK = 0xFF472F2B, BODY = 0xFF654737, MUTED = 0xFF79563F;
    static final int GOLD = 0xFF8A5429, ERROR = 0xFF993D3B, CREAM = 0xFFFFEED0;
    private AuctionUi() { }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/auction/" + name + ".png");
    }
    static void sprite(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture(name), x, y, 0, 0, w, h, w, h);
    }
    /** Native 16px decorative objects, displayed at an integer multiple like item icons. */
    static void illustration(GuiGraphics g, String name, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(4, 4, 1);
        sprite(g, name, 0, 0, 16, 16);
        g.pose().popPose();
    }
    static void box(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int b = Math.min(name.equals("frame") ? 7 : 4, Math.min(w, h) / 2);
        if (b < 1) return;
        int[] src = {0, b, 32 - b}, span = {b, 32 - 2 * b, b};
        int[] xx = {x, x + b, x + w - b}, yy = {y, y + b, y + h - b};
        int[] ww = {b, w - 2 * b, b}, hh = {b, h - 2 * b, b};
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
            for (int dy = 0; dy < hh[r]; dy += span[r]) for (int dx = 0; dx < ww[c]; dx += span[c])
                g.blit(texture(name), xx[c] + dx, yy[r] + dy, src[c], src[r],
                        Math.min(span[c], ww[c] - dx), Math.min(span[r], hh[r] - dy), 32, 32);
    }
    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        box(g, "frame", x, y, w, h);
        for (int dy = 0; dy < h - 14; dy += 128) for (int dx = 0; dx < w - 14; dx += 128)
            g.blit(texture("paper"), x + 7 + dx, y + 7 + dy, 0, 0,
                    Math.min(128, w - 14 - dx), Math.min(128, h - 14 - dy), 128, 128);
    }
    static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0xFFC6A878);
        g.fill(x, y + 1, x + w, y + 2, 0xFFFFF0CD);
    }
}
