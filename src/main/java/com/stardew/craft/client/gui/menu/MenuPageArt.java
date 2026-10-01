package com.stardew.craft.client.gui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Authored menu inserts, always tiled at native pixel density. */
public final class MenuPageArt {
    public static final int INK = 0xFF623E2A, MUTED = 0xFF80613F;
    private MenuPageArt() { }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stardewcraft", "textures/gui/menu_pages/" + name + ".png");
    }
    public static void sprite(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        g.blit(texture(name), x, y, 0, 0, w, h, w, h);
    }
    public static void box(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        int b = Math.min(4, Math.min(w, h) / 2);
        if (b < 1) return;
        int[] src = {0, b, 32-b}, span = {b, 32-2*b, b};
        int[] xx = {x, x+b, x+w-b}, yy = {y, y+b, y+h-b};
        int[] ww = {b, w-2*b, b}, hh = {b, h-2*b, b};
        for (int r=0;r<3;r++) for (int c=0;c<3;c++)
            for (int dy=0;dy<hh[r];dy+=span[r]) for (int dx=0;dx<ww[c];dx+=span[c])
                g.blit(texture(name),xx[c]+dx,yy[r]+dy,src[c],src[r],Math.min(span[c],ww[c]-dx),Math.min(span[r],hh[r]-dy),32,32);
    }
    public static void tooltip(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int mx, int my) {
        int line=com.stardew.craft.client.font.StardewFonts.lineHeight(font);
        int wrap=Math.min(260,g.guiWidth()-24);
        var lines=font.split(text,wrap);
        while(lines.size()*(line+3)+13>g.guiHeight()-8 && wrap<g.guiWidth()-24) {
            wrap=Math.min(g.guiWidth()-24,wrap+64); lines=font.split(text,wrap);
        }
        int tw=0; for(var entry:lines)tw=Math.max(tw,font.width(entry));
        var box=MenuPageLayout.tooltip(mx,my,g.guiWidth(),g.guiHeight(),tw,lines.size(),line);
        g.pose().pushPose();g.pose().translate(0,0,600);
        box(g,"tooltip",box.x(),box.y(),box.width(),box.height());
        int y=box.y()+7;
        for(var entry:lines) {
            if(y+line>box.y()+box.height()-5)break;
            g.drawString(font,entry,box.x()+8,y,INK,false);y+=line+3;
        }
        g.pose().popPose();
    }
    public static void paper(GuiGraphics g, int x, int y, int w, int h) {
        box(g,"paper",x,y,w,h);
        for(int dy=0;dy<h-10;dy+=128) for(int dx=0;dx<w-10;dx+=128)
            g.blit(texture("paper_fibre"),x+5+dx,y+5+dy,0,0,Math.min(128,w-10-dx),Math.min(128,h-10-dy),128,128);
    }
}
