package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class BuildingManagerArt {
    static final int INK = 0xFF493B2E, MUTED = 0xFF756347, GREEN = 0xFF4F6544, RED = 0xFF994D40;
    private BuildingManagerArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/building_manager/" + name + ".png");
    }
    static void sprite(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        g.blit(texture(name), x, y, 0, 0, w, h, w, h);
    }
    static void icon(GuiGraphics g, String name, int x, int y, int scale) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        sprite(g, name, 0, 0, 16, 16); g.pose().popPose();
    }
    static void box(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        int b = Math.min(4, Math.min(w, h) / 2);
        int[] src = {0, b, 32 - b}, span = {b, 32 - 2 * b, b};
        int[] xs = {x, x + b, x + w - b}, ys = {y, y + b, y + h - b};
        int[] ws = {b, w - 2 * b, b}, hs = {b, h - 2 * b, b};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
            for (int dy = 0; dy < hs[row]; dy += span[row]) for (int dx = 0; dx < ws[col]; dx += span[col])
                g.blit(texture(name), xs[col] + dx, ys[row] + dy, src[col], src[row],
                        Math.min(span[col], ws[col] - dx), Math.min(span[row], hs[row] - dy), 32, 32);
    }
    static void page(GuiGraphics g, int x, int y, int w, int h) {
        box(g, "cover", x, y, w, h);
        for (int dy = 0; dy < h - 10; dy += 64) for (int dx = 0; dx < w - 12; dx += 64)
            g.blit(texture("paper"), x + 6 + dx, y + 4 + dy, 0, 0,
                    Math.min(64, w - 12 - dx), Math.min(64, h - 10 - dy), 64, 64);
        for (int dy = 0; dy < h - 4; dy += 32)
            g.blit(texture("binding"), x + 3, y + 2 + dy, 0, 0, 16, Math.min(32, h - 4 - dy), 16, 32);
    }
    static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFFD0BB93);
        g.fill(x, y + 1, x + width, y + 2, 0xFFF9EFD7);
    }
}
