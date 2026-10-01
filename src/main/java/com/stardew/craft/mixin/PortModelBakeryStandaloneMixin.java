package com.stardew.craft.mixin;

import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): NeoForge 21.1 registers extra models through {@code ModelEvent.RegisterAdditional} as
 * {@code ModelResourceLocation(id, "standalone")} and loads them as plain model files. Forge 1.20.1 would treat any
 * non-"inventory" {@link ModelResourceLocation} as a blockstate variant and fall back to the missing model. This loads
 * "standalone" locations from {@code <namespace>:models/<path>.json} and keeps them keyed by the standalone location,
 * so {@code ModifyBakingResult#getModels()} and {@code ModelManager#getModel} lookups match NeoForge.
 */
@Mixin(ModelBakery.class)
public abstract class PortModelBakeryStandaloneMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow
    protected abstract BlockModel loadBlockModel(ResourceLocation location) throws java.io.IOException;

    @Shadow
    private void cacheAndQueueDependencies(ResourceLocation location, UnbakedModel model) {
        throw new AssertionError();
    }

    @Inject(method = "loadModel", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$loadStandalone(ResourceLocation location, CallbackInfo ci) throws Exception {
        if (location instanceof ModelResourceLocation model && "standalone".equals(model.getVariant())) {
            ResourceLocation file = new ResourceLocation(location.getNamespace(), location.getPath());
            BlockModel blockModel = this.loadBlockModel(file);
            this.cacheAndQueueDependencies(location, blockModel);
            this.unbakedCache.put(file, blockModel);
            ci.cancel();
        }
    }
}
