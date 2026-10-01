package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.client.gui.common.StardewGuiContentSize;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.fishing.BobberStyles;
import com.stardew.craft.fishing.network.BobberMenuPayload;
import com.stardew.craft.fishing.network.BobberSelectPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

public final class BobberStyleScreen extends Screen implements StardewGuiContentSize {
    private static final int CELL=36,COLUMNS=8,PANEL_W=328,PANEL_H=266;
    private static final ResourceLocation SLOT=texture("slot"),HOVER=texture("hover"),LOCK=texture("locked"),RANDOM=texture("random"),PANEL=texture("panel"),CLOSE=texture("close");
    private static final ResourceLocation[] ICONS=java.util.stream.IntStream.range(0,BobberStyles.COUNT).mapToObj(i->texture("styles/"+i)).toArray(ResourceLocation[]::new);
    private final UUID token;
    private int selected,fishSpecies,x0,y0;
    public BobberStyleScreen(BobberMenuPayload p){super(Component.translatable("stardewcraft.bobber.title"));token=p.token();selected=p.selected();fishSpecies=p.fishSpecies();}
    private static ResourceLocation texture(String name){return new ResourceLocation("stardewcraft","textures/gui/bobbers/"+name+".png");}
    @Override public int minimumCanvasWidth(){return 380;}
    @Override public int minimumCanvasHeight(){return 316;}
    @Override public boolean isPauseScreen(){return false;}
    public void accept(BobberMenuPayload p){if(token.equals(p.token())){selected=p.selected();fishSpecies=p.fishSpecies();for(var child:children())if(child instanceof StyleButton button)button.refreshTooltip();}}
    private boolean canSelect(int style){return BobberStyles.canSelect(style,fishSpecies);}
    @Override protected void init(){
        x0=(width-PANEL_W)/2;y0=(height-PANEL_H)/2;
        for(int i=0;i<40;i++)addRenderableWidget(new StyleButton(x0+20+(i%COLUMNS)*CELL,y0+42+(i/COLUMNS)*CELL,i==39?BobberStyles.RANDOM:i));
        addRenderableWidget(new AbstractButton(x0+PANEL_W-34,y0+8,24,24,Component.translatable("gui.close")){
            @Override public void onPress(){onClose();}
            @Override public void playDownSound(SoundManager sounds){}
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){g.blit(CLOSE,getX(),getY(),24,24,0,0,12,12,12,12);if(isHoveredOrFocused())g.renderOutline(getX(),getY(),24,24,0xffe7a348);}
            @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
        });
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g,mx,my,partial);StardewGuiUtil.drawTextureBox(g,PANEL,18,18,0,0,18,18,x0,y0,PANEL_W,PANEL_H,1,true);
        g.drawCenteredString(font,title,width/2,y0+16,0xff512e20);
        // Screen.render would blur the already-drawn panel a second time.
        for(var widget:renderables)widget.render(g,mx,my,partial);
        Component count=Component.translatable("stardewcraft.bobber.unlocked",BobberStyles.lastUnlocked(fishSpecies)+1,BobberStyles.COUNT);
        g.drawCenteredString(font,count,width/2,y0+PANEL_H-18-StardewFonts.lineHeight(font),0xff654732);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BIG_DESELECT.get(),1f,.25f));super.onClose();}
    @Override public void removed(){if(minecraft!=null&&minecraft.getConnection()!=null)PacketDistributor.sendToServer(new BobberSelectPayload(token,-1));super.removed();}
    private final class StyleButton extends AbstractButton {
        private final int style;
        StyleButton(int x,int y,int style){
            super(x,y,32,32,style==BobberStyles.RANDOM?Component.translatable("stardewcraft.bobber.random"):Component.translatable("stardewcraft.bobber.style",style+1));this.style=style;
            refreshTooltip();
        }
        void refreshTooltip(){setTooltip(Tooltip.create(canSelect(style)?getMessage():Component.translatable("stardewcraft.bobber.locked",style*2)));}
        @Override public void onPress(){if(canSelect(style)&&style!=selected)PacketDistributor.sendToServer(new BobberSelectPayload(token,style));}
        @Override public void playDownSound(SoundManager sounds){
            if(!canSelect(style))sounds.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(),1f,.25f));
            else if(style!=selected)sounds.play(SimpleSoundInstance.forUI(ModSounds.BUTTON1.get(),1f,.25f));
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            if(style==selected)g.fill(getX()-1,getY()-2,getX()+33,getY()+34,0xffff0000);
            g.blit(style==selected||isHoveredOrFocused()?HOVER:SLOT,getX(),getY(),32,32,0,0,16,16,16,16);
            if(!canSelect(style))g.blit(LOCK,getX(),getY(),32,32,0,0,16,16,16,16);
            else if(style==BobberStyles.RANDOM)g.blit(RANDOM,getX(),getY(),32,32,0,0,16,16,16,16);
            else g.blit(ICONS[style],getX(),getY(),32,32,0,0,16,16,16,16);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
}
