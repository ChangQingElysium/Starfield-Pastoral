package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.data.ModelProperty;
import io.github.fabricators_of_create.porting_lib.models.geometry.BlockGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.IUnbakedGeometry;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CompositeGeometryTest {
    private static final ModelProperty<Integer> NUMBER = new ModelProperty<>();
    private static final Material MATERIAL = new Material(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/stone"));
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); if (RendererAccess.INSTANCE.getRenderer() == null) RendererAccess.INSTANCE.registerRenderer(IndigoRenderer.INSTANCE); }
    @Test void loaderPreservesChildOrderAndAnExplicitRepeatedItemPassOrderAndRejectsInvalidLayouts() {
        var first = new FakeModel(new QueryModel(RenderType.cutout())); var second = new FakeModel(new QueryModel(RenderType.translucent()));
        JsonDeserializationContext parser = parser(Map.of("a", first, "b", second));
        var geometry = CompositeGeometry.read(json("{children:{a:'a',b:'b'},item_render_order:['b','a','b']}"), parser);
        assertEquals(List.of("a", "b"), new ArrayList<>(geometry.getConfigurableComponentNames()));
        var baked = (CompositeGeometry.Baked) geometry.bake(context(Set.of(), Transformation.identity()), null, m -> sprite(), BlockModelRotation.X0_Y0, ItemOverrides.EMPTY);
        assertSame(first.result, baked.getPart("a")); assertSame(second.result, baked.getPart("b"));
        assertEquals(List.of(second.result, first.result, second.result), baked.getRenderPasses(ItemStack.EMPTY, true));
        assertThrows(JsonParseException.class, () -> CompositeGeometry.read(json("{}"), parser));
        assertThrows(JsonParseException.class, () -> CompositeGeometry.read(json("{children:{}}"), parser));
        assertThrows(JsonParseException.class, () -> CompositeGeometry.read(json("{children:{a:'a'},item_render_order:['missing']}"), parser));
    }
    @Test void visibilitySkipsBakingAndItemPassesWhileParentsStillResolveAndRootTransformReachesChildren() {
        var a = new FakeModel(new QueryModel(RenderType.cutout())); var b = new FakeModel(new QueryModel(RenderType.cutout()));
        var geometry = new CompositeGeometry(ordered("a", a, "b", b), List.of("a", "b")); var root = new Transformation(new Matrix4f().translation(.25F, .5F, 0));
        geometry.resolveParents(id -> null, context(Set.of("b"), root)); assertEquals(1, a.parents); assertEquals(1, b.parents);
        var baked = (CompositeGeometry.Baked) geometry.bake(context(Set.of("b"), root), null, m -> sprite(), BlockModelRotation.X0_Y90, ItemOverrides.EMPTY);
        assertNull(baked.getPart("b")); assertNull(b.state); assertNotNull(a.state);
        var expected = new Matrix4f(BlockModelRotation.X0_Y90.getRotation().getMatrix()).mul(new Matrix4f().translation(-.5F, -.5F, -.5F).mul(root.getMatrix()).translate(.5F, .5F, .5F));
        assertTrue(expected.equals(a.state.getRotation().getMatrix(), .00001F));
        assertEquals(List.of(a.result), baked.getRenderPasses(ItemStack.EMPTY, true)); assertSame(SPRITE, baked.getParticleIcon());
        assertTrue(baked.useAmbientOcclusion()); assertTrue(baked.isGui3d()); assertFalse(baked.usesBlockLight());
    }
    @Test void eachChildReceivesItsOwnModelDataAndCullLayerAndLayerUnionKeepsOrderWithoutDuplicates() {
        var a = new QueryModel(RenderType.cutout()); var b = new QueryModel(RenderType.translucent());
        var baked = baked(ordered("a", a, "b", b)); ModelData root = ModelData.of(NUMBER, 3);
        var input = root.derive().with(CompositeGeometry.Data.PROPERTY, new CompositeGeometry.Data(Map.of("a", ModelData.of(NUMBER, 7)))).build();
        var data = baked.getModelData(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), input);
        assertEquals(7, a.seed.get(NUMBER)); assertEquals(3, b.seed.get(NUMBER)); assertEquals(3, data.get(NUMBER));
        var random = RandomSource.create(1); var quads = baked.getQuads(Blocks.STONE.defaultBlockState(), Direction.NORTH, random, data, RenderType.translucent());
        assertEquals(1, quads.size()); assertSame(b.quad, quads.getFirst()); assertEquals(0, a.queries); assertEquals(1, b.queries);
        assertSame(Direction.NORTH, b.side); assertEquals(13, b.queriedData.get(NUMBER));
        assertEquals(List.of(RenderType.cutout(), RenderType.translucent()), baked.getRenderTypes(Blocks.STONE.defaultBlockState(), random, data).asList());
        assertEquals(2, baked.getQuads(null, null, random).size());
    }
    @Test void nestedModelDataPreservesEachCompositeBoundaryAndUnspecifiedChildDataFallsBackToItsParent() {
        var innerData = ModelData.of(NUMBER, 4); var nested = ModelData.EMPTY.derive().with(CompositeGeometry.Data.PROPERTY, new CompositeGeometry.Data(Map.of("inner", innerData))).build();
        var root = ModelData.of(NUMBER, 9).derive().with(CompositeGeometry.Data.PROPERTY, new CompositeGeometry.Data(Map.of("outer", nested))).build();
        assertSame(nested, CompositeGeometry.Data.resolve(root, "outer")); assertSame(innerData, CompositeGeometry.Data.resolve(nested, "inner")); assertSame(root, CompositeGeometry.Data.resolve(root, "absent"));
    }
    @Test void nativeFrApiChildrenEmitTheirOwnMeshWithoutBeingFlattenedThroughVanillaQuads() {
        var calls = new ArrayList<String>(); BakedModel a = nativeModel("a", calls); BakedModel b = nativeModel("b", calls); var composite = baked(ordered("a", a, "b", b));
        composite.emitBlockQuads(view(ModelData.EMPTY), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, () -> RandomSource.create(1), null);
        assertEquals(List.of("a", "b"), calls);
    }
    @Test void nestedWorldEmissionKeepsNativeMeshAndResolvesDynamicChildDataOnlyOnce() {
        var calls = new ArrayList<String>();
        var dynamic = new QueryModel(RenderType.cutout()) {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
                super.getQuads(state, side, random, data, layer);
                return List.of();
            }
        };
        var inner = baked(ordered("dynamic", dynamic, "mesh", nativeModel("mesh", calls)));
        var outer = baked(Map.of("outer", inner));
        var innerData = ModelData.EMPTY.derive().with(CompositeGeometry.Data.PROPERTY,
                new CompositeGeometry.Data(Map.of("dynamic", ModelData.of(NUMBER, 4)))).build();
        var root = ModelData.EMPTY.derive().with(CompositeGeometry.Data.PROPERTY,
                new CompositeGeometry.Data(Map.of("outer", innerData))).build();
        outer.emitBlockQuads(view(root), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, () -> RandomSource.create(1), null);
        assertEquals(List.of("mesh"), calls);
        assertEquals(4, dynamic.seed.get(NUMBER));
        assertEquals(14, dynamic.queriedData.get(NUMBER));
        assertEquals(7, dynamic.queries);
    }
    @Test void nestedItemEmissionPreservesRepeatedOrderedNativePassesWithoutFlattening() {
        var calls = new ArrayList<String>();
        var a = nativeModel("a", calls); var b = nativeModel("b", calls);
        var inner = new CompositeGeometry.Baked(true, true, true, sprite(), ItemTransforms.NO_TRANSFORMS,
                ItemOverrides.EMPTY, ordered("a", a, "b", b), List.of(b, a, b));
        baked(Map.of("inner", inner)).emitItemQuads(ItemStack.EMPTY, () -> RandomSource.create(1), null);
        assertEquals(List.of("item:b", "item:a", "item:b"), calls);
    }
    @Test void ordinaryElementChildrenUseActualPlBakingAndKeepNativeFrApiRenderMaterial() {
        var face = new BlockElementFace(null, -1, "#stone", new BlockFaceUV(new float[]{0, 0, 16, 16}, 0));
        var element = new BlockElement(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16), Map.of(Direction.UP, face), null, true);
        var child = new ElementModel(List.of(element)); child.custom.setRenderTypeHint(ResourceLocation.withDefaultNamespace("translucent"));
        var composite = (CompositeGeometry.Baked) new CompositeGeometry(Map.of("mesh", child), List.of("mesh")).bake(context(Set.of(), Transformation.identity()), null, m -> sprite(), BlockModelRotation.X0_Y0, ItemOverrides.EMPTY);
        assertEquals(List.of(RenderType.translucent()), composite.getRenderTypes(Blocks.STONE.defaultBlockState(), RandomSource.create(1), ModelData.EMPTY).asList());
        var mesh = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        var renderContext = (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(), new Class<?>[]{RenderContext.class}, (p, m, a) -> { if (m.getName().equals("getEmitter")) return mesh.getEmitter(); throw new AssertionError(m); });
        composite.emitBlockQuads(view(ModelData.EMPTY), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, () -> RandomSource.create(1), renderContext);
        var xs = new ArrayList<Float>(); var blends = new ArrayList<BlendMode>();
        mesh.build().forEach(q -> { blends.add(q.material().blendMode()); assertNull(q.cullFace()); for (int i = 0; i < 4; i++) xs.add(q.x(i)); });
        assertEquals(List.of(BlendMode.TRANSLUCENT), blends); assertEquals(0F, java.util.Collections.min(xs), .00001F); assertEquals(1F, java.util.Collections.max(xs), .00001F);
    }
    @Test void geometryEventRegistersTheRealCompositeLoaderAndRejectsConflictingClaims() {
        var bridge = new FabricModelEvents(new com.stardew.craft.platform.fabric.event.EventBus()); var loaders = new LinkedHashMap<ResourceLocation, io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader<?>>();
        bridge.registerGeometryLoaders(loaders); assertSame(CompositeGeometry.LOADER, loaders.get(CompositeGeometry.LOADER_ID));
        assertThrows(IllegalArgumentException.class, () -> bridge.registerGeometryLoaders(loaders));
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static JsonDeserializationContext parser(Map<String, BlockModel> models) { return new JsonDeserializationContext() { @SuppressWarnings("unchecked") public <T> T deserialize(JsonElement value, Type type) { assertSame(BlockModel.class, type); return (T) models.get(value.getAsString()); } }; }
    private static <T> Map<String, T> ordered(String a, T first, String b, T second) { var map = new LinkedHashMap<String, T>(); map.put(a, first); map.put(b, second); return map; }
    private static CompositeGeometry.Baked baked(Map<String, BakedModel> children) { return new CompositeGeometry.Baked(true, true, true, sprite(), ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY, children, new ArrayList<>(children.values())); }
    private static IGeometryBakingContext context(Set<String> hidden, Transformation root) { return (IGeometryBakingContext) Proxy.newProxyInstance(IGeometryBakingContext.class.getClassLoader(), new Class<?>[]{IGeometryBakingContext.class}, (p, m, a) -> switch (m.getName()) { case "getRootTransform" -> root; case "isComponentVisible" -> !hidden.contains(a[0]); case "getMaterial" -> MATERIAL; case "getTransforms" -> ItemTransforms.NO_TRANSFORMS; case "isGui3d", "useAmbientOcclusion" -> true; case "useBlockLight" -> false; default -> throw new AssertionError(m); }); }
    private static BlockAndTintGetter view(ModelData data) { return (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class, FabricBlockView.class}, (p, m, a) -> { if (m.getName().equals("getBlockEntityRenderData")) return data; throw new AssertionError("unexpected world read: " + m); }); }
    private static BakedModel nativeModel(String name, List<String> calls) { return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class}, (p, m, a) -> switch (m.getName()) { case "emitBlockQuads" -> { calls.add(name); yield null; } case "emitItemQuads" -> { calls.add("item:" + name); yield null; } case "isVanillaAdapter" -> false; case "getQuads" -> throw new AssertionError("native mesh flattened"); default -> throw new AssertionError(m); }); }
    private static final class FakeModel extends ElementModel {
        final BakedModel result; ModelState state; int parents;
        FakeModel(BakedModel result) { super(List.of()); this.result = result; custom.setCustomGeometry(new DummyGeometry()); }
        @Override public BakedModel bake(ModelBaker baker, BlockModel owner, Function<Material, TextureAtlasSprite> sprites, ModelState state, boolean gui) { this.state = state; return result; }
        @Override public void resolveParents(Function<ResourceLocation, UnbakedModel> models) { parents++; }
    }
    private static final class DummyGeometry implements IUnbakedGeometry<DummyGeometry> { public BakedModel bake(IGeometryBakingContext c, ModelBaker b, Function<Material, TextureAtlasSprite> s, ModelState ms, ItemOverrides o) { throw new AssertionError(); } }
    private static class ElementModel extends BlockModel implements BlockModelExtension {
        final BlockGeometryBakingContext custom = new BlockGeometryBakingContext(this);
        ElementModel(List<BlockElement> elements) { super(null, elements, Map.of("stone", Either.left(MATERIAL), "particle", Either.left(MATERIAL)), true, GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, List.of()); }
        @Override public BlockGeometryBakingContext port_lib$getCustomData() { return custom; }
        @Override public ItemOverrides getOverrides(ModelBaker baker, BlockModel owner, Function<Material, TextureAtlasSprite> sprites) { return ItemOverrides.EMPTY; }
    }
    private static class QueryModel implements IDynamicBakedModel {
        final RenderType layer; final BakedQuad quad = allocate(BakedQuad.class); ModelData seed, queriedData; Direction side; int queries;
        QueryModel(RenderType layer) { this.layer = layer; }
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) { return List.of(quad); }
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) { queries++; queriedData = data; this.side = side; return List.of(quad); }
        public ModelData getModelData(BlockAndTintGetter v, BlockPos p, BlockState s, ModelData d) { seed = d; return d.derive().with(NUMBER, d.get(NUMBER) + 10).build(); }
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) { return ChunkRenderTypeSet.of(layer); }
        public boolean useAmbientOcclusion() { return true; } public boolean isGui3d() { return true; } public boolean usesBlockLight() { return true; } public boolean isCustomRenderer() { return false; } public TextureAtlasSprite getParticleIcon() { return sprite(); } public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; } public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
    }
    private static final Sprite SPRITE = allocate(Sprite.class);
    private static TextureAtlasSprite sprite() { return SPRITE; }
    private static final class Sprite extends TextureAtlasSprite {
        Sprite() { super(null, null, 0, 0, 0, 0); }
        @Override public float getU(float u) { return u; } @Override public float getV(float v) { return v; } @Override public float uvShrinkRatio() { return 0; }
    }
    private static <T> T allocate(Class<T> type) { try { var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type)); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
}
