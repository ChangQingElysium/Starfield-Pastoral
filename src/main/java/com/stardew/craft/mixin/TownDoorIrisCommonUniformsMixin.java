package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris normally samples the player entity, which stays outside while the portal camera moves. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class TownDoorIrisCommonUniformsMixin {
    @Inject(method = "getEyeBrightness", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stardewcraft$destinationLight(CallbackInfoReturnable<Vector2i> cir) {
        if (!TownDoorRenderContext.isRendering()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var position = BlockPos.containing(TownDoorRenderContext.cameraPosition());
        cir.setReturnValue(new Vector2i(level.getBrightness(LightLayer.BLOCK, position) * 16,
                level.getBrightness(LightLayer.SKY, position) * 16));
    }

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stardewcraft$destinationSky(CallbackInfoReturnable<Vector3d> cir) {
        if (!TownDoorRenderContext.isRendering()) return;
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        var color = client.level.getSkyColor(TownDoorRenderContext.cameraPosition(),
                com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false));
        cir.setReturnValue(new Vector3d(color.x, color.y, color.z));
    }
}
