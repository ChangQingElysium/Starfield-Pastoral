package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.templates.client.ModelDataAmbientOcclusion;
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
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PORT(1.20.1): NeoForge 1.21 passes the block's ModelData to {@code useAmbientOcclusion}; Forge 1.20.1 does not.
 * Template models pick their ambient-occlusion behaviour from the material stored in that data.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class TemplateModelAmbientOcclusionMixin {
    @Redirect(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;useAmbientOcclusion(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/RenderType;)Z"),
            // Both the ModelData tesselateBlock overload and useAmbientOcclusion(BlockState, RenderType) are Forge-added.
            remap = false)
    private boolean stardewcraft$templateAmbientOcclusion(BakedModel model, BlockState state, RenderType renderType,
                                                          BlockAndTintGetter level, BakedModel tesselated,
                                                          BlockState tesselatedState, BlockPos pos, PoseStack pose,
                                                          VertexConsumer consumer, boolean checkSides,
                                                          RandomSource random, long seed, int overlay,
                                                          ModelData modelData, RenderType tesselatedRenderType) {
        return model instanceof ModelDataAmbientOcclusion template
                ? template.stardewcraft$useAmbientOcclusion(state, modelData, renderType)
                : model.useAmbientOcclusion(state, renderType);
    }
}
