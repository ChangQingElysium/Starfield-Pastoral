package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.FishPondManagerMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Pond observation window. All population, requirements and permissions are server snapshots. */
@SuppressWarnings("null")
public class FishPondManagerScreen extends AbstractContainerScreen<FishPondManagerMenu> implements com.stardew.craft.port.PortContainerScreen {
    private enum Confirm { NONE, CLEAR, DEMOLISH }
    private record Text(Component label, int x, int y, int width, int color) { }
    private record Icon(ItemStack item, int x, int y, int scale) { }
    private record Requirement(ItemStack icon, Component label, int current, int needed, boolean met, int y, FishPondLayout.Row layout) { }
    private Confirm confirm = Confirm.NONE;
    private FishPondLayout.Page page;
    private int line, scroll, contentHeight, pondHeight, populationY, requestTagY, mx, my, cooldown;
    private boolean opened, dragging, hasPond;
    private final List<Text> texts = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();
    private final List<Requirement> requirements = new ArrayList<>();
    private final List<Button> actions = new ArrayList<>();
    private List<Object> lastState = List.of();
    private ItemStack hoverItem = ItemStack.EMPTY;
    private Component hoverText;

    public FishPondManagerScreen(FishPondManagerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    private Component tr(String key, Object... args) { return Component.translatable("gui.stardew_craft.fish_pond_manager." + key, args); }
    private int textHeight(Component text, int width) { return font.split(text, Math.max(1,width)).size() * (line + 3); }
    private int buttonHeight(Component text, int width) { return Math.max(24, font.split(text, Math.max(1,width-14)).size()*(line+2)+10); }
    private int addText(Component label,int x,int y,int width,int color) {
        texts.add(new Text(label,x,y,width,color)); return y+textHeight(label,width);
    }
    @Override protected void init() {
        super.init(); font=StardewFonts.small(); line=StardewFonts.lineHeight(font); rebuild();
        if (!opened) { opened=true; minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(),1f,.25f)); }
    }
    private void rebuild() {
        setFocused(null); clearWidgets(); actions.clear();
        int cw=Math.min(420,width-12)-32;
        int footerH;
        if(confirm!=Confirm.NONE) footerH=Math.max(buttonHeight(tr("dialog.back"),(cw-8)/2),buttonHeight(tr(confirm==Confirm.CLEAR?"clear":"demolish"),(cw-8)/2));
        else if(menu.isFormed()) {
            int bw=(cw-12)/3;
            footerH=Math.max(buttonHeight(tr("clear"),bw),Math.max(buttonHeight(tr("demolish"),bw),buttonHeight(Component.translatable("building.stardewcraft.manage_building"),cw-2*(bw+6))));
        } else footerH=buttonHeight(tr("build"),cw);
        page=FishPondLayout.fit(width,height,line,footerH,306);
        layoutBody();
        int preferred=Math.min(306,Math.max(confirm==Confirm.NONE?220:160,line+36+contentHeight+footerH+22));
        page=FishPondLayout.fit(width,height,line,footerH,preferred);
        leftPos=page.x(); topPos=page.y(); imageWidth=page.width(); imageHeight=page.height();
        scroll=FishPondLayout.clampScroll(scroll,contentHeight,page.bottom()-page.top());
        button(page.x()+page.width()-40,page.y()+12,24,Math.max(24,line+10),Component.translatable("gui.done"),false,false,true,this::onClose);
        if(confirm!=Confirm.NONE) {
            int bw=(cw-8)/2;
            Button back=button(page.contentX(),page.footerY(),bw,footerH,tr("dialog.back"),false,false,false,()->switchConfirm(Confirm.NONE));
            actions.add(button(page.contentX()+bw+8,page.footerY(),cw-bw-8,footerH,tr(confirm==Confirm.CLEAR?"clear":"demolish"),true,true,false,this::submitConfirm));
            setInitialFocus(back);
        } else if(menu.isFormed()) {
            int bw=(cw-12)/3;
            actions.add(button(page.contentX(),page.footerY(),bw,footerH,tr("clear"),false,true,false,()->switchConfirm(Confirm.CLEAR)));
            actions.add(button(page.contentX()+bw+6,page.footerY(),bw,footerH,tr("demolish"),false,true,false,()->switchConfirm(Confirm.DEMOLISH)));
            actions.add(button(page.contentX()+2*(bw+6),page.footerY(),cw-2*(bw+6),footerH,Component.translatable("building.stardewcraft.manage_building"),true,false,false,()->submit(FishPondManagerMenu.ACTION_BUILD_OR_REFRESH)));
        } else actions.add(button(page.contentX(),page.footerY(),cw,footerH,tr("build"),true,false,false,()->submit(FishPondManagerMenu.ACTION_BUILD_OR_REFRESH)));
        updateActions(); lastState=snapshot();
    }
    private void layoutBody() {
        texts.clear(); icons.clear(); requirements.clear(); populationY=-1; requestTagY=-1;
        int cw=page.contentWidth(); hasPond=confirm==Confirm.NONE;
        if(!hasPond) {
            String stem="dialog."+(confirm==Confirm.CLEAR?"clear":"demolish")+".";
            int y=addText(tr(stem+"title"),0,0,cw,FishPondArt.INK)+16;
            y=addText(tr(stem+"line1"),0,y,cw,FishPondArt.INK)+12;
            y=addText(tr(stem+"line2"),0,y,cw,confirm==Confirm.DEMOLISH?FishPondArt.RED:FishPondArt.MUTED)+8;
            contentHeight=y; return;
        }
        pondHeight=page.columns()?92:72;
        int summaryX=page.columns()?0:page.pondWidth()+14;
        int summaryW=page.columns()?page.pondWidth():cw-summaryX;
        int sy=page.columns()?pondHeight+10:0;
        ItemStack fish=menu.getFishPreviewStack();
        sy=addText(!menu.isFormed()?tr("unformed"):fish.isEmpty()?tr("no_fish"):fish.getHoverName(),summaryX,sy,summaryW,FishPondArt.INK)+8;
        if(menu.isFormed() && !fish.isEmpty()) {
            sy=addText(tr("population",menu.getCurrentPopulation(),menu.getMaxPopulation()),summaryX,sy,summaryW,FishPondArt.INK)+4;
            populationY=sy;sy+=12;
        }
        if(menu.hasGoldenAnimalCracker()) {
            icons.add(new Icon(new ItemStack(ModItems.GOLDEN_ANIMAL_CRACKER.get()),summaryX,sy,1));
            sy=addText(tr("golden_cracker_yes"),summaryX+24,sy,summaryW-24,FishPondArt.MUTED)+8;
        }
        int noteY=page.columns()?0:Math.max(pondHeight,sy)+16;
        int nx=page.noteX(),nw=page.noteWidth(),y=noteY;
        if(menu.isOwnerMismatch()) y=addText(tr("owner_mismatch"),nx,y,nw,FishPondArt.RED)+12;
        if(menu.isFormed()) {
            if(menu.hasUnresolvedRequest()) {
                requestTagY=y;
                y=addText(tr("request_bring"),nx+24,y+2,nw-24,FishPondArt.INK)+12;
                ItemStack needed=menu.getNeededItemPreviewStack();
                icons.add(new Icon(needed.isEmpty()?new ItemStack(Items.PAPER):needed,nx,y,2));
                int end=addText(needed.isEmpty()?tr("status_unknown_item"):needed.getHoverName(),nx+42,y,nw-42,FishPondArt.INK)+4;
                end=addText(tr("request_needed",menu.getNeededItemCount()),nx+42,end,nw-42,FishPondArt.GREEN);
                y=Math.max(y+40,end)+14;
            }
            y=addText(menu.getStatusText(),nx,y,nw,FishPondArt.MUTED)+12;
            if(fish.isEmpty()) y=addText(tr("status_no_fish"),nx,y,nw,FishPondArt.INK)+8;
            else if(menu.hasCompletedRequest()) y=addText(tr("request_complete"),nx,y,nw,FishPondArt.GREEN)+8;
        } else {
            y=addText(tr("requirements"),nx,y,nw,FishPondArt.INK)+10;
            boolean waterMet=menu.getWaterCellCount()>=menu.getRequiredWaterCells() && menu.getCurrentWaterWidth()>=menu.getRequiredWaterWidth() && menu.getCurrentWaterLength()>=menu.getRequiredWaterLength();
            y=addRequirement(new ItemStack(Items.WATER_BUCKET),tr("need.water",menu.getCurrentWaterWidth(),menu.getCurrentWaterLength(),menu.getRequiredWaterWidth(),menu.getRequiredWaterLength()),menu.getWaterCellCount(),menu.getRequiredWaterCells(),waterMet,y);
            y=addRequirement(new ItemStack(ModItems.FISH_NET.get()),tr("need.net"),menu.getNetCount(),menu.getRequiredNetCount(),menu.getNetCount()>=menu.getRequiredNetCount(),y);
            y=addRequirement(new ItemStack(ModItems.FISH_POND_BUCKET.get()),tr("need.bucket"),menu.getCurrentBucketCount(),menu.getRequiredBucketCount(),menu.getCurrentBucketCount()==menu.getRequiredBucketCount(),y);
            y=addText(tr(menu.canBuild()?"ready":"not_ready"),nx,y+8,nw,menu.canBuild()?FishPondArt.GREEN:FishPondArt.RED)+8;
        }
        contentHeight=Math.max(Math.max(pondHeight,sy),y)+4;
    }
    private int addRequirement(ItemStack icon,Component label,int current,int needed,boolean met,int y) {
        int countW=font.width(current+" / "+needed);
        int labelW=FishPondLayout.labelWidth(page.noteWidth(),countW);
        var row=FishPondLayout.row(page.noteWidth(),countW,textHeight(label,labelW),line);
        requirements.add(new Requirement(icon,label,current,needed,met,y,row)); return y+row.height()+4;
    }
    private void switchConfirm(Confirm value) { confirm=value;scroll=0;rebuild(); }
    private void updateActions() {
        boolean allowed=cooldown==0 && (menu.isFormed()?menu.canManagePond():menu.canBuild()&&!menu.isOwnerMismatch());
        for(Button b:actions)b.active=allowed;
    }
    private void submit(int action) {
        updateActions();
        boolean allowed=action==FishPondManagerMenu.ACTION_BUILD_OR_REFRESH ? (menu.isFormed()?menu.canManagePond():menu.canBuild()&&!menu.isOwnerMismatch()) : menu.canManagePond();
        if(!allowed || cooldown>0 || minecraft.gameMode==null)return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);cooldown=8;updateActions();
    }
    private void submitConfirm() {
        if(confirm==Confirm.NONE || !menu.canManagePond() || cooldown>0)return;
        submit(confirm==Confirm.CLEAR?FishPondManagerMenu.ACTION_CLEAR_POND:FishPondManagerMenu.ACTION_DEMOLISH);
        switchConfirm(Confirm.NONE);
    }
    private List<Object> snapshot() {
        return List.of(menu.isFormed(),menu.isOwnerMismatch(),menu.getFishPreviewStack().getItem(),menu.getCurrentPopulation(),menu.getMaxPopulation(),menu.getNeededItemPreviewStack().getItem(),menu.getNeededItemCount(),menu.hasCompletedRequest(),menu.hasGoldenAnimalCracker(),menu.canBuild(),menu.getWaterCellCount(),menu.getNetCount(),menu.getCurrentBucketCount(),menu.getCurrentWaterWidth(),menu.getCurrentWaterLength(),menu.getRequiredWaterCells(),menu.getRequiredWaterWidth(),menu.getRequiredWaterLength(),menu.getRequiredNetCount(),menu.getRequiredBucketCount(),menu.getStatusText().getString());
    }
    @Override protected void containerTick() {
        super.containerTick();if(cooldown>0)cooldown--;
        if(!snapshot().equals(lastState)) {
            if(!menu.canManagePond())confirm=Confirm.NONE;
            rebuild();
        }
        updateActions();
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int mouseX,int mouseY) {
        FishPondArt.page(g,page);
        String heading=title.getString();int hw=page.contentWidth()-32;
        if(font.width(heading)>hw)heading=font.plainSubstrByWidth(heading,hw-font.width("..."))+"...";
        g.drawString(font,heading,page.contentX(),page.y()+16,FishPondArt.INK,false);
        if(inside(mouseX,mouseY,page.contentX(),page.y()+12,hw,line+12))hoverText=title;
        FishPondArt.rule(g,page.contentX(),page.top()-10,page.contentWidth());
        g.enableScissor(page.contentX()-3,page.top(),page.contentX()+page.contentWidth()+1,page.bottom());
        g.pose().pushPose();g.pose().translate(page.contentX(),page.top()-scroll,0);
        drawBody(g);g.pose().popPose();g.disableScissor();
        int vh=page.bottom()-page.top(),max=Math.max(0,contentHeight-vh);
        if(max>0) {
            int thumb=Math.max(12,vh*vh/contentHeight),y=page.top()+(vh-thumb)*scroll/max;
            g.fill(page.x()+page.width()-10,page.top(),page.x()+page.width()-8,page.bottom(),0xFFD2CEB5);
            g.fill(page.x()+page.width()-11,y,page.x()+page.width()-7,y+thumb,0xFF658779);
        }
    }
    private void drawBody(GuiGraphics g) {
        if(hasPond) {
            FishPondArt.pond(g,0,0,page.pondWidth(),pondHeight);
            ItemStack fish=menu.getFishPreviewStack();
            if(menu.isFormed() && !fish.isEmpty()) {
                int ix=(page.pondWidth()-32)/2,iy=(pondHeight-32)/2-2;
                CommonGuiTextures.drawItem(g,fish,ix,iy,2);
                if(inside(mx,my,ix,iy,32,32))hoverItem=fish;
            }
            if(populationY>=0) {
                int x=page.columns()?0:page.pondWidth()+14,w=page.columns()?page.pondWidth():page.contentWidth()-x;
                int max=menu.getMaxPopulation(),fill=max<=0?0:(int)((long)w*Math.min(menu.getCurrentPopulation(),max)/max);
                g.fill(x,populationY,x+w,populationY+6,0xFFD2D2B6);g.fill(x,populationY,x+fill,populationY+6,0xFF6B9982);
                if(max>0&&max<=20)for(int i=1;i<max;i++)g.fill(x+i*w/max,populationY,x+i*w/max+1,populationY+6,0xFFEEE8CE);
            }
            if(requestTagY>=0)FishPondArt.icon(g,"tag",page.noteX(),requestTagY,1);
        }
        for(Text t:texts) {
            int y=t.y;
            for(FormattedCharSequence part:font.split(t.label,Math.max(1,t.width))) {g.drawString(font,part,t.x,y,t.color,false);y+=line+3;}
        }
        for(Icon icon:icons) {
            CommonGuiTextures.drawItem(g,icon.item,icon.x,icon.y,icon.scale);
            if(inside(mx,my,icon.x,icon.y,16*icon.scale,16*icon.scale))hoverItem=icon.item;
        }
        for(Requirement req:requirements) {
            int x=page.noteX(),w=page.noteWidth(),y=req.y;var r=req.layout;
            g.renderItem(req.icon,x,y+2);int ty=y;
            for(var part:font.split(req.label,r.labelWidth())) {g.drawString(font,part,x+24,ty,FishPondArt.INK,false);ty+=line+3;}
            String count=req.current+" / "+req.needed;
            g.drawString(font,count,r.stacked()?x+24:x+w-14-font.width(count),y+r.countY(),req.met?FishPondArt.GREEN:FishPondArt.RED,false);
            FishPondArt.sprite(g,req.met?"ok":"missing",x+w-8,y+r.countY()+Math.max(0,(line-8)/2),8,8);
            FishPondArt.rule(g,x+24,y+r.height()-2,w-24);
            if(inside(mx,my,x,y,20,20))hoverItem=req.icon;
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) { }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick) {
        hoverItem=ItemStack.EMPTY;hoverText=null;
        boolean within=mouseY>=page.top()&&mouseY<page.bottom();
        mx=within?mouseX-page.contentX():-10000;my=within?mouseY-page.top()+scroll:-10000;
        renderTransparentBackground(g);super.render(g,mouseX,mouseY,tick);
        if(!hoverItem.isEmpty())g.renderTooltip(minecraft.font,hoverItem,mouseX,mouseY);
        else if(hoverText!=null)g.renderTooltip(font,font.split(hoverText,Math.min(240,width-24)),mouseX,mouseY);
    }
    private Button button(int x,int y,int w,int h,Component label,boolean primary,boolean danger,boolean close,Runnable action) {
        Button b=new Button(x,y,w,h,label,unused->action.run(),supplier->supplier.get()) {
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float tick) {
                String skin=!active?"disabled":primary?(danger?"danger":"button")+(isHoveredOrFocused()?"_hover":""):"secondary"+(isHoveredOrFocused()?"_hover":"");
                FishPondArt.box(g,skin,getX(),getY(),getWidth(),getHeight());
                if(close)FishPondArt.sprite(g,"close",getX()+(getWidth()-8)/2,getY()+(getHeight()-8)/2,8,8);
                else {
                    var ll=font.split(getMessage(),getWidth()-14);int yy=getY()+(getHeight()-ll.size()*(line+2)+2)/2;
                    for(var part:ll) {g.drawString(font,part,getX()+(getWidth()-font.width(part))/2,yy,!active?FishPondArt.MUTED:primary?0xFFFFF4D8:danger?FishPondArt.RED:FishPondArt.INK,false);yy+=line+2;}
                }
                if(isFocused())g.renderOutline(getX()+3,getY()+3,getWidth()-6,getHeight()-6,primary?0xFFE7D6A9:FishPondArt.GREEN);
            }
            @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {manager.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(),1f,.25f));}
        };
        return addRenderableWidget(b);
    }
    private boolean inside(double x,double y,int rx,int ry,int w,int h) {return x>=rx&&x<rx+w&&y>=ry&&y<ry+h;}
    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        double horizontal = 0.0D; // PORT(1.20.1): no horizontal scroll before 1.20.2
        if(inside(x,y,page.contentX(),page.top(),page.contentWidth(),page.bottom()-page.top())&&vertical!=0) {
            scroll=FishPondLayout.clampScroll(scroll+(vertical<0?24:-24),contentHeight,page.bottom()-page.top());return true;
        }
        return super.mouseScrolled(x, y, vertical);
    }
    private void drag(double y) {
        int vh=page.bottom()-page.top(),max=Math.max(0,contentHeight-vh),thumb=Math.min(vh,Math.max(12,vh*vh/Math.max(1,contentHeight)));
        scroll=(int)Math.round(Math.max(0,Math.min(1,(y-page.top()-thumb/2.0)/Math.max(1,vh-thumb)))*max);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0&&contentHeight>page.bottom()-page.top()&&inside(x,y,page.x()+page.width()-13,page.top(),9,page.bottom()-page.top())) {dragging=true;drag(y);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {if(dragging&&button==0){drag(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button) {if(dragging){dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==256&&confirm!=Confirm.NONE){switchConfirm(Confirm.NONE);return true;}
        if(key==266||key==267){scroll=FishPondLayout.clampScroll(scroll+(key==267?60:-60),contentHeight,page.bottom()-page.top());return true;}
        return super.keyPressed(key,scan,mods);
    }
}
