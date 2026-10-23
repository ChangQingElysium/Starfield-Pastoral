/*
 * Adapted from NeoForge CompositeModel, Copyright (c) Forge Development LLC and contributors.
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.stardew.craft.platform.fabric.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.fabricators_of_create.porting_lib.models.ElementsModel;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.data.ModelProperty;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader;
import io.github.fabricators_of_create.porting_lib.models.geometry.IUnbakedGeometry;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import io.github.fabricators_of_create.porting_lib.render_types.RenderTypeGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The existing composite JSON format, with named ordered children and native FRAPI emission. */
public final class CompositeGeometry implements IUnbakedGeometry<CompositeGeometry> {
    public static final ResourceLocation LOADER_ID = ResourceLocation.fromNamespaceAndPath("neoforge", "composite");
    public static final IGeometryLoader<CompositeGeometry> LOADER = CompositeGeometry::read;
    private final Map<String, BlockModel> children;
    private final List<String> itemPasses;

    CompositeGeometry(Map<String, BlockModel> children, List<String> itemPasses) {
        this.children = Collections.unmodifiableMap(new LinkedHashMap<>(children));
        this.itemPasses = List.copyOf(itemPasses);
    }

    static CompositeGeometry read(JsonObject json, JsonDeserializationContext context) {
        Map<String, BlockModel> children = new LinkedHashMap<>();
        if (json.has("children")) json.getAsJsonObject("children").entrySet().forEach(entry ->
                children.put(entry.getKey(), Objects.requireNonNull(context.deserialize(entry.getValue(), BlockModel.class), "child model")));
        if (children.isEmpty()) throw new JsonParseException("Composite model requires non-empty children");
        List<String> passes = new ArrayList<>(children.keySet());
        if (json.has("item_render_order")) {
            passes.clear();
            for (var value : json.getAsJsonArray("item_render_order")) {
                String name = value.getAsString();
                if (!children.containsKey(name)) throw new JsonParseException("Unknown composite item pass: " + name);
                passes.add(name);
            }
        }
        return new CompositeGeometry(children, passes);
    }

    @Override public Set<String> getConfigurableComponentNames() { return children.keySet(); }
    @Override public void resolveParents(Function<ResourceLocation, UnbakedModel> models, IGeometryBakingContext context) {
        children.values().forEach(child -> child.resolveParents(models));
    }

