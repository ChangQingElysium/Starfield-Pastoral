package com.stardew.craft.platform.fabric.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.modeldata.ModelDataProvider;
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
import net.minecraft.world.level.block.entity.BlockEntity;

/** Dispatch to real extended features or the corresponding vanilla behavior. No native five-argument API is assumed. */
public final class BakedModels {
    private BakedModels() {}

    public static net.minecraft.client.resources.model.ModelResourceLocation standalone(net.minecraft.resources.ResourceLocation id) {
        return new net.minecraft.client.resources.model.ModelResourceLocation(id, "standalone");
    }

    public static List<BakedQuad> getQuads(BakedModel model, BlockState state, Direction side, RandomSource random,
            ModelData data, RenderType layer) {
        if (model instanceof ExtendedBakedModel extended) return extended.getQuads(state, side, random, data, layer);
        // Neo's default extended quad query delegates directly. The caller may render a standalone
        // model with an unrelated blockstate or an entity buffer, so filtering its layer would erase it.
        return model.getQuads(state, side, random);
    }
    public static ModelData getModelData(BlockEntity blockEntity) {
        return blockEntity instanceof ModelDataProvider provider
                ? java.util.Objects.requireNonNull(provider.getModelData(), "block entity model data") : ModelData.EMPTY;
    }
    public static ModelData getModelData(BakedModel model, BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData data) {
        return model instanceof ExtendedBakedModel extended ? extended.getModelData(view, pos, state, data) : data;
    }
    public static TextureAtlasSprite getParticleIcon(BakedModel model, ModelData data) {
        return model instanceof ExtendedBakedModel extended ? extended.getParticleIcon(data) : model.getParticleIcon();
    }
    public static ChunkRenderTypeSet getRenderTypes(BakedModel model, BlockState state, RandomSource random, ModelData data) {
        return model instanceof ExtendedBakedModel extended ? extended.getRenderTypes(state, random, data)
                : ChunkRenderTypeSet.of(ItemBlockRenderTypes.getChunkRenderType(state));
    }
    public static List<BakedModel> getRenderPasses(BakedModel model, ItemStack stack, boolean fabulous) {
        return model instanceof ExtendedBakedModel extended ? extended.getRenderPasses(stack, fabulous) : List.of(model);
    }
    public static List<RenderType> getRenderTypes(BakedModel model, ItemStack stack, boolean fabulous) {
        return model instanceof ExtendedBakedModel extended ? extended.getRenderTypes(stack, fabulous)
                : List.of(ItemBlockRenderTypes.getRenderType(stack, fabulous));
    }
    public static TriState useAmbientOcclusion(BakedModel model, BlockState state, ModelData data, RenderType layer) {
        return model instanceof ExtendedBakedModel extended ? extended.useAmbientOcclusion(state, data, layer)
                : model.useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE;
    }
    public static BakedModel applyTransform(BakedModel model, ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        if (model instanceof ExtendedBakedModel extended) return extended.applyTransform(context, pose, leftHand);
        model.getTransforms().getTransform(context).apply(leftHand, pose);
        return model;
    }
}
