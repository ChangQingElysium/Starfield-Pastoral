package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.client.render.SpatialBlockModelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep oversized native map-prop faces inside the AO interpolator's block-local domain. */
@Mixin(ModelBlockRenderer.class)
public abstract class PortMapDecorSpatialLightingMixin {
    @Inject(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1, expect = 1, allow = 1)
    private void stardewcraft$localMapPropLighting(BlockAndTintGetter level, BakedModel model, BlockState state,
            BlockPos pos, PoseStack pose, VertexConsumer consumer, boolean checkSides, RandomSource random,
            long seed, int overlay, ModelData modelData, RenderType renderType, CallbackInfo ci) {
        if (state.getBlock() instanceof MapDecorStaticBlock
                && SpatialBlockModelRenderer.renderMapDecor((ModelBlockRenderer) (Object) this,
                        level, model, state, pos, pose, consumer, checkSides, random, seed, overlay, modelData, renderType)) {
            ci.cancel();
        }
    }
}