    @Override public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
        if (!context.getRootTransform().isIdentity())
            state = composeRootTransform(state, context.getRootTransform());
        Map<String, BakedModel> baked = new LinkedHashMap<>();
        for (var entry : children.entrySet()) {
            if (!context.isComponentVisible(entry.getKey(), true)) continue;
            BlockModel child = entry.getValue();
            if (!((Object) child instanceof BlockModelExtension extension))
                throw new IllegalStateException("Porting Lib BlockModel geometry mixin is required for composite children");
            var childContext = extension.port_lib$getCustomData();
            // PL's vanilla BlockModel path does not apply its stored root transform/render hint.
            // Bake ordinary elements through PL too; inherited elements/UV-lock/cull faces are retained.
            BakedModel model = childContext.hasCustomGeometry()
                    ? child.bake(baker, child, sprites, state, true)
                    : new ElementsModel(child.getElements()).bake(childContext, baker, sprites, state,
                            extension.getOverrides(baker, child, sprites));
            if (!(model instanceof ExtendedBakedModel)) {
                var hint = childContext.getRenderTypeHint();
                if (hint != null) {
                    RenderTypeGroup group = childContext.getRenderType(hint);
                    if (!group.isEmpty()) model = new LayeredChild(model, group);
                }
            }
            baked.put(entry.getKey(), model);
        }
        List<BakedModel> passes = new ArrayList<>();
        for (String name : itemPasses) {
            BakedModel part = baked.get(name);
            if (part != null) passes.add(part);
            else if (context.isComponentVisible(name, true)) throw new IllegalStateException("Missing composite item pass: " + name);
        }
        return new Baked(context.isGui3d(), context.useBlockLight(), context.useAmbientOcclusion(),
                sprites.apply(context.getMaterial("particle")), context.getTransforms(), overrides, baked, passes);
    }

    // Same origin convention as PL/Neo, expressed with native matrices so this operation does not
    // depend on an additional method injected into Mojang's final Transformation class.
    static ModelState composeRootTransform(ModelState state, com.mojang.math.Transformation root) {
        var shifted = new org.joml.Matrix4f().translation(-.5F, -.5F, -.5F).mul(root.getMatrix()).translate(.5F, .5F, .5F);
        var combined = state.getRotation().compose(new com.mojang.math.Transformation(shifted));
        return new OrdinaryModelTransforms.RootTransformState(combined, state.isUvLocked());
    }

    /** Native PL meshes retain their own FRAPI material; explicit extended callers also retain layer hints. */
    private static final class LayeredChild extends BakedModelWrapper<BakedModel> {
        private final RenderTypeGroup types;
        private LayeredChild(BakedModel model, RenderTypeGroup types) { super(model); this.types = types; }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) { return ChunkRenderTypeSet.of(types.block()); }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean direct) { return List.of(direct ? types.entity() : types.entityFabulous()); }
    }

    public static final class Baked implements IDynamicBakedModel {
        private final boolean gui3d, blockLight, ambientOcclusion;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final ItemOverrides overrides;
        private final Map<String, BakedModel> children;
        private final List<BakedModel> itemPasses;

        Baked(boolean gui3d, boolean blockLight, boolean ambientOcclusion, TextureAtlasSprite particle,
                ItemTransforms transforms, ItemOverrides overrides, Map<String, BakedModel> children, List<BakedModel> itemPasses) {
            this.gui3d = gui3d; this.blockLight = blockLight; this.ambientOcclusion = ambientOcclusion;
            this.particle = particle; this.transforms = transforms; this.overrides = overrides;
            this.children = Collections.unmodifiableMap(new LinkedHashMap<>(children)); this.itemPasses = List.copyOf(itemPasses);
        }
        @Nullable public BakedModel getPart(String name) { return children.get(name); }
        @Override public boolean useAmbientOcclusion() { return ambientOcclusion; }
        @Override public boolean isGui3d() { return gui3d; }
        @Override public boolean usesBlockLight() { return blockLight; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public ItemOverrides getOverrides() { return overrides; }
        @Override public ItemTransforms getTransforms() { return transforms; }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean direct) { return itemPasses; }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
            List<BakedQuad> quads = new ArrayList<>();
            for (var entry : children.entrySet()) {
                ModelData childData = Data.resolve(data, entry.getKey());
                if (layer == null || state != null && BakedModels.getRenderTypes(entry.getValue(), state, random, childData).contains(layer))
                    quads.addAll(BakedModels.getQuads(entry.getValue(), state, side, random, childData, layer));
            }
            return quads;
        }
        @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData original) {
            Map<String, ModelData> childData = new LinkedHashMap<>();
            children.forEach((name, model) -> childData.put(name, BakedModels.getModelData(model, view, pos, state, Data.resolve(original, name))));
            return original.derive().with(Data.PROPERTY, new Data(childData)).build();
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            var layers = new ArrayList<ChunkRenderTypeSet>();
            children.forEach((name, model) -> layers.add(BakedModels.getRenderTypes(model, state, random, Data.resolve(data, name))));
            return ChunkRenderTypeSet.union(layers.toArray(ChunkRenderTypeSet[]::new));
        }
        @Override public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                Supplier<RandomSource> random, RenderContext context) {
            ModelData data = getModelData(view, pos, state, DynamicModelEmitter.attachment(view, pos));
            emitResolvedBlockQuads(view, state, pos, data, random, context);
        }
        private void emitResolvedBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                ModelData data, Supplier<RandomSource> random, RenderContext context) {
            children.forEach((name, model) -> {
                ModelData childData = Data.resolve(data, name);
                if (model instanceof Baked composite) {
                    // getModelData above has already resolved every nested child exactly once.
                    // Recursing into meshes preserves their materials instead of flattening their quads.
                    composite.emitResolvedBlockQuads(view, state, pos, childData, random, context);
                } else if (model instanceof ExtendedBakedModel && (model instanceof IDynamicBakedModel || model.isVanillaAdapter())) {
                    for (RenderType layer : BakedModels.getRenderTypes(model, state, random.get(), childData))
                        DynamicModelEmitter.emitExplicitBlock(model, view, pos, state, childData, layer, random, context);
                } else {
                    // In particular, Porting Lib MeshBakedModel owns material/AO/emissive metadata.
                    model.emitBlockQuads(view, state, pos, random, context);
                }
            });
        }
        @Override public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
            // Each child owns its own ordered passes and native material, including nested composites.
            for (BakedModel pass : itemPasses) pass.emitItemQuads(stack, random, context);
        }
    }

    public static final class Data {
        public static final ModelProperty<Data> PROPERTY = new ModelProperty<>();
        private final Map<String, ModelData> children;
        public Data(Map<String, ModelData> children) { this.children = Map.copyOf(children); }
        public static ModelData resolve(ModelData parent, String name) {
            Data composite = parent.get(PROPERTY);
            return composite == null ? parent : composite.children.getOrDefault(name, parent);
        }
    }
}
