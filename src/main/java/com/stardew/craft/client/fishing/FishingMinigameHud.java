package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.client.gui.common.GuiLayoutMath;
import com.stardew.craft.fishing.network.FishingMotionInputPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** The original simulator runs behind a non-modal, near-rod HUD. No screen, blur, or cursor capture. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class FishingMinigameHud {
    private static FishingMinigameScreen active;
    private static FishingMinigameScreen.Snapshot result;
    private static boolean success;
    private static long opened,finished,lastFeedback;
    private static long suspendedAt=-1;
    private static boolean wasControlled;
    private static long controlPulse;
    public static boolean active(){return active!=null;}
    public static void open(FishingMinigameScreen game) {
        cancel();var mc=Minecraft.getInstance();if(!FishingInteractionState.accepts(game.session()))return;
        active=game;game.init(mc,480,348);opened=-1;wasControlled=false;
    }
    public static void cancel(){if(active!=null)active.cancelWithoutResult();active=null;result=null;finished=0;lastFeedback=0;suspendedAt=-1;}
    public static void finish(FishingMinigameScreen game,boolean caught) {
        if(active!=game)return;result=game.snapshot();success=caught;finished=Util.getMillis();active=null;
        var mc=Minecraft.getInstance();mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);mc.options.keyJump.setDown(false);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        var mc=Minecraft.getInstance();FishingInteractionState.validateEquipment();
        if(active==null)return;
        if(!FishingInteractionState.accepts(active.session())){FishingInteractionState.cancel(true);cancel();return;}
        long now=Util.getMillis();
        // Menus and chat do not invalidate the rod. Suspend the obscured minigame and
        // release its input, so typing/clicking cannot reel and closing cannot catch up time.
        if(mc.screen!=null||mc.isPaused()) {
            active.suspendPresentation(now);
            if(suspendedAt<0) {
                suspendedAt=now;var s=active.snapshot();
                FishingPresentationClient.feedback(s.fishPosition()/548,s.progress(),0,false,s.inBar());
                PacketDistributor.sendToServer(new FishingMotionInputPayload(active.session(),s.fishPosition()/548,s.progress(),0,false,s.inBar()));
            }
            return;
        }
        resumePresentation(now);
        // Packet handling may precede the first visible frame. Do not spend the entry off-screen.
        if(opened<0)return;
        active.advance();if(active==null)return;var s=active.snapshot();
        if(s.inBar()&&!wasControlled)controlPulse=Util.getMillis();wasControlled=s.inBar();
        FishingPresentationClient.feedback(s.fishPosition()/548,s.progress(),s.fishVelocity(),s.held(),s.inBar());
        if(Util.getMillis()-lastFeedback>=100){lastFeedback=Util.getMillis();PacketDistributor.sendToServer(new FishingMotionInputPayload(active.session(),s.fishPosition()/548,s.progress(),s.fishVelocity(),s.held(),s.inBar()));}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(active!=null&&Minecraft.getInstance().screen==null&&!Minecraft.getInstance().isPaused())active.tick();}
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){if(active!=null&&Minecraft.getInstance().screen==null){event.setCanceled(true);event.setSwingHand(false);}}
    private static void resumePresentation(long now) {
        if(active==null||suspendedAt<0)return;
        if(opened>=0)opened+=now-suspendedAt;
        active.startPresentation(now);suspendedAt=-1;
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        if(result!=null&&Util.getMillis()-finished>(success?500:380))result=null;
        if(active==null&&result==null)return;
        resumePresentation(Util.getMillis());
        if(active!=null&&opened<0){opened=Util.getMillis();active.startPresentation(opened);}
        var window=mc.getWindow();var layout=GuiLayoutMath.viewport(window.getWidth(),window.getHeight(),StardewGuiViewport.renderScale(),480,348);
        var previous=StardewGuiViewport.enter(layout);var g=event.getGuiGraphics();g.pose().pushPose();
        try{g.pose().translate(layout.x(),layout.y(),0);g.pose().scale((float)layout.scale(),(float)layout.scale(),1);draw(g,active==null?result:active.snapshot(),layout.width(),layout.height());}
        finally{g.setColor(1,1,1,1);g.pose().popPose();StardewGuiViewport.restore(previous);}
    }
    private static ResourceLocation tex(String name){return FishingRigAssets.resource("textures/gui/fishing/native/"+name+".png");}
    public static float nativeScale(int width,int height) {
        // Reserve the whole 48x210 entry, including the waterline below the 24x164 bubble.
        // With the bubble centred, that canvas reaches 120 native pixels below screen centre.
        float preferred=height*.62f/164*StardewFonts.readingScale();
        return Math.max(0,Math.min(preferred,Math.min((height*.5f-12)/120,(width-24f)/78)));
    }
    private static float fade(float from,float to,float ms){return FishingPresentationClient.smooth((ms-from)/(to-from));}
    public static void draw(GuiGraphics g,FishingMinigameScreen.Snapshot s,int width,int height) {
        if(s==null)return;long now=Util.getMillis();float ms=finished==0?0:now-finished;boolean ending=active==null&&result!=null;
        float unit=nativeScale(width,height);float x=width*.445f-12*unit,y=(height-164*unit)*.5f;
        // Mirror the composition for a left-handed player; the track itself is never mirrored.
        var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)x=width*.555f-12*unit;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(unit,unit,1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        float water=1,bar=1,fish=1,lift=0,ring=0;
        if(ending){if(success){water=1-fade(70,285,ms);bar=1-fade(55,195,ms);fish=1-fade(330,500,ms);ring=fade(85,180,ms)*(1-fade(320,500,ms));lift=-2*fade(275,455,ms);}else{water=1-fade(20,230,ms);bar=1-fade(0,100,ms);fish=1-fade(50,155,ms);lift=Math.signum(s.fishVelocity())*5*fade(0,150,ms);}}
        int frame=(int)(((ending?finished:now)-opened)%8000/1000.0*30);frame=Math.floorMod(frame,240);
        float entryMs=now-opened;
        if(!ending&&entryMs<550){int f=Math.min(33,(int)(entryMs*.06));sprite(g,"entry_entry_container",-12,-8,48,210,(f%6)*48,(f/6)*210,48,210,288,1260,1);}
        else sprite(g,"bubble_loop",0,0,24,164,(frame%20)*24,(frame/20)*164,24,164,480,1968,water);
        float content=ending?1:fade(230,330,entryMs);bar*=content;fish*=content;water*=content;
        if(content>0){
            float bx=6+s.barShakeX()/4,by=3+(s.barPosition()+s.barShakeY())/4,h=s.barHeight()/4f;
            float blink=s.inBar()?1:Mth.clamp(.25f*((float)Math.sin(now/100.0)+2),0,1);
            sprite(g,"bar_top",bx,by,9,2,9,2,bar*blink);
            float middle=Math.max(0,h-4);int crop=Math.min(138,(int)Math.ceil(middle));
            if(crop>0)sprite(g,"bar_body",bx,by+2,9,middle,0,0,9,crop,9,138,bar*blink);
            sprite(g,"bar_bottom",bx,by+h-2,9,2,9,2,bar*blink);
            if(!ending&&s.inBar()) {
                g.enableScissor((int)Math.floor(bx),(int)Math.floor(by+2),(int)Math.ceil(bx+9),(int)Math.ceil(by+h-2));
                float yy=by-10+(now%4000)/4000f*(h+10);sprite(g,"bar_sheen",bx,yy,9,10,9,10,.7f);g.disableScissor();
                float pulse=1-fade(0,130,now-controlPulse);if(pulse>0)g.fill(6,(int)by,15,(int)by+1,((int)(100*pulse)<<24)|0xf2ffc5);
            }
            float fy=Mth.clamp(s.fishPosition()+ (ending&&!success?lift*4:0),0,548)/4+6+(ending&&success?lift:0)+s.fishShakeY()/4;
            float fx=10.5f+s.fishShakeX()/4;
            sprite(g,s.legendary()?"legend_fish":"fish",fx-5,fy-5,10,10,20,20,fish);
            // Progress is drawn from the exact simulator value; there is no smoothed extra tension meter.
            float progress=Mth.clamp(s.progress(),0,1);int bottom=149,top=bottom-Math.round(145*progress);
            g.fill(24,3,28,150,((int)(190*water)<<24)|0x163941);g.fill(25,top,27,bottom,((int)(255*water)<<24)|progressColor(progress));
            if(water>0 && s.hasTreasure()&&s.treasureScale()>0) {
                float tx=10.5f+s.treasureShakeX()/4,ty=6+(s.treasurePosition()+s.treasureShakeY())/4,ts=s.treasureScale();
                sprite(g,s.golden()?"golden_treasure":"treasure",tx-5*ts,ty-6*ts,10*ts,12*ts,20,24,water);
                if(s.treasureProgress()>0&&!s.treasureCaught()){g.fill(6,(int)(ty-8),16,(int)(ty-6),((int)(160*water)<<24)|0x23383b);g.fill(6,(int)(ty-8),6+Math.round(10*s.treasureProgress()),(int)(ty-6),((int)(255*water)<<24)|(s.golden()?0xffdf7f:0xcba355));}
            }
            if(!s.sonar().isEmpty()&&water>0){sprite(g,"sonar_panel",31,0,29,24,29,24,water);g.pose().pushPose();g.pose().translate(36,4,0);g.setColor(1,1,1,water);g.renderItem(s.sonar(),0,0);g.pose().popPose();g.setColor(1,1,1,1);}
            if(s.challengeFish()>=0&&water>0){sprite(g,"challenge_panel",-18,2,15,38,15,38,water);for(int i=0;i<3;i++)sprite(g,i<s.challengeFish()?"challenge_fish":"challenge_lost",-16,4+i*11,9.5f,9.5f,19,19,water);}
            if(ending&&success){
                sprite(g,"success_catch_ring",fx-8,fy-8,16,16,16,16,ring);
                for(int i=0;i<2;i++){float q=fade(25+i*30,210+i*30,ms);float yy=(i==0?7:151)+(fy-(i==0?7:151))*q;sprite(g,"success_water_fold",(i==0?2:19)-8,yy-8,16,16,16,16,(1-q)*.8f);}
                sprite(g,"success_last_drop",fx-8,fy-7+18*fade(190,450,ms),16,16,16,16,fade(170,220,ms)*(1-fade(300,480,ms)));
                if(s.perfect()&&ms<450){g.pose().pushPose();g.pose().scale(.5f,.5f,1);g.drawString(StardewFonts.small(),Component.translatable("stardewcraft.fishing.minigame.perfect"),-5,-20,0xffe7ac,false);g.pose().popPose();}
            } else if(ending) {
                for(int i=0;i<3;i++){float q=fade(40+i*35,300+i*30,ms);float dx=(i-1)*(3+q*2);sprite(g,i==1?"failure_drop_long":"failure_drop_round",fx+dx-8,fy-8+q*q*18,16,16,16,16,(1-q)*fade(20+i*30,70+i*30,ms));}
                for(int i=0;i<2;i++)sprite(g,"failure_loose_film",(i==0?1:21)-8,fy-8+12*fade(20+i*12,280,ms),16,16,16,16,fade(0,35,ms)*(1-fade(120,300,ms)));
            }
        }
        g.setColor(1,1,1,1);g.pose().popPose();
    }
    private static int progressColor(float p){int r=(int)Mth.lerp(p,194,102),g=(int)Mth.lerp(p,91,182),b=(int)Mth.lerp(p,76,92);return r<<16|g<<8|b;}
    private static void sprite(GuiGraphics g,String name,float x,float y,float w,float h,int tw,int th,float alpha){sprite(g,name,x,y,w,h,0,0,tw,th,tw,th,alpha);}
    private static void sprite(GuiGraphics g,String name,float x,float y,float w,float h,int u,int v,int sw,int sh,int tw,int th,float alpha){if(alpha<=0||w<=0||h<=0)return;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(w/sw,h/sh,1);g.setColor(1,1,1,Mth.clamp(alpha,0,1));g.blit(tex(name),0,0,u,v,sw,sh,tw,th);g.setColor(1,1,1,1);g.pose().popPose();}
}
