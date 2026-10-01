package com.stardew.craft.client.gui.common;

import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared source-textured menu action; keeps vanilla focus and keyboard activation. */
public final class StardewMenuButton extends Button {
    private BuildingUiIcons.Icon icon;
    private StardewMenuButton(int x,int y,int width,int height,Component message,OnPress press) {
        super(x,y,width,height,message,press,s->s.get());
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float tick) {
        var font=StardewFonts.small();
        CommonGuiTextures.drawTextureBox(g,getX(),getY(),getWidth(),getHeight(),1,true);
        if(isHoveredOrFocused() && active)CommonGuiTextures.drawOptionHighlightBox(g,getX()+3,getY()+3,getWidth()-6,getHeight()-6,1);
        var shown=icon==null?BuildingUiIcons.action(getMessage()):icon;
        int inset=shown==null?6:27;
        BuildingUiIcons.draw(g,shown,getX()+6,getY()+(getHeight()-16)/2);
        var lines=font.split(getMessage(),Math.max(1,getWidth()-inset-6));
        int count=Math.min(lines.size(),Math.max(1,(getHeight()-4)/StardewFonts.lineHeight(font)));
        int yy=getY()+(getHeight()-count*StardewFonts.lineHeight(font))/2;
        for(var line:lines.subList(0,count)){g.drawString(font,line,getX()+inset+(getWidth()-inset-6-font.width(line))/2,yy,active?0xFF5C2B00:0xFF816A50,false);yy+=StardewFonts.lineHeight(font);}
        if(lines.size()>count && isHoveredOrFocused()){var screen=net.minecraft.client.Minecraft.getInstance().screen;if(screen!=null)screen.setTooltipForNextRenderPass(net.minecraft.client.gui.components.Tooltip.splitTooltip(net.minecraft.client.Minecraft.getInstance(), getMessage()));}
    }
    public static Builder builder(Component label,OnPress press) {return new Builder(label,press);}
    public static final class Builder extends Button.Builder {
        private final Component label;private final OnPress press;
        private int x,y,width=150,height=20;private BuildingUiIcons.Icon icon;
        private Builder(Component label,OnPress press){super(label,press);this.label=label;this.press=press;}
        public Builder icon(BuildingUiIcons.Icon icon){this.icon=icon;return this;}
        @Override public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;width=w;height=h;return this;}
        @Override public Button build(){var button=new StardewMenuButton(x,y,width,height,label,press);button.icon=icon;return button;}
    }
}
