package com.stardew.craft.client.render;

import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.seat.AnimatedDecorSeat;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderPlayerEvent;

/** Vanilla passenger interpolation lags the freely swinging model by a tick. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class AnimatedDecorRiderRenderer {
    private AnimatedDecorRiderRenderer() {}
    @SubscribeEvent public static void before(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity().getVehicle() instanceof AnimatedDecorSeat seat)) return;
        var player = event.getEntity(); float partial = event.getPartialTick();
        Vec3 drawn = new Vec3(net.minecraft.util.Mth.lerp(partial, player.xOld, player.getX()),
                net.minecraft.util.Mth.lerp(partial, player.yOld, player.getY()),
                net.minecraft.util.Mth.lerp(partial, player.zOld, player.getZ()));
        Vec3 correction = seat.riderFeet(partial).subtract(drawn);
        var pose = event.getPoseStack(); pose.pushPose();
        pose.translate(correction.x, correction.y + AnimatedDecorSeat.HIPS_FROM_FEET, correction.z);
        int yaw = switch (seat.facing()) { case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0; };
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees((float)seat.riderAngle(partial)));
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, -AnimatedDecorSeat.HIPS_FROM_FEET, 0);
    }
    @SubscribeEvent public static void after(RenderPlayerEvent.Post event) {
        if (event.getEntity().getVehicle() instanceof AnimatedDecorSeat) event.getPoseStack().popPose();
    }
}
