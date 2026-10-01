package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.client.gui.common.GuiLayoutMath;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** Catch items are rendered by the hand rig. Treasure follows stowing and survives packet ordering. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class FishingCatchVisuals {
    public record CatchPresentation(ItemStack stack,boolean fish,Vec3 origin) {}
    private static CatchPresentation presentation;
    private static long chest=-1,treasureStarted;
    private static boolean golden,handFinished,opened,openSound;
    public static CatchPresentation currentCatch(){return presentation;}
    public static void start(ItemStack stack,boolean fish,Vec3 origin){presentation=new CatchPresentation(stack.copy(),fish,origin);handFinished=false;opened=false;treasureStarted=0;openSound=false;
        var player=Minecraft.getInstance().player;
        var state=player==null?null:FishingPresentationClient.state(player);
        if(state!=null&&state.phase==com.stardew.craft.fishing.FishingPresentationPhase.CATCH&&state.elapsed()>=state.hideTime())finishHandPresentation();
    }
    public static void finishHandPresentation(){handFinished=true;beginTreasure();}
    public static void setPendingTreasure(long chestId,boolean isGolden){chest=chestId;golden=isGolden;beginTreasure();}
    private static void beginTreasure(){if(handFinished&&chest>=0&&treasureStarted==0&&FishingInteractionState.valid())treasureStarted=Util.getMillis();}
    public static boolean expectsTreasureMenu(){return opened&&FishingInteractionState.valid();}
    public static void cancel(){presentation=null;chest=-1;treasureStarted=0;golden=handFinished=opened=openSound=false;}
    public static void startHookedPopup(){FishingBiteVisuals.clear();}
    public static void startFail(){presentation=null;chest=-1;treasureStarted=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(treasureStarted==0||opened)return;var mc=Minecraft.getInstance();if(!FishingInteractionState.valid()){cancel();return;}
        long elapsed=Util.getMillis()-treasureStarted;
        if(elapsed>=500&&!openSound){openSound=true;if(mc.player!=null)mc.player.playSound(ModSounds.OPEN_CHEST.get(),.7f,1);}
        // Original closed-chest beat, then four 200 ms opening frames. Rendering/F1 never gates delivery.
        if(elapsed>=1300){opened=true;PacketDistributor.sendToServer(new com.stardew.craft.network.payload.OpenTreasureChestRequestPayload(chest));chest=-1;}
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(treasureStarted==0||opened||mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        var w=mc.getWindow();var layout=GuiLayoutMath.viewport(w.getWidth(),w.getHeight(),StardewGuiViewport.renderScale());
        var previous=StardewGuiViewport.enter(layout);var g=event.getGuiGraphics();g.pose().pushPose();
        try {
            g.pose().translate(layout.x(),layout.y(),0);g.pose().scale((float)layout.scale(),(float)layout.scale(),1);
            long elapsed=Util.getMillis()-treasureStarted;int frame=elapsed<500?0:Math.min(3,(int)((elapsed-500)/200));
            float lift=FishingPresentationClient.smooth(elapsed/180f)*4;
            g.pose().translate(layout.width()*.45-24,layout.height()*.36-lift,0);g.pose().scale(1.5f,1.5f,1);
            g.blit(FishingRigAssets.resource("textures/gui/fishing/native/"+(golden?"golden_treasure_open":"treasure_open")+".png"),0,0,frame*32,0,32,32,128,32);
        } finally{g.pose().popPose();StardewGuiViewport.restore(previous);}
    }
}
