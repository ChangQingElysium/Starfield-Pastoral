package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class AnimalBirthArt {
    static final int INK = 0xFF5C4834, MUTED = 0xFF7B6747, GREEN = 0xFF536545, RED = 0xFF9B4936;
    private AnimalBirthArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/animal_birth/" + name + ".png");
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
    static void page(GuiGraphics g,AnimalBirthLayout.Page p) {
        box(g,"cover",p.x()-3,p.y()+4,p.width()+6,p.height());
        box(g,"card",p.x(),p.y(),p.width(),p.height());
        icon(g,"heart",p.x()+p.width()-30,p.y()-6,1);
    }
}
