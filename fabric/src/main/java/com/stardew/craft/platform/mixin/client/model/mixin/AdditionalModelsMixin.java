package com.stardew.craft.platform.mixin.client.model.mixin;


import com.stardew.craft.platform.fabric.client.model.FabricModelEvents;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBakery.class)
abstract class AdditionalModelsMixin {
    @Shadow abstract UnbakedModel getModel(ResourceLocation id);

    @Shadow
    private void registerModelAndLoadDependencies(ModelResourceLocation id, UnbakedModel model) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;values()Ljava/util/Collection;"), require = 1)
    private void stardewcraft$additionalBeforeResolvingParents(CallbackInfo ci) {
        // FAPI's resource-model variant is fabric_resource; existing renderers need standalone.
        // Use the native registration path so FAPI/PL on-load, dependencies and parent resolution all remain active.
        FabricModelEvents.onRegisterAdditional(id -> registerModelAndLoadDependencies(id, getModel(id.id())));
    }
}
