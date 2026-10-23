package com.stardew.craft.platform.mixin.client.model.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.client.model.FabricModelEvents;
import java.util.Map;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelManager.class)
abstract class ModelBakingEventsMixin {
    @Shadow private Map<ModelResourceLocation, BakedModel> bakedRegistry;

    @Inject(method = "loadModels", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/ModelBakery;getBakedTopLevelModels()Ljava/util/Map;"), require = 1)
    private void stardewcraft$modifyBeforeBlockstateCache(ProfilerFiller profiler,
            Map<ResourceLocation, AtlasSet.StitchResult> atlases, ModelBakery bakery,
            CallbackInfoReturnable<?> cir) {
        FabricModelEvents.onModifyBakingResult(bakery.getBakedTopLevelModels(), atlases, bakery);
    }

    @Inject(method = "apply", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"), require = 1)
    private void stardewcraft$completedAfterManagerAdoptsModels(CallbackInfo ci, @Local ModelBakery bakery) {
        // This is Neo's onModelBake boundary: uploaded atlases and all manager fields are new;
        // the subsequent native replaceCache must still run normally.
        FabricModelEvents.onBakingCompleted((ModelManager) (Object) this, bakedRegistry, bakery);
    }
}
