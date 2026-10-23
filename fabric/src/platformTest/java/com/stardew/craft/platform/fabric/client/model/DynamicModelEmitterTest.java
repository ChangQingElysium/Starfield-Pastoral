package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.util.TriState;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.data.ModelProperty;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicModelEmitterTest {
    private static final ModelProperty<Integer> NUMBER = new ModelProperty<>();
    private static final Supplier<RandomSource> RANDOM = () -> RandomSource.create(42);
    private static final int PACKED_LIGHT = 8 << 4 | 12 << 20;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        if (RendererAccess.INSTANCE.getRenderer() == null) RendererAccess.INSTANCE.registerRenderer(IndigoRenderer.INSTANCE);
    }

    @Test void blockEmissionUsesTheSnapshotAndEveryLayerAndCullFaceWithoutReadingTheBlockEntity() {
        ModelData attached = ModelData.of(NUMBER, 4);
        BlockAndTintGetter view = view(attached);
        var model = new TestModel();
        var context = new Context(ItemDisplayContext.NONE);
        DynamicModelEmitter.emitBlock(model, view, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RANDOM, context);
        assertSame(attached, model.seenData);
        assertEquals(14, model.calls.size());
        var output = context.output();
        assertEquals(14, output.size());
        assertEquals(7, output.stream().filter(q -> q.blend == BlendMode.CUTOUT).count());
        assertEquals(7, output.stream().filter(q -> q.blend == BlendMode.TRANSLUCENT).count());
        for (Direction face : Direction.values()) assertEquals(2, output.stream().filter(q -> q.cull == face).count());
        assertEquals(2, output.stream().filter(q -> q.cull == null).count());
        assertTrue(output.stream().allMatch(q -> q.light == PACKED_LIGHT));
        assertTrue(output.stream().allMatch(q -> q.disableDiffuse));
        assertTrue(output.stream().allMatch(q -> q.ao == net.fabricmc.fabric.api.util.TriState.FALSE));
    }

    @Test void itemEmissionPreservesAllModelPassesAndTheirLayers() {
        var first = new TestModel();
        var second = new TestModel();
        first.itemLayer = Sheets.solidBlockSheet();
        second.itemLayer = Sheets.translucentItemSheet();
        var outer = new TestModel();
        outer.passes = List.of(first, second);
        var context = new Context(ItemDisplayContext.GUI);
        DynamicModelEmitter.emitItem(outer, new ItemStack(Items.STONE), RANDOM, context);
        assertTrue(outer.calls.isEmpty());
        assertEquals(7, first.calls.size());
        assertEquals(7, second.calls.size());
        assertEquals(7, context.output().stream().filter(q -> q.blend == BlendMode.SOLID).count());
        // output() snapshots the already-built mesh, so use a fresh context for a separate render.
        var again = new Context(ItemDisplayContext.GUI);
        DynamicModelEmitter.emitItem(outer, new ItemStack(Items.STONE), RANDOM, again);
        assertEquals(7, again.output().stream().filter(q -> q.blend == BlendMode.TRANSLUCENT).count());
        assertTrue(first.direct);
        assertTrue(second.direct);
    }

    @Test void itemPassNativeFrApiProducerIsInvokedWithoutFlatteningItsMaterial() {
        var nativeCalls = new ArrayList<String>();
        var nativePass = new TestModel() {
            @Override public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
                nativeCalls.add("native-emission");
            }
        };
        // Native API models do not implement the project extension interface.
        BakedModel pass = new net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel(nativePass) {
            @Override public boolean isVanillaAdapter() { return false; }
            @Override public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
                nativeCalls.add("forwarded-native-emission");
            }
        };
        var outer = new TestModel(); outer.passes = List.of(pass);
        DynamicModelEmitter.emitItem(outer, ItemStack.EMPTY, RANDOM, new Context(ItemDisplayContext.GUI));
        assertEquals(List.of("forwarded-native-emission"), nativeCalls);
    }

    @Test void templateItemGeometryIsNotFilteredByItsEntityBufferLayer() throws Exception {
        // Use the actual template material filter which erased the creative-tab icons.
        // The item's entity sheet selects a buffer; Neo's ItemRenderer queries quads with no layer.
        var filter = Class.forName("com.stardew.craft.templates.client.TemplateBakedModel")
                .getDeclaredMethod("supportsLayer", BakedModel.class, BlockState.class, RenderType.class);
        filter.setAccessible(true);
        var material = new TestModel() {
            @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
                return ChunkRenderTypeSet.of(RenderType.solid());
            }
        };
        var layers = List.of(Sheets.solidBlockSheet(), Sheets.cutoutBlockSheet(), Sheets.translucentItemSheet());
        var blends = List.of(BlendMode.SOLID, BlendMode.CUTOUT, BlendMode.TRANSLUCENT);
        for (var display : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.GROUND)) {
            for (int index = 0; index < layers.size(); index++) {
                var template = new TestModel() {
                    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                            ModelData data, RenderType layer) {
                        try {
                            if (!(boolean) filter.invoke(null, material, Blocks.OAK_PLANKS.defaultBlockState(), layer)) return List.of();
                        } catch (ReflectiveOperationException failure) {
                            throw new AssertionError(failure);
                        }
                        return super.getQuads(state, side, random, data, layer);
                    }
                };
                template.itemLayer = layers.get(index);
                var context = new Context(display);
                DynamicModelEmitter.emitItem(template, new ItemStack(Items.OAK_PLANKS), RANDOM, context);
                var output = context.output();
                assertEquals(7, output.size(), "Template disappeared in " + display + " / " + template.itemLayer);
                assertTrue(template.calls.stream().allMatch(call -> call.layer == null));
                BlendMode expected = blends.get(index);
                assertTrue(output.stream().allMatch(quad -> quad.blend == expected), "Keep the selected item buffer material");
            }
        }
    }

    @Test void actualSeasonalFurnitureModelsRetainTheirGeometryAndSeasonInEveryItemContext() throws Exception {
        BakedModel[] seasons = new BakedModel[4];
        for (int season = 0; season < seasons.length; season++) seasons[season] = taggedModel(200 + season);
        var models = new ArrayList<BakedModel>();
        var simple = Class.forName("com.stardew.craft.client.model.OutdoorTableModels$Seasonal")
                .getDeclaredConstructor(BakedModel.class, BakedModel[].class);
        simple.setAccessible(true);
        models.add((BakedModel) simple.newInstance(seasons[0], seasons));
        for (String owner : List.of("ParkBenchModels", "BlueFlowerpotSeasonModels", "SpecialOrderSiteModels",
                "GravestoneModels", "SaloonSignSeasonModels")) {
            var constructor = Class.forName("com.stardew.craft.client.model." + owner + "$Seasonal")
                    .getDeclaredConstructor(BakedModel.class, BakedModel[].class, Direction.class, boolean.class);
            constructor.setAccessible(true);
            models.add((BakedModel) constructor.newInstance(seasons[0], seasons, Direction.NORTH, true));
        }
        var planter = Class.forName("com.stardew.craft.client.model.GardenPlanterModels$Seasonal")
                .getDeclaredConstructor(BakedModel.class, List.class);
        planter.setAccessible(true);
        models.add((BakedModel) planter.newInstance(seasons[0], java.util.Arrays.stream(seasons).map(List::of).toList()));
        var textureSelection = Class.forName("com.stardew.craft.client.model.terrain.TerrainSeasonTextures");
        var seasonField = textureSelection.getDeclaredField("currentSeason");
        seasonField.setAccessible(true);
        int previous = seasonField.getInt(null);
        try {
            for (int season = 0; season < 4; season++) {
                seasonField.setInt(null, season);
                for (var model : models) for (var display : List.of(ItemDisplayContext.GUI,
                        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.GROUND)) {
                    assertSame(model, BakedModels.applyTransform(model, display, new PoseStack(), false));
                    var context = new Context(display);
                    model.emitItemQuads(new ItemStack(Items.STONE), RANDOM, context);
                    var output = context.output();
                    assertEquals(1, output.size(), model.getClass().getName() + " / " + display);
                    assertEquals(200 + season, output.getFirst().tint, model.getClass().getName());
                }
            }
        } finally {
            seasonField.setInt(null, previous);
        }
    }

    @Test void actualMineItemOverridesRetainEverySavedThemeAndVariantBeforeEmission() throws Exception {
        var themeClass = Class.forName("com.stardew.craft.block.mine.MineBuildingTheme");
        var themes = themeClass.getEnumConstants();
        var property = (net.minecraft.world.level.block.state.properties.Property<?>) themeClass.getField("PROPERTY").get(null);
        var variantProperty = (net.minecraft.world.level.block.state.properties.Property<?>) Class.forName(
                "com.stardew.craft.block.mine.MinePlanksBlock").getField("VARIANT").get(null);
        BakedModel[][] variants = new BakedModel[themes.length][2];
        for (int theme = 0; theme < themes.length; theme++) for (int variant = 0; variant < 2; variant++)
            variants[theme][variant] = taggedModel(300 + theme * 2 + variant);
        var constructor = Class.forName("com.stardew.craft.client.model.MineThemeItemModels$Themed")
                .getDeclaredConstructor(BakedModel.class, BakedModel[][].class);
        constructor.setAccessible(true);
        var model = (BakedModel) constructor.newInstance(taggedModel(-1), variants);
        for (int theme = 0; theme < themes.length; theme++) for (int variant = 0; variant < 2; variant++) {
            var stack = new ItemStack(Items.STONE);
            stack.set(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                    new net.minecraft.world.item.component.BlockItemStateProperties(java.util.Map.of(
                            property.getName(), ((net.minecraft.util.StringRepresentable) themes[theme]).getSerializedName(),
                            variantProperty.getName(), Integer.toString(variant))));
            BakedModel selected = model.getOverrides().resolve(model, stack, null, null, 0);
            assertSame(variants[theme][variant], selected);
            var context = new Context(ItemDisplayContext.GUI);
            selected.emitItemQuads(stack, RANDOM, context);
            assertEquals(300 + theme * 2 + variant, context.output().getFirst().tint);
        }
        assertSame(variants[0][0], model.getOverrides().resolve(model, new ItemStack(Items.STONE), null, null, 0));
    }

    @Test void itemBufferFlagMatchesUpstreamGuiHandAndTransparentBlockCases() {
        var glass = new ItemStack(Items.GLASS);
        assertTrue(DynamicModelEmitter.directItemBuffer(glass, ItemDisplayContext.GUI));
        assertTrue(DynamicModelEmitter.directItemBuffer(glass, ItemDisplayContext.FIRST_PERSON_LEFT_HAND));
        assertFalse(DynamicModelEmitter.directItemBuffer(glass, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        assertFalse(DynamicModelEmitter.directItemBuffer(new ItemStack(Items.WHITE_STAINED_GLASS_PANE), ItemDisplayContext.GROUND));
        assertTrue(DynamicModelEmitter.directItemBuffer(new ItemStack(Items.GLASS_PANE), ItemDisplayContext.GROUND));
        assertTrue(DynamicModelEmitter.directItemBuffer(new ItemStack(Items.STONE), ItemDisplayContext.GROUND));
        assertTrue(DynamicModelEmitter.directItemBuffer(new ItemStack(Items.STICK), ItemDisplayContext.GROUND));
    }

    @Test void renderLayerSetsAreOrderedImmutableAndDeduplicated() {
        var a = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout(), RenderType.solid());
        var both = ChunkRenderTypeSet.union(a, ChunkRenderTypeSet.of(RenderType.translucent(), RenderType.cutout()));
        assertEquals(List.of(RenderType.solid(), RenderType.cutout(), RenderType.translucent()), both.asList());
        assertThrows(UnsupportedOperationException.class, () -> both.asList().clear());
    }

    @Test void wrapperDispatchesExtendedDataInsteadOfInventingVanillaFiveArgumentCalls() {
        var inner = new TestModel();
        var wrapper = new BakedModelWrapper<>(inner);
        var data = ModelData.of(NUMBER, 7);
        assertEquals(1, BakedModels.getQuads(wrapper, Blocks.STONE.defaultBlockState(), Direction.UP,
                RandomSource.create(), data, RenderType.cutout()).size());
        assertSame(data, inner.calls.getFirst().data);
        assertSame(RenderType.cutout(), inner.calls.getFirst().layer);
    }

    @Test void contextualEmissionDisablesDefaultAoWhileExplicitTrueStillForcesIt() {
        for (TriState mode : TriState.values()) {
            var model = new TestModel() {
                @Override public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType layer) { return mode; }
                @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
                    var quad = super.getQuads(state, side, random, data, layer).getFirst();
                    return List.of(new ModelQuad(quad.getVertices(), quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), true));
                }
            };
            var context = new Context(ItemDisplayContext.GUI);
            DynamicModelEmitter.emitBlock(model, view(ModelData.EMPTY), Blocks.GLOWSTONE.defaultBlockState(), BlockPos.ZERO, RANDOM, context);
            var expected = mode == TriState.TRUE ? net.fabricmc.fabric.api.util.TriState.TRUE : net.fabricmc.fabric.api.util.TriState.FALSE;
            assertTrue(context.output().stream().allMatch(quad -> quad.ao == expected));
        }
    }

    private static BlockAndTintGetter view(Object attachment) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class, FabricBlockView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockEntityRenderData")) return attachment;
                    throw new AssertionError("Unexpected live world access: " + method);
                });
    }

    private record Call(Direction face, ModelData data, RenderType layer) {}
    private record Output(Direction cull, BlendMode blend, int light, boolean disableDiffuse, int tint,
            net.fabricmc.fabric.api.util.TriState ao) {}

    private static TestModel taggedModel(int tint) {
        return new TestModel() {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                    ModelData data, RenderType layer) {
                if (side != null) return List.of();
                var quad = super.getQuads(state, side, random, data, layer).getFirst();
                return List.of(new ModelQuad(quad.getVertices(), tint, quad.getDirection(), quad.getSprite(), quad.isShade(), false));
            }
        };
    }

    private static class TestModel implements IDynamicBakedModel {
        final List<Call> calls = new ArrayList<>();
        ModelData seenData;
        RenderType itemLayer = Sheets.cutoutBlockSheet();
        List<BakedModel> passes = List.of(this);
        boolean direct;
        @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData data) { seenData = data; return data; }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent());
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean direct) { this.direct = direct; return passes; }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean direct) { this.direct = direct; return List.of(itemLayer); }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
            calls.add(new Call(side, data, layer));
            int[] vertices = new int[32];
            for (int v = 0; v < 4; v++) {
                vertices[v * 8] = Float.floatToIntBits(v == 1 || v == 2 ? 1 : 0);
                vertices[v * 8 + 2] = Float.floatToIntBits(v >= 2 ? 1 : 0);
                vertices[v * 8 + 3] = -1;
                vertices[v * 8 + 6] = PACKED_LIGHT;
            }
            return List.of(new ModelQuad(vertices, 2, Direction.UP, null, false, false));
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) { return getQuads(state, side, random, ModelData.EMPTY, null); }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType layer) { return TriState.TRUE; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static final class Context implements RenderContext {
        private final MeshBuilder builder = IndigoRenderer.INSTANCE.meshBuilder();
        private final ItemDisplayContext display;
        Context(ItemDisplayContext display) { this.display = display; }
        @Override public QuadEmitter getEmitter() { return builder.getEmitter(); }
        @Override public void pushTransform(QuadTransform transform) { throw new AssertionError("Unexpected transform"); }
        @Override public void popTransform() { throw new AssertionError("Unexpected transform"); }
        @Override public ItemDisplayContext itemTransformationMode() { return display; }
        @Override public BakedModelConsumer bakedModelConsumer() { throw new AssertionError("Deprecated fallback"); }
        List<Output> output() {
            List<Output> output = new ArrayList<>();
            builder.build().forEach(quad -> output.add(new Output(quad.cullFace(), quad.material().blendMode(),
                    quad.lightmap(0), quad.material().disableDiffuse(), quad.colorIndex(), quad.material().ambientOcclusion())));
            return output;
        }
    }
}
