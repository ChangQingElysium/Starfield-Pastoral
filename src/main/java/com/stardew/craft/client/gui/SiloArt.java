package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class SiloArt {
    static final int INK = 0xFF594331, MUTED = 0xFF796344, GREEN = 0xFF536545, RED = 0xFF9B4936;
    private SiloArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/silo/" + name + ".png");
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
    static void page(GuiGraphics g, SiloLayout.Page p,int titleWidth) {
        box(g,"header",p.x()-3,p.y()+4,p.width()+6,p.height());
        box(g,"frame",p.x(),p.y(),p.width(),p.height());
        box(g,"header",p.x()-4,p.y()+6,Math.min(p.width()-36,Math.max(116,titleWidth+60)),30);
        icon(g,"silo",p.x()+8,p.y()-1,2);
    }
    static void storage(GuiGraphics g,int x,int y,int w,int amount,int capacity) {
        box(g,"trough",x,y,w,12);
        int fill=SiloLayout.fillWidth(amount,capacity,w-6);
        for(int xx=0;xx<fill;xx+=16)
            g.blit(texture("grain"),x+3+xx,y+3,0,0,Math.min(16,fill-xx),6,16,16);
        for(int i=1;i<4;i++)g.fill(x+3+(w-6)*i/4,y+10,x+4+(w-6)*i/4,y+13,0xFFAD854E);
    }
}
