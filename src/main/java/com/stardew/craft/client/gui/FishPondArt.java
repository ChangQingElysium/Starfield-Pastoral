package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class FishPondArt {
    static final int INK = 0xFF474C39, MUTED = 0xFF6D7054, GREEN = 0xFF486C50, RED = 0xFFA2543E;
    private FishPondArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/fish_pond/" + name + ".png");
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
    private static void tile(GuiGraphics g, String name, int x, int y, int w, int h) {
        for (int yy=0;yy<h;yy+=64) for (int xx=0;xx<w;xx+=64)
            g.blit(texture(name),x+xx,y+yy,0,0,Math.min(64,w-xx),Math.min(64,h-yy),64,64);
    }
    static void page(GuiGraphics g, FishPondLayout.Page p) {
        box(g,"frame",p.x(),p.y(),p.width(),p.height());
        tile(g,"paper",p.x()+5,p.y()+5,p.width()-10,p.height()-10);
    }
    static void pond(GuiGraphics g, int x, int y, int w, int h) {
        box(g,"pond",x,y,w,h);
        tile(g,"water",x+4,y+4,w-8,h-8);
        icon(g,"reeds",x-2,y+h-29,2);
        icon(g,"lily",x+w-27,y+8,1);
    }
    static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFFD0BB93);
        g.fill(x, y + 1, x + width, y + 2, 0xFFF9EFD7);
    }
}
