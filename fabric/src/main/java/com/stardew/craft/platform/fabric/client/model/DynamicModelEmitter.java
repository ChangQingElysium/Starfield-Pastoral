package com.stardew.craft.platform.fabric.client.model;

import com.stardew.craft.platform.fabric.util.TriState;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/** FRAPI owns emission/culling; this bridge supplies every extended layer and face exactly once. */
public final class DynamicModelEmitter {
    private DynamicModelEmitter() {}

    public static ModelData attachment(BlockAndTintGetter view, BlockPos pos) {
        if (!((Object) view instanceof FabricBlockView fabric)) {
            throw new IllegalStateException("Fabric block-view interface is required for thread-safe model data");
        }
        Object data = fabric.getBlockEntityRenderData(pos);
        return data instanceof ModelData modelData ? modelData : ModelData.EMPTY;
    }

    public static void emitBlock(ExtendedBakedModel model, BlockAndTintGetter view, BlockState state,
            BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        ModelData data = Objects.requireNonNull(model.getModelData(view, pos, state, attachment(view, pos)), "model data");
        int emission = com.stardew.craft.platform.fabric.light.LightEmissionHooks.getLightEmission(state, view, pos);
        for (RenderType layer : model.getRenderTypes(state, random.get(), data)) {
            emitFaces(model, state, data, layer, random, context, emission);
        }
    }

    public static void emitBlock(IDynamicBakedModel model, BlockAndTintGetter view, BlockState state,
            BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        emitBlock((ExtendedBakedModel) model, view, state, pos, random, context);
    }

    public static void emitItem(ExtendedBakedModel model, ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
        // This is the actual upstream ItemRenderer flag, despite the historical parameter name "fabulous".
        boolean direct = directItemBuffer(stack, context.itemTransformationMode());
        for (BakedModel pass : BakedModels.getRenderPasses(model, stack, direct)) {
            if (pass == model || pass instanceof IDynamicBakedModel) {
                for (RenderType layer : BakedModels.getRenderTypes(pass, stack, direct)) {
                    emitFaces(pass, null, ModelData.EMPTY, layer, random, context, 0);
                }
            } else {
                // A native FRAPI pass retains its own material, culling, light and vertex metadata.
                pass.emitItemQuads(stack, random, context);
            }
        }
    }

    public static void emitItem(IDynamicBakedModel model, ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
        emitItem((ExtendedBakedModel) model, stack, random, context);
    }

    /** BER/world callers already supply model data and a quad-query layer; do not replace either with a world snapshot. */
    public static void emitExplicitBlock(BakedModel model, BlockAndTintGetter view, BlockPos pos, BlockState state,
            ModelData data, RenderType layer, Supplier<RandomSource> random, RenderContext context) {
        emitFaces(model, state, Objects.requireNonNull(data, "model data"), layer, random, context,
                com.stardew.craft.platform.fabric.light.LightEmissionHooks.getLightEmission(state, view, pos));
    }

    public static boolean directItemBuffer(ItemStack stack, ItemDisplayContext context) {
        if (context != ItemDisplayContext.GUI && !context.firstPerson() && stack.getItem() instanceof BlockItem item) {
            return !(item.getBlock() instanceof HalfTransparentBlock) && !(item.getBlock() instanceof StainedGlassPaneBlock);
        }
        return true;
    }

    private static void emitFaces(BakedModel model, BlockState state, ModelData data, RenderType layer,
            Supplier<RandomSource> random, RenderContext context, int emission) {
        // Neo's ItemRenderer selects an entity buffer per pass, but queries item quads
        // without a chunk layer. Passing the entity sheet would erase material templates.
        RenderType quadLayer = state == null ? null : layer;
        TriState ao = state == null ? (model.useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE)
                : BakedModels.useAmbientOcclusion(model, state, data, layer);
        if (ao == TriState.DEFAULT && emission > 0) ao = TriState.FALSE;
        for (int index = 0; index < 7; index++) {
            Direction cull = index == 6 ? null : Direction.values()[index];
            for (BakedQuad quad : BakedModels.getQuads(model, state, cull, random.get(), data, quadLayer)) {
                var emitter = context.getEmitter();
                emitter.fromVanilla(quad, material(model, layer, ao, quad), cull).emit();
            }
        }
    }

    public static BlendMode blendMode(RenderType layer) {
        if (layer == null) return BlendMode.DEFAULT;
        if (layer == Sheets.solidBlockSheet()) return BlendMode.SOLID;
        if (layer == Sheets.cutoutBlockSheet()) return BlendMode.CUTOUT;
        if (layer == Sheets.translucentCullBlockSheet() || layer == Sheets.translucentItemSheet()) return BlendMode.TRANSLUCENT;
        BlendMode blend = BlendMode.fromRenderLayer(layer);
        if (blend == BlendMode.DEFAULT) throw new UnsupportedOperationException("No FRAPI blend mapping for layer: " + layer);
        return blend;
    }

    private static RenderMaterial material(BakedModel model, RenderType layer, TriState ao, BakedQuad quad) {
        var renderer = Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer(), "A Fabric Renderer API implementation is required");
        net.fabricmc.fabric.api.util.TriState fabricAo = !ModelQuad.ambientOcclusion(quad) || ao == TriState.FALSE
                ? net.fabricmc.fabric.api.util.TriState.FALSE : ao == TriState.TRUE
                ? net.fabricmc.fabric.api.util.TriState.TRUE : net.fabricmc.fabric.api.util.TriState.DEFAULT;
        var finder = renderer.materialFinder();
        // PL's named entity layers are custom RenderTypes, while its public group already owns
        // the exact FRAPI material used by its mesh builder. Keep that material for items too.
        RenderMaterial hinted = OrdinaryModelRenderTypes.materialFor(model, layer);
        if (hinted == null) finder.blendMode(blendMode(layer));
        else finder.copyFrom(hinted);
        return finder.ambientOcclusion(fabricAo)
                .disableDiffuse(!quad.isShade()).find();
    }
}
