package com.stardew.craft.platform.fabric.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.platform.mixin.client.model.mixin.BlockRenderDispatcherColorsAccessor;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit extended rendering calls retain native tessellation and vertex emission. */
@Environment(EnvType.CLIENT)
public final class BlockModelRendering {
    private BlockModelRendering() {}

    public static void renderModel(ModelBlockRenderer renderer, PoseStack.Pose pose, VertexConsumer vertices,
            BlockState state, BakedModel model, float red, float green, float blue, int light, int overlay,
            ModelData data, RenderType quadLayer) {
        // Native renderModel resets seed 42 for all six cull buckets and the unculled bucket,
        // clamps tinted colors and writes the original packed light, overlay, UVs and vertex normals.
        renderer.renderModel(pose, vertices, state, new BoundModel(model, data, quadLayer), red, green, blue, light, overlay);
    }

    public static void renderSingleBlock(BlockRenderDispatcher dispatcher, BlockState state, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        renderSingleBlock(dispatcher, state, pose, buffers, light, overlay, ModelData.EMPTY, null);
    }

    public static void renderSingleBlock(BlockRenderDispatcher dispatcher, BlockState state, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, ModelData data, RenderType bufferOverride) {
        Objects.requireNonNull(data, "model data");
        if (state.getRenderShape() != RenderShape.MODEL) {
            // Includes INVISIBLE and ENTITYBLOCK_ANIMATED. Keep vanilla/FAPI's actual builtin item renderer path.
            dispatcher.renderSingleBlock(state, pose, buffers, light, overlay);
            return;
        }
        if (!((Object) dispatcher instanceof BlockRenderDispatcherColorsAccessor access)) {
            throw new IllegalStateException("Block renderer color accessor mixin is required");
        }
        BakedModel model = dispatcher.getBlockModel(state);
        int tint = access.stardewcraft$blockColors().getColor(state, null, null, 0);
        float red = (tint >> 16 & 255) / 255F;
        float green = (tint >> 8 & 255) / 255F;
        float blue = (tint & 255) / 255F;
        for (RenderType quadLayer : BakedModels.getRenderTypes(model, state, RandomSource.create(42), data)) {
            RenderType bufferLayer = bufferOverride != null ? bufferOverride : RenderTypeHelper.getEntityRenderType(quadLayer, false);
            renderModel(dispatcher.getModelRenderer(), pose.last(), buffers.getBuffer(bufferLayer), state, model,
                    red, green, blue, light, overlay, data, quadLayer);
        }
    }

    public static void tesselateBlock(ModelBlockRenderer renderer, BlockAndTintGetter view, BakedModel model,
            BlockState state, BlockPos pos, PoseStack pose, VertexConsumer vertices, boolean checkSides,
            RandomSource random, long seed, int overlay, ModelData data, RenderType quadLayer) {
        // Compatible FRAPI renderers intercept native tesselateBlock for this non-vanilla adapter.
        // They retain world light, AO, neighbor-face culling, state offset, pose and overlay.
        renderer.tesselateBlock(view, new WorldBoundModel(model, data, quadLayer), state, pos, pose, vertices,
                checkSides, random, seed, overlay);
    }

    private static class BoundModel extends ForwardingBakedModel {
        final ModelData data;
        final RenderType layer;

        BoundModel(BakedModel original, ModelData data, RenderType layer) {
            super(Objects.requireNonNull(original, "model"));
            this.data = Objects.requireNonNull(data, "model data");
            this.layer = layer;
        }

        @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
            return BakedModels.getQuads(wrapped, state, face, random, data, layer);
        }
    }

    private static final class WorldBoundModel extends BoundModel {
        WorldBoundModel(BakedModel original, ModelData data, RenderType layer) { super(original, data, layer); }

        @Override public boolean isVanillaAdapter() { return false; }

        @Override public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                Supplier<RandomSource> random, RenderContext context) {
            if (!(wrapped instanceof ExtendedBakedModel) && !wrapped.isVanillaAdapter()) {
                // A native FRAPI model has no extended ModelData API. Keep its mesh/material producer
                // rather than flattening per-face AO, diffuse shade and emissive metadata to BakedQuad.
                wrapped.emitBlockQuads(view, state, pos, random, context);
            } else DynamicModelEmitter.emitExplicitBlock(wrapped, view, pos, state, data, layer, random, context);
        }
    }
}
