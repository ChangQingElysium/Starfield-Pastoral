package com.stardew.craft.client.gui.menu;

import com.stardew.craft.client.gui.FarmBrowserArt;
import com.stardew.craft.client.gui.FarmPermissionClientCache;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.network.payload.FarmPermissionUpdatePayload;
import com.stardew.craft.network.payload.FarmPermSyncPayload.PlayerPermEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.UUID;

/** Player directory and permission editor share the original inventory-page bounds. */
final class FarmManagementPage {
    private int scroll, choiceScroll;
    private UUID selected;
    private boolean editingDefault;
    private Component hoveredTooltip;
    private MenuPageLayout.Farm layout;
    private int choiceTop, choiceHeight, visibleChoices;
    Component hoveredTooltip() { return hoveredTooltip; }
    void reset() { scroll=0; selected=null; editingDefault=false; choiceScroll=0; }
    private boolean editing() { return editingDefault || selected!=null; }
    private static Component text(String key) { return Component.translatable("gui.stardewcraft.farm_mgmt."+key); }
    private static Component permission(int level) {
        return level<0?Component.translatable("stardewcraft.menu_pages.use_default"):
                Component.translatable("gui.stardewcraft.farm_entry."+switch(level){case 0->"perm_none";case 1->"perm_visit";default->"perm_full";});
    }
    private static String clip(Font f,Component c,int w) {
        return f.width(c)<=w?c.getString():f.plainSubstrByWidth(c.getString(),Math.max(0,w-f.width("...")))+"...";
    }
    void render(GuiGraphics g,Font font,int x,int y,int width,int height,int mx,int my) {
        hoveredTooltip=null;
        int line=StardewFonts.lineHeight(font);
        layout=MenuPageLayout.farm(x,y,width,height,line);
        if(!FarmPermissionClientCache.hasData()) {
            g.drawString(font,clip(font,text("title"),width-8),x+4,y,MenuPageArt.INK,false);
            g.drawString(font,clip(font,text("loading"),width-8),x+4,y+line+16,MenuPageArt.MUTED,false);
            return;
        }
        var players=FarmPermissionClientCache.getPlayers();
        // A departing player cannot leave an editor targeting a different roster entry.
        if(selected!=null && players.stream().noneMatch(p->p.uuid().equals(selected)))selected=null;
        if(editing()) { renderEditor(g,font,mx,my); return; }
        g.drawString(font,clip(font,text("title"),width-8),x+4,y,MenuPageArt.INK,false);
        drawEntry(g,font,text("default_perm"),permission(FarmPermissionClientCache.getDefaultPerm()),
                layout.defaultY(),layout.defaultHeight(),true,mx,my);
        if(inside(mx,my,x,layout.defaultY(),width,layout.defaultHeight()))
            hoveredTooltip=text("default_perm.desc").copy().append("\n").append(permission(FarmPermissionClientCache.getDefaultPerm()));
        g.drawString(font,clip(font,text("online_players"),width-8),x+4,layout.listTitleY(),MenuPageArt.MUTED,false);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,players.size()-layout.visibleRows())));
        for(int i=0;i<Math.min(layout.visibleRows(),players.size()-scroll);i++) {
            var p=players.get(i+scroll);
            Component status=permission(p.permission()<0?FarmPermissionClientCache.getDefaultPerm():p.permission());
            if(p.permission()<0)status=status.copy().append(" · ").append(text("using_default"));
            drawEntry(g,font,Component.literal(p.name()),status,layout.listY()+i*layout.rowHeight(),layout.rowHeight(),false,mx,my);
        }
        if(players.isEmpty())g.drawString(font,clip(font,text("no_players"),width-8),x+4,layout.listY()+4,MenuPageArt.MUTED,false);
        drawScroll(g,layout.listY(),layout.visibleRows()*layout.rowHeight(),players.size(),layout.visibleRows(),scroll);
    }
    private void drawEntry(GuiGraphics g,Font f,Component name,Component status,int y,int h,boolean defaults,int mx,int my) {
        int x=layout.x(),w=layout.width(),line=StardewFonts.lineHeight(f);
        boolean hover=inside(mx,my,x,y,w,h-2);
        if(defaults||hover)MenuPageArt.box(g,defaults?"row_selected":"tab_hover",x,y,w,h-2);
        else g.fill(x+5,y+h-3,x+w-5,y+h-2,0x35BA9A6B);
        int ty=y+(h-2-line*2-2)/2;
        g.drawString(f,clip(f,name,w-26),x+6,ty,MenuPageArt.INK,false);
        g.drawString(f,clip(f,status,w-26),x+6,ty+line+2,MenuPageArt.MUTED,false);
        CommonGuiTextures.drawForwardArrow(g,x+w-17,y+(h-11)/2,1f);
        if(hover && (f.width(name)>w-26||f.width(status)>w-26))hoveredTooltip=name.copy().append("\n").append(status);
    }
    private void renderEditor(GuiGraphics g,Font f,int mx,int my) {
        int x=layout.x(),y=layout.y(),w=layout.width(),line=StardewFonts.lineHeight(f);
        var player=FarmPermissionClientCache.getPlayers().stream().filter(p->p.uuid().equals(selected)).findFirst().orElse(null);
        Component title=editingDefault?text("default_perm"):Component.literal(player.name());
        MenuPageArt.box(g,inside(mx,my,x,y,22,line+8)?"tab_hover":"tab",x,y,22,line+8);
        CommonGuiTextures.drawBackArrow(g,x+5,y+(line+8-11)/2,1f);
        g.drawString(f,clip(f,title,w-30),x+28,y+4,MenuPageArt.INK,false);
        if(inside(mx,my,x+26,y,w-26,line+8)&&f.width(title)>w-30)hoveredTooltip=title;
        int choices=editingDefault?3:4,maxLines=1;
        for(int i=0;i<choices;i++)maxLines=Math.max(maxLines,f.split(permission(i==3?-1:i),w-42).size());
        choiceHeight=Math.max(28,maxLines*(line+2)+10);
        choiceTop=y+line+18;
        visibleChoices=Math.max(1,Math.min(choices,(layout.bottom()-choiceTop)/choiceHeight));
        choiceScroll=Math.max(0,Math.min(choiceScroll,choices-visibleChoices));
        int active=editingDefault?FarmPermissionClientCache.getDefaultPerm():player.permission();
        for(int i=0;i<visibleChoices;i++) {
            int index=i+choiceScroll,level=index==3?-1:index,cy=choiceTop+i*choiceHeight;
            boolean hover=inside(mx,my,x,cy,w,choiceHeight-3);
            if(level>=0)FarmBrowserArt.permission(g,switch(level){case 0->"none";case 1->"visit";default->"full";},x,cy,w,choiceHeight-3);
            else MenuPageArt.box(g,"row_selected",x,cy,w,choiceHeight-3);
            if(hover)g.renderOutline(x+3,cy+2,w-6,choiceHeight-7,0xFFAA8454);
            MenuPageArt.sprite(g,"select_"+(active==level?"on":"off"),x+10,cy+(choiceHeight-15)/2,12,12);
            var lines=f.split(permission(level),w-42);int ty=cy+(choiceHeight-3-lines.size()*(line+2))/2;
            for(var text:lines){g.drawString(f,text,x+30,ty,MenuPageArt.INK,false);ty+=line+2;}
            if(hover&&level<0)hoveredTooltip=text("default_perm.desc").copy().append("\n").append(permission(FarmPermissionClientCache.getDefaultPerm()));
        }
        drawScroll(g,choiceTop,visibleChoices*choiceHeight,choices,visibleChoices,choiceScroll);
    }
    private void drawScroll(GuiGraphics g,int y,int height,int total,int visible,int offset) {
        if(total<=visible||visible==0)return;
        int thumb=Math.max(12,height*visible/total),sy=y+(height-thumb)*offset/Math.max(1,total-visible);
        g.fill(layout.x()+layout.width()+2,y,layout.x()+layout.width()+4,y+height,0xFFCAB28B);
        g.fill(layout.x()+layout.width()+1,sy,layout.x()+layout.width()+5,sy+thumb,0xFF95764F);
    }
    boolean click(int mx,int my,Font font) {
        if(layout==null||!FarmPermissionClientCache.hasData())return false;
        var players=FarmPermissionClientCache.getPlayers();
        if(editing()) {
            if(inside(mx,my,layout.x(),layout.y(),22,StardewFonts.lineHeight(font)+8)){selected=null;editingDefault=false;choiceScroll=0;return true;}
            for(int i=0;i<visibleChoices;i++)if(inside(mx,my,layout.x(),choiceTop+i*choiceHeight,layout.width(),choiceHeight-3)){
                int index=i+choiceScroll,level=index==3?-1:index;
                if(editingDefault) {
                    if(level!=FarmPermissionClientCache.getDefaultPerm()){
                        PacketDistributor.sendToServer(new FarmPermissionUpdatePayload(2,new UUID(0,0),level));
                        FarmPermissionClientCache.update(level,players);
                    }
                } else for(int j=0;j<players.size();j++)if(players.get(j).uuid().equals(selected)&&players.get(j).permission()!=level) {
                    var p=players.get(j);PacketDistributor.sendToServer(new FarmPermissionUpdatePayload(level<0?1:0,p.uuid(),level));
                    var changed=new ArrayList<>(players);changed.set(j,new PlayerPermEntry(p.uuid(),p.name(),level));
                    FarmPermissionClientCache.update(FarmPermissionClientCache.getDefaultPerm(),changed);break;
                }
                return true;
            }
            return false;
        }
        if(inside(mx,my,layout.x(),layout.defaultY(),layout.width(),layout.defaultHeight()-2)){editingDefault=true;choiceScroll=0;return true;}
        for(int i=0;i<Math.min(layout.visibleRows(),players.size()-scroll);i++)if(inside(mx,my,layout.x(),layout.listY()+i*layout.rowHeight(),layout.width(),layout.rowHeight()-2)){
            selected=players.get(i+scroll).uuid();choiceScroll=0;return true;
        }
        return false;
    }
    boolean scroll(double mx,double my,double dy) {
        if(layout==null)return false;
        if(editing()) {
            if(!inside(mx,my,layout.x(),choiceTop,layout.width(),visibleChoices*choiceHeight))return false;
            int count=editingDefault?3:4;choiceScroll=Math.max(0,Math.min(count-visibleChoices,choiceScroll+(dy>0?-1:dy<0?1:0)));return true;
        }
        if(!inside(mx,my,layout.x(),layout.listY(),layout.width(),layout.listBottom()-layout.listY()))return false;
        scroll=Math.max(0,Math.min(Math.max(0,FarmPermissionClientCache.getPlayers().size()-layout.visibleRows()),scroll+(dy>0?-1:dy<0?1:0)));return true;
    }
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
}
