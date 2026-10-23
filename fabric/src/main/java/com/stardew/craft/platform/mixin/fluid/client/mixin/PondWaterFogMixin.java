package com.stardew.craft.platform.mixin.fluid.client.mixin;


import com.stardew.craft.platform.fabric.fluid.FishPondWaterFluid;
import com.stardew.craft.platform.fabric.fluid.PondWaterTint;
import com.stardew.craft.platform.fabric.fluid.client.FishPondFluidRendering;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class PondWaterFogMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @ModifyArgs(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void stardewcraft$usePondFogColor(Args args, Camera camera, float partialTick,
            ClientLevel level, int renderDistance, float darkenWorldAmount) {
        if (camera.getFluidInCamera() != FogType.WATER
                || !(level.getFluidState(camera.getBlockPosition()).getType() instanceof FishPondWaterFluid)) {
            return;
        }
        var color = PondWaterTint.fogColor(FishPondFluidRendering.color(level, camera.getBlockPosition()));
        fogRed = color.x();
        fogGreen = color.y();
        fogBlue = color.z();
        args.set(0, fogRed);
        args.set(1, fogGreen);
        args.set(2, fogBlue);
    }
}
