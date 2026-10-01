package com.stardew.craft.client.mining;

import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;

/** Soft 3D counterpart to the source swarm overlay; local to the receiving player's floor. */
@EventBusSubscriber(modid="stardewcraft",value=Dist.CLIENT)
public final class ClientMineFog {
    private static int floor=-1,remaining;
    private static float alpha;
    public static void receive(int value,int ticks) {floor=value;remaining=ticks;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {floor=-1;remaining=0;alpha=0;}
    @SubscribeEvent public static void tick(com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.level.dimension()!=ModMiningDimensions.STARDEW_MINING
                || OrdinaryMineRuntime.floorAt(mc.player.blockPosition())!=floor) {alpha=0;return;}
        if(mc.isPaused())return;
        float target=Math.min(1,remaining/100f);alpha+=Math.clamp(target-alpha,-.05f,.05f);
        if(remaining>0)remaining--;
    }
    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor event) {
        if(alpha<=0 || event.getCamera().getFluidInCamera()!=net.minecraft.world.level.material.FogType.NONE)return;
        float[] c=floor<30?new float[]{.25f,.42f,.24f}:floor<40?new float[]{.5f,.46f,.26f}
                :floor<80?new float[]{.18f,.28f,.6f}:new float[]{.45f,.16f,.15f};
        float mix=alpha*.55f;
        event.setRed(event.getRed()*(1-mix)+c[0]*mix);
        event.setGreen(event.getGreen()*(1-mix)+c[1]*mix);
        event.setBlue(event.getBlue()*(1-mix)+c[2]*mix);
    }
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        if(alpha<=0 || event.getCamera().getFluidInCamera()!=net.minecraft.world.level.material.FogType.NONE)return;
        event.setNearPlaneDistance(event.getNearPlaneDistance()*(1-alpha));
        event.setFarPlaneDistance(event.getFarPlaneDistance()*(1-alpha)+Math.min(22,event.getFarPlaneDistance())*alpha);
        event.setCanceled(true);
    }
}
