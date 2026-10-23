package com.stardew.craft.platform.mixin.client.model.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.client.model.OrdinaryModelRenderTypes;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockModel.class)
abstract class OrdinaryBlockModelRenderTypeMixin {
    @ModifyReturnValue(method = "bake(Lnet/minecraft/client/resources/model/ModelBaker;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Z)Lnet/minecraft/client/resources/model/BakedModel;",
            at = @At("RETURN"), require = 1)
    private BakedModel stardewcraft$ordinaryRenderType(BakedModel model) {
        return OrdinaryModelRenderTypes.apply((BlockModel) (Object) this, model);
    }
}
