package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.SiloManagerMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Shared hay tally, with numeric hierarchy and a separate confirmation state. */
@SuppressWarnings("null")
public class SiloManagerScreen extends AbstractContainerScreen<SiloManagerMenu> {
    private enum Confirm { NONE, DEMOLISH, RELOCATE }
    private record Text(Component label,int x,int y,int width,int color) { }
    private Confirm confirm=Confirm.NONE;
    private SiloLayout.Page page;
    private int line,contentHeight,scroll,cooldown,hayY,countY,meterY;
    private SiloLayout.Counter counter;
    private boolean opened,dragging;
    private final ItemStack hay=new ItemStack(ModItems.HAY.get());
    private final List<Text> texts=new ArrayList<>();
    private final List<Button> actions=new ArrayList<>();
    private List<Object> lastState=List.of();

    public SiloManagerScreen(SiloManagerMenu menu,Inventory inventory,Component title) { super(menu,inventory,title); }
    private Component tr(String key,Object... args) { return Component.translatable("gui.stardew_craft.silo_manager."+key,args); }
    private int addText(Component text,int x,int y,int w,int color) {
        texts.add(new Text(text,x,y,w,color)); return y+font.split(text,Math.max(1,w)).size()*(line+3);
    }
    private int buttonHeight(Component text,int w) { return Math.max(24,font.split(text,Math.max(1,w-16)).size()*(line+2)+10); }
    @Override protected void init() {
        super.init();font=StardewFonts.small();line=StardewFonts.lineHeight(font);rebuild();
        if(!opened){opened=true;minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(),1f,.25f));}
    }
    private void rebuild() {
        clearWidgets();actions.clear();setFocused(null);
        int cw=Math.min(280,width-20)-32,bw=(cw-8)/2;
        int demolishW=Math.min(cw/3,Math.max(48,font.width(tr("demolish"))+24));
        int moveW=Math.min(cw-demolishW-12,Math.max(88,font.width(tr("relocate"))+30));
        int footer=confirm!=Confirm.NONE?Math.max(buttonHeight(tr("dialog.back"),bw),buttonHeight(tr(confirm==Confirm.DEMOLISH?"demolish":"relocate"),cw-bw-8)):
                menu.isFormed()?Math.max(buttonHeight(tr("demolish"),demolishW),buttonHeight(tr("relocate"),moveW)):buttonHeight(tr("build"),Math.min(120,cw));
        page=SiloLayout.fit(width,height,line,footer,112);layoutBody();
        page=SiloLayout.fit(width,height,line,footer,contentHeight);
        leftPos=page.x();topPos=page.y();imageWidth=page.width();imageHeight=page.height();
        scroll=SiloLayout.clampScroll(scroll,contentHeight,page.bottom()-page.top());
        button(page.x()+page.width()-32,page.y()+8,24,Math.max(24,line+10),Component.translatable("gui.done"),false,false,true,this::onClose);
        if(confirm!=Confirm.NONE) {
            Button back=button(page.contentX(),page.footerY(),bw,footer,tr("dialog.back"),false,false,false,()->switchConfirm(Confirm.NONE));
            actions.add(button(page.contentX()+bw+8,page.footerY(),cw-bw-8,footer,tr(confirm==Confirm.DEMOLISH?"demolish":"relocate"),true,confirm==Confirm.DEMOLISH,false,this::submitConfirm));
            setInitialFocus(back);
        } else if(menu.isFormed()) {
            actions.add(button(page.contentX(),page.footerY(),demolishW,footer,tr("demolish"),false,true,false,()->switchConfirm(Confirm.DEMOLISH)));
            actions.add(button(page.contentX()+cw-moveW,page.footerY(),moveW,footer,tr("relocate"),false,false,false,()->switchConfirm(Confirm.RELOCATE)));
        } else actions.add(button(page.contentX()+cw-Math.min(120,cw),page.footerY(),Math.min(120,cw),footer,tr("build"),true,false,false,()->submit(SiloManagerMenu.ACTION_BUILD)));
        updateActions();lastState=snapshot();
    }
    private void layoutBody() {
        texts.clear();hayY=-1;
        if(confirm!=Confirm.NONE) {
            String stem="dialog."+(confirm==Confirm.DEMOLISH?"demolish":"relocate")+".";
            int y=addText(tr(stem+"title"),0,0,page.contentWidth(),SiloArt.INK)+14;
            y=addText(tr(stem+"line1"),0,y,page.contentWidth(),SiloArt.INK)+10;
            contentHeight=addText(tr(stem+"line2"),0,y,page.contentWidth(),confirm==Confirm.DEMOLISH?SiloArt.RED:SiloArt.MUTED)+4;
            return;
        }
        int w=page.contentWidth(),y;
        if(menu.isFormed()) {
            int labelEnd=addText(hay.getHoverName(),44,0,w-44,SiloArt.MUTED);
            countY=labelEnd+4;
            counter=SiloLayout.counter(w-44,font.width(Integer.toString(menu.getHayAmount())),font.width("/ "+menu.getHayCapacity()),line);
            int rowHeight=Math.max(32,countY+counter.height());
            hayY=(rowHeight-32)/2;
            meterY=rowHeight+12;
            y=addText(tr("shared_hint"),0,meterY+24,w,SiloArt.MUTED)+4;
            if(!menu.canManage())y=addText(Component.translatable("message.stardew_craft.manager.relocate_owner_mismatch"),0,y+6,w,SiloArt.RED)+4;
        } else {
            y=addText(tr("stage.unformed"),0,0,w,SiloArt.MUTED)+12;
            y=addText(tr("build_hint"),0,y,w,SiloArt.INK)+12;
            y=addText(tr(menu.canBuild()?"ready":"not_ready"),0,y,w,menu.canBuild()?SiloArt.GREEN:SiloArt.RED)+4;
        }
        contentHeight=y;
    }
    private List<Object> snapshot() { return List.of(menu.isFormed(),menu.canBuild(),menu.canManage(),menu.getHayAmount(),menu.getHayCapacity()); }
    @Override protected void containerTick() {
        super.containerTick();if(cooldown>0)cooldown--;
        if(!snapshot().equals(lastState)) {
            if(!menu.isFormed()||!menu.canManage())confirm=Confirm.NONE;
            rebuild();
        }
        updateActions();
    }
    private void updateActions() { for(Button b:actions)b.active=cooldown==0&&(menu.isFormed()?menu.canManage():menu.canBuild()); }
    private void switchConfirm(Confirm value) { confirm=value;scroll=0;rebuild(); }
    private void submit(int action) {
        if(cooldown>0||minecraft.gameMode==null)return;
        if(action==SiloManagerMenu.ACTION_BUILD ? menu.isFormed()||!menu.canBuild() : !menu.isFormed()||!menu.canManage())return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);cooldown=8;updateActions();
    }
    private void submitConfirm() {
        if(confirm==Confirm.NONE||!menu.canManage()||cooldown>0)return;
        submit(confirm==Confirm.DEMOLISH?SiloManagerMenu.ACTION_DEMOLISH:SiloManagerMenu.ACTION_RELOCATE);switchConfirm(Confirm.NONE);
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int mx,int my) {
        SiloArt.page(g,page,Math.min(font.width(title),page.width()-96));
        String heading=title.getString();int hw=page.width()-96;
        if(font.width(heading)>hw)heading=font.plainSubstrByWidth(heading,hw-font.width("..."))+"...";
        g.drawString(font,heading,page.x()+48,page.y()+16,0xFFFFEAC3,false);
        g.enableScissor(page.contentX(),page.top(),page.contentX()+page.contentWidth(),page.bottom());
        g.pose().pushPose();g.pose().translate(page.contentX(),page.top()-scroll,0);

        for(Text t:texts){int y=t.y;for(var part:font.split(t.label,Math.max(1,t.width))){g.drawString(font,part,t.x,y,t.color,false);y+=line+3;}}
        if(hayY>=0) {
            CommonGuiTextures.drawItem(g,hay,0,hayY,2);
            g.pose().pushPose();g.pose().translate(44,countY,0);g.pose().scale(counter.scale(),counter.scale(),1);
            g.drawString(font,Integer.toString(menu.getHayAmount()),0,0,SiloArt.INK,false);g.pose().popPose();
            g.drawString(font,"/ "+menu.getHayCapacity(),44+counter.capacityX(),countY+(counter.stacked()?line*counter.scale()+4:(counter.scale()-1)*line),SiloArt.MUTED,false);
            SiloArt.storage(g,0,meterY,page.contentWidth(),menu.getHayAmount(),menu.getHayCapacity());
        }
        g.pose().popPose();g.disableScissor();
        int view=page.bottom()-page.top(),max=Math.max(0,contentHeight-view);
        if(max>0) {
            int thumb=Math.max(12,view*view/contentHeight),y=page.top()+(view-thumb)*scroll/max;
            g.fill(page.x()+page.width()-14,page.top(),page.x()+page.width()-12,page.bottom(),0xFFD4C4A3);
            g.fill(page.x()+page.width()-15,y,page.x()+page.width()-11,y+thumb,0xFF99754D);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) { }
    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        renderTransparentBackground(g);super.render(g,mx,my,tick);
        if(hayY>=0&&my>=page.top()&&my<page.bottom()&&inside(mx,my,page.contentX(),page.top()+hayY-scroll,32,32))g.renderTooltip(minecraft.font,hay,mx,my);
        else if(inside(mx,my,page.x()+48,page.y()+12,page.width()-96,line+10))g.renderTooltip(font,font.split(title,Math.min(240,width-24)),mx,my);
    }
    private Button button(int x,int y,int w,int h,Component label,boolean primary,boolean danger,boolean close,Runnable action) {
        return addRenderableWidget(new Button(x,y,w,h,label,unused->action.run(),supplier->supplier.get()) {
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float tick) {
                String skin=!active?"disabled":primary?(danger?"danger":"button")+(isHoveredOrFocused()?"_hover":""):"secondary"+(isHoveredOrFocused()?"_hover":"");
                boolean quiet=close||(!primary&&danger&&confirm==Confirm.NONE);
                if(!quiet||isHoveredOrFocused())SiloArt.box(g,skin,getX(),getY(),getWidth(),getHeight());
                if(close)SiloArt.sprite(g,"close",getX()+(getWidth()-8)/2,getY()+(getHeight()-8)/2,8,8);
                else {var ll=font.split(getMessage(),getWidth()-16);int yy=getY()+(getHeight()-ll.size()*(line+2)+2)/2;
                    for(var part:ll){g.drawString(font,part,getX()+(getWidth()-font.width(part))/2,yy,!active?SiloArt.MUTED:primary?0xFFFFF1CC:danger?SiloArt.RED:SiloArt.INK,false);yy+=line+2;}}
                if(isFocused())g.renderOutline(getX()+3,getY()+3,getWidth()-6,getHeight()-6,primary?0xFFE7C48B:SiloArt.INK);
            }
            @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){manager.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(),1f,.25f));}
        });
    }
    private boolean inside(double x,double y,int rx,int ry,int w,int h){return x>=rx&&x<rx+w&&y>=ry&&y<ry+h;}
    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        double horizontal = 0.0D; // PORT(1.20.1): no horizontal scroll before 1.20.2
        if(vertical!=0&&inside(x,y,page.contentX(),page.top(),page.contentWidth(),page.bottom()-page.top())){scroll=SiloLayout.clampScroll(scroll+(vertical<0?24:-24),contentHeight,page.bottom()-page.top());return true;}
        return super.mouseScrolled(x, y, vertical);
    }
    private void drag(double y){int view=page.bottom()-page.top(),max=Math.max(0,contentHeight-view),thumb=Math.min(view,Math.max(12,view*view/Math.max(1,contentHeight)));scroll=(int)Math.round(Math.max(0,Math.min(1,(y-page.top()-thumb/2.0)/Math.max(1,view-thumb)))*max);}
    @Override public boolean mouseClicked(double x,double y,int b){if(b==0&&contentHeight>page.bottom()-page.top()&&inside(x,y,page.x()+page.width()-17,page.top(),9,page.bottom()-page.top())){dragging=true;drag(y);return true;}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){if(dragging&&b==0){drag(y);return true;}return super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){if(dragging){dragging=false;return true;}return super.mouseReleased(x,y,b);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256&&confirm!=Confirm.NONE){switchConfirm(Confirm.NONE);return true;}if(key==266||key==267){scroll=SiloLayout.clampScroll(scroll+(key==267?60:-60),contentHeight,page.bottom()-page.top());return true;}return super.keyPressed(key,scan,mods);}
}
