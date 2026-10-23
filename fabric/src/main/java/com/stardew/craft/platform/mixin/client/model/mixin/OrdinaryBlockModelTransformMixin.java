package com.stardew.craft.platform.mixin.client.model.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.math.Transformation;
import com.stardew.craft.platform.fabric.client.model.OrdinaryModelTransforms;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only native element expressions run here; PL cancels the method for custom geometry. */
@Mixin(BlockModel.class)
abstract class OrdinaryBlockModelTransformMixin {
    private static final String BAKE = "bake(Lnet/minecraft/client/resources/model/ModelBaker;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Z)Lnet/minecraft/client/resources/model/BakedModel;";

    @WrapOperation(method = BAKE, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/BlockModel;bakeFace(Lnet/minecraft/client/renderer/block/model/BlockElement;Lnet/minecraft/client/renderer/block/model/BlockElementFace;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/core/Direction;Lnet/minecraft/client/resources/model/ModelState;)Lnet/minecraft/client/renderer/block/model/BakedQuad;"), require = 2)
    private BakedQuad stardewcraft$rootTransformFace(BlockElement element, BlockElementFace face,
            TextureAtlasSprite sprite, Direction direction, ModelState state, Operation<BakedQuad> original) {
        return original.call(element, face, sprite, direction,
                OrdinaryModelTransforms.forBake((BlockModel) (Object) this, state));
    }

    @WrapOperation(method = BAKE, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelState;getRotation()Lcom/mojang/math/Transformation;"), require = 1)
    private Transformation stardewcraft$rootTransformCull(ModelState state, Operation<Transformation> original) {
        ModelState combined = OrdinaryModelTransforms.forBake((BlockModel) (Object) this, state);
        return combined == state ? original.call(state) : combined.getRotation();
    }
}
