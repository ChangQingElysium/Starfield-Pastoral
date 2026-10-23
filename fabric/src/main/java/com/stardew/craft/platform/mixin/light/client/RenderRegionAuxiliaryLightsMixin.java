package com.stardew.craft.platform.mixin.light.client;


import com.stardew.craft.platform.fabric.light.AuxiliaryLightManager;
import com.stardew.craft.platform.fabric.light.AuxiliaryLightView;
import com.stardew.craft.platform.fabric.light.AuxiliaryLights;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderChunkRegion.class)
abstract class RenderRegionAuxiliaryLightsMixin implements AuxiliaryLightView {
    @Shadow @Final protected Level level;
    @Shadow @Final private int minChunkX;
    @Shadow @Final private int minChunkZ;
    @Unique private AuxiliaryLightManager stardewcraft$lightSnapshot;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void stardewcraft$captureLightSnapshot(CallbackInfo ci) {
        stardewcraft$lightSnapshot = AuxiliaryLights.snapshotRegion(level, minChunkX, minChunkZ, RenderChunkRegion.SIZE);
    }

    @Override public AuxiliaryLightManager stardewcraft$auxiliaryLights(BlockPos pos) { return stardewcraft$lightSnapshot; }
}
