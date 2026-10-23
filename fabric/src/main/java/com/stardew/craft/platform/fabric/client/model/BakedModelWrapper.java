package com.stardew.craft.platform.fabric.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.util.TriState;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
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

public class BakedModelWrapper<T extends BakedModel> extends ForwardingBakedModel implements ExtendedBakedModel {
    protected final T originalModel;

    public BakedModelWrapper(T original) { super(Objects.requireNonNull(original, "original")); originalModel = original; }

    @Override public boolean isVanillaAdapter() { return !(this instanceof IDynamicBakedModel) && originalModel.isVanillaAdapter(); }
    @Override public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
            Supplier<RandomSource> random, RenderContext context) {
        if (this instanceof IDynamicBakedModel || OrdinaryModelRenderTypes.hasHintedGeometry(originalModel))
            DynamicModelEmitter.emitBlock(this, view, state, pos, random, context);
        else originalModel.emitBlockQuads(view, state, pos, random, context);
    }
    @Override public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
        if (this instanceof IDynamicBakedModel || OrdinaryModelRenderTypes.hasHintedGeometry(originalModel))
            DynamicModelEmitter.emitItem(this, stack, random, context);
        else originalModel.emitItemQuads(stack, random, context);
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        return BakedModels.getQuads(originalModel, state, side, random, data, layer);
    }
    @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData data) {
        return BakedModels.getModelData(originalModel, view, pos, state, data);
    }
    @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return BakedModels.getParticleIcon(originalModel, data); }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return BakedModels.getRenderTypes(originalModel, state, random, data);
    }
    @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean direct) { return List.of(this); }
    @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean direct) { return BakedModels.getRenderTypes(originalModel, stack, direct); }
    @Override public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType layer) {
        return BakedModels.useAmbientOcclusion(originalModel, state, data, layer);
    }
    @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        BakedModels.applyTransform(originalModel, context, pose, leftHand);
        return this;
    }
}
