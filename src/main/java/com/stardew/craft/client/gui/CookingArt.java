package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class CookingArt {
    static final int INK = 0xFF4C3D31, MUTED = 0xFF756047, GREEN = 0xFF536542, RED = 0xFF9A4936;
    private CookingArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/cooking/" + name + ".png");
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
    private static void tile(GuiGraphics g, String name, int x, int y, int w, int h, int size) {
        for (int yy = 0; yy < h; yy += size) for (int xx = 0; xx < w; xx += size)
            g.blit(texture(name), x + xx, y + yy, 0, 0, Math.min(size, w - xx), Math.min(size, h - yy), size, size);
    }
    static void page(GuiGraphics g, CookingLayout.Page p) {
        int x = p.x(), y = p.y(), w = p.width(), h = p.height();
        int bookH = p.wide() ? p.inventoryY() - y - 7 : h;
        if (p.wide()) {
            box(g, "cloth_edge", x + 12, p.inventoryY() - 6, w - 24, 87);
            tile(g, "cloth", x + 16, p.inventoryY() - 2, w - 32, 77, 32);
        }
        box(g, "cover", x + 2, y + 8, w - 4, bookH - 3);
        if (p.wide()) {
            int split = p.detailX() + p.detailWidth() + 6;
            box(g, "leaf_left", x + 7, y + 12, split - x - 2, bookH - 15);
            tile(g, "paper_left", x + 12, y + 17, split - x - 12, bookH - 30, 64);
            box(g, "leaf_right", split + 8, y + 3, x + w - split - 15, bookH - 6);
            tile(g, "paper_right", split + 13, y + 8, x + w - split - 25, bookH - 21, 64);
            for (int yy = 0; yy < bookH - 15; yy += 32)
                g.blit(texture("gutter"), split - 2, y + 12 + yy, 0, 0, 16, Math.min(32, bookH - 15 - yy), 16, 32);
            icon(g, "page_fold", x + w - 25, y + bookH - 20, 1);
            icon(g, "bookmark", split + 20, y + bookH - 6, 1);
            icon(g, "pot", x + 32, p.inventoryY() + 20, 2);
            icon(g, "herbs", x + w - 62, p.inventoryY() + 26, 2);
        } else {
            box(g, "leaf_right", x + 7, y + 3, w - 14, h - 6);
            tile(g, "paper_right", x + 12, y + 8, w - 24, h - 21, 64);
            icon(g, "page_fold", x + w - 25, y + h - 20, 1);
        }
    }
    static void dish(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y, int scale, float shade) {
        g.flush();
        RenderSystem.setShaderColor(shade, shade, shade, 1f);
        try {
            com.stardew.craft.client.gui.common.CommonGuiTextures.drawItem(g, stack, x, y, scale);
            g.flush();
        } finally { RenderSystem.setShaderColor(1f, 1f, 1f, 1f); }
    }
    static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFFD7C6A7);
    }
}
