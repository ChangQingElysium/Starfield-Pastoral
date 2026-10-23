package com.stardew.craft.platform.fabric.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.util.TriState;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import java.util.List;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit project model features; vanilla callers go through BakedModels helpers. */
public interface ExtendedBakedModel extends BakedModel {
    default List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        return getQuads(state, side, random);
    }

    default ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData data) { return data; }
    default TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
    default ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(ItemBlockRenderTypes.getChunkRenderType(state));
    }
    default List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    default List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(ItemBlockRenderTypes.getRenderType(stack, fabulous));
    }
    default TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType layer) {
        return useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE;
    }
    default BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        getTransforms().getTransform(context).apply(leftHand, pose);
        return this;
    }
}
