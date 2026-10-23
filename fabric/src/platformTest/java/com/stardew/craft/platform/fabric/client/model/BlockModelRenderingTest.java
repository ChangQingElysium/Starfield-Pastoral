package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.platform.mixin.client.model.mixin.BlockRenderDispatcherColorsAccessor;
import com.stardew.craft.platform.fabric.modeldata.ModelDataProvider;
import com.stardew.craft.platform.fabric.util.TriState;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.data.ModelProperty;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockModelRenderingTest {
    private static final ModelProperty<Integer> NUMBER = new ModelProperty<>();

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        if (RendererAccess.INSTANCE.getRenderer() == null) RendererAccess.INSTANCE.registerRenderer(IndigoRenderer.INSTANCE);
    }

    @Test void extendedModelUsesNativeQuadOutputAndForwardsDataLayerAndSevenIdenticallySeededBuckets() {
        var model = new TestModel();
        var colors = new BlockColors();
        var renderer = new ModelBlockRenderer(colors);
        var pose = new PoseStack(); pose.translate(.25, .75, -.5);
        var expected = new Vertices();
        var actual = new Vertices();
        // The native renderer is the reference for unchanged quads/pose, tint clamping, light and overlay.
        // Capture its bulk-output boundary without invoking LWJGL's native memory allocator in a headless test.
        var nativeModel = new net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel(model) {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
                return model.quads(face);
            }
        };
        var state = Blocks.STONE.defaultBlockState();
        var data = ModelData.of(NUMBER, 73);
        var layer = RenderType.entityCutout(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        renderer.renderModel(pose.last(), expected, state, nativeModel, -.2F, .5F, 1.2F, 0x00A000B0, 0x00030004);
        BlockModelRendering.renderModel(renderer, pose.last(), actual, state, model,
                -.2F, .5F, 1.2F, 0x00A000B0, 0x00030004, data, layer);
        assertEquals(expected.values, actual.values);
        assertFalse(actual.values.isEmpty());
        assertEquals(7, model.calls.size());
        long first = RandomSource.create(42).nextLong();
        for (var call : model.calls) {
            assertSame(state, call.state); assertSame(data, call.data); assertSame(layer, call.layer);
            assertEquals(first, call.randomValue);
        }
        assertEquals(java.util.Arrays.asList(Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST, null), model.calls.stream().map(Call::face).toList());
    }

    @Test void nativeStandaloneGeometryIsNotFilteredByTheUnrelatedStateOrRequestedLayer() {
        var dynamic = new TestModel();
        BakedModel nativeModel = new net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel(dynamic) {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) { return dynamic.quads(face); }
        };
        var result = BakedModels.getQuads(nativeModel, Blocks.STONE.defaultBlockState(), null, RandomSource.create(),
                ModelData.EMPTY, RenderType.translucent());
        assertEquals(2, result.size());
    }

    @Test void singleBlockRendersEveryModelLayerWithCallerDataAndItsOwnDispatcherColors() {
        var model = new TestModel();
        var colors = new BlockColors();
        colors.register((state, view, pos, index) -> 0x4080C0, Blocks.STONE);
        var animated = new AnimatedRenderer();
        var dispatcher = new Dispatcher(model, colors, animated);
        var data = ModelData.of(NUMBER, 94);
        var requestedBuffers = new ArrayList<RenderType>();
        var vertices = new Vertices();
        MultiBufferSource buffers = layer -> { requestedBuffers.add(layer); return vertices; };
        BlockModelRendering.renderSingleBlock(dispatcher, Blocks.STONE.defaultBlockState(), new PoseStack(), buffers,
                0x00A000B0, 0x00030004, data, null);
        assertEquals(List.of(Sheets.cutoutBlockSheet(), Sheets.cutoutBlockSheet()), requestedBuffers);
        assertSame(data, model.layerQueryData);
        assertEquals(RandomSource.create(42).nextLong(), model.layerQueryRandom);
        assertEquals(14, model.calls.size());
        assertEquals(List.of(RenderType.solid(), RenderType.cutout()), model.calls.stream().map(Call::layer).distinct().toList());
        assertTrue(model.calls.stream().allMatch(call -> call.data == data));
        assertEquals(0, dispatcher.nativeCalls);
        // Only the tinted quad receives the dispatcher's color. Untinted vertices keep their native white.
        assertTrue(vertices.values.stream().anyMatch(quad -> quad.red == 64 / 255F && quad.green == 128 / 255F && quad.blue == 192 / 255F));
        assertTrue(vertices.values.stream().anyMatch(quad -> quad.red == 1 && quad.green == 1 && quad.blue == 1));
        assertTrue(vertices.values.stream().allMatch(quad -> quad.light == 0x00A000B0 && quad.overlay == 0x00030004 && quad.alpha == 1));
    }

    @Test void previewBufferOverrideDoesNotReplaceTheModelQuadQueryLayer() {
        var model = new TestModel();
        var dispatcher = new Dispatcher(model, new BlockColors(), new AnimatedRenderer());
        var override = RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        var selected = new ArrayList<RenderType>();
        BlockModelRendering.renderSingleBlock(dispatcher, Blocks.STONE.defaultBlockState(), new PoseStack(),
                layer -> { selected.add(layer); return new Vertices(); }, 0, 0, ModelData.EMPTY, override);
        assertEquals(List.of(override, override), selected);
        assertEquals(List.of(RenderType.solid(), RenderType.cutout()), model.calls.stream().map(Call::layer).distinct().toList());
    }

    @Test void invisibleAndAnimatedShapesKeepTheNativeDispatcherAndBuiltinItemRenderPath() {
        var animated = new AnimatedRenderer();
        var dispatcher = new Dispatcher(new TestModel(), new BlockColors(), animated);
        var pose = new PoseStack(); MultiBufferSource buffers = layer -> { fail("invisible/animated path requested static model buffer"); return null; };
        BlockModelRendering.renderSingleBlock(dispatcher, Blocks.AIR.defaultBlockState(), pose, buffers,
                31, 42, ModelData.of(NUMBER, 5), null);
        assertEquals(1, dispatcher.nativeCalls); assertEquals(0, animated.calls);
        BlockModelRendering.renderSingleBlock(dispatcher, Blocks.CHEST.defaultBlockState(), pose, buffers,
                31, 42, ModelData.of(NUMBER, 5), null);
        assertEquals(2, dispatcher.nativeCalls); assertEquals(1, animated.calls);
        assertTrue(animated.stack.is(net.minecraft.world.item.Items.CHEST));
        assertSame(ItemDisplayContext.NONE, animated.context); assertSame(pose, animated.pose); assertSame(buffers, animated.buffers);
        assertEquals(31, animated.light); assertEquals(42, animated.overlay);
    }

    @Test void tessellationUsesExplicitDataAndFrApiAoCullAndLightingMetadataWithoutWorldDataReplacement() {
        var model = new TestModel();
        var data = ModelData.of(NUMBER, 106);
        var view = (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, args) -> { throw new AssertionError("Unexpected world/BE lookup: " + method); });
        var pose = new PoseStack(); var random = RandomSource.create(918);
        var context = new Emission();
        var renderer = new ModelBlockRenderer(new BlockColors()) {
            @Override public void tesselateBlock(BlockAndTintGetter world, BakedModel bound, BlockState state, BlockPos pos,
                    PoseStack matrices, VertexConsumer vertices, boolean checkSides, RandomSource rng, long seed, int overlay) {
                assertSame(view, world); assertSame(pose, matrices); assertSame(random, rng);
                assertEquals(new BlockPos(3, 4, 5), pos); assertTrue(checkSides); assertEquals(123456L, seed); assertEquals(97, overlay);
                assertFalse(bound.isVanillaAdapter(), "native FRAPI tessellation must handle per-face AO");
                bound.emitBlockQuads(world, state, pos, () -> { rng.setSeed(seed); return rng; }, context);
            }
        };
        BlockModelRendering.tesselateBlock(renderer, view, model, Blocks.STONE.defaultBlockState(), new BlockPos(3, 4, 5),
                pose, new Vertices(), true, random, 123456L, 97, data, null);
        assertEquals(7, model.calls.size());
        assertTrue(model.calls.stream().allMatch(call -> call.data == data && call.layer == null));
        assertEquals(0, model.modelDataQueries, "the caller already prepared data, as in native Neo tessellation");
        var output = new ArrayList<String>();
        context.builder.build().forEach(quad -> output.add(quad.cullFace() + ":" + quad.material().blendMode()
                + ":" + quad.material().ambientOcclusion() + ":" + quad.material().disableDiffuse() + ":" + quad.lightmap(0)));
        assertEquals(List.of("null:DEFAULT:TRUE:false:10485936", "null:DEFAULT:FALSE:true:10485936"), output);
    }

    @Test void blockEntityDataConsumerUsesTheRealProviderAndDefaultsOnlyForAnOrdinaryBlockEntity() {
        var data = ModelData.of(NUMBER, 512);
        class TemplateFixture extends ChestBlockEntity implements ModelDataProvider {
            TemplateFixture() { super(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
            @Override public ModelData getModelData() { return data; }
        }
        assertSame(data, BakedModels.getModelData(new TemplateFixture()));
        assertSame(ModelData.EMPTY, BakedModels.getModelData(new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState())));
    }

    @Test void nativeFrApiWorldModelKeepsItsOwnMeshEmissionInsteadOfBeingFlattenedToQuads() {
        var nativeEmissions = new ArrayList<String>();
        BakedModel nativeModel = new net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel(new TestModel()) {
            @Override public boolean isVanillaAdapter() { return false; }
            @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
                throw new AssertionError("lost native FRAPI mesh metadata");
            }
            @Override public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                    Supplier<RandomSource> random, RenderContext context) {
                nativeEmissions.add("mesh");
                var material = IndigoRenderer.INSTANCE.materialFinder().emissive(true).disableDiffuse(true)
                        .ambientOcclusion(net.fabricmc.fabric.api.util.TriState.FALSE).find();
                context.getEmitter().fromVanilla(quad(0, false, false), material, Direction.UP).emit();
            }
        };
        var context = new Emission();
        var renderer = new ModelBlockRenderer(new BlockColors()) {
            @Override public void tesselateBlock(BlockAndTintGetter view, BakedModel bound, BlockState state, BlockPos pos,
                    PoseStack matrices, VertexConsumer vertices, boolean checkSides, RandomSource random, long seed, int overlay) {
                bound.emitBlockQuads(view, state, pos, () -> random, context);
            }
        };
        BlockModelRendering.tesselateBlock(renderer, null, nativeModel, Blocks.STONE.defaultBlockState(), BlockPos.ZERO,
                new PoseStack(), new Vertices(), false, RandomSource.create(), 42, 0, ModelData.EMPTY, null);
        assertEquals(List.of("mesh"), nativeEmissions);
        context.builder.build().forEach(quad -> {
            assertTrue(quad.material().emissive()); assertTrue(quad.material().disableDiffuse());
            assertEquals(net.fabricmc.fabric.api.util.TriState.FALSE, quad.material().ambientOcclusion());
            assertSame(Direction.UP, quad.cullFace());
        });
    }

    @Test void dispatcherColorAccessorNamesTheActualNativeField() throws Exception {
        var method = BlockRenderDispatcherColorsAccessor.class.getDeclaredMethod("stardewcraft$blockColors");
        assertEquals("blockColors", method.getAnnotation(org.spongepowered.asm.mixin.gen.Accessor.class).value());
        assertSame(BlockColors.class, BlockRenderDispatcher.class.getDeclaredField("blockColors").getType());
    }

    private record Call(BlockState state, Direction face, ModelData data, RenderType layer, long randomValue) {}

    private static class TestModel implements IDynamicBakedModel {
        final List<Call> calls = new ArrayList<>();
        final List<BakedQuad> quads = List.of(quad(0, true, true), quad(-1, false, false));
        ModelData layerQueryData; long layerQueryRandom; int modelDataQueries;
        List<BakedQuad> quads(Direction face) { return face == null ? quads : List.of(); }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
            calls.add(new Call(state, side, data, layer, random.nextLong())); return quads(side);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) { throw new AssertionError("lost explicit model data"); }
        @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData data) { modelDataQueries++; throw new AssertionError("replaced prepared data"); }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            layerQueryData = data; layerQueryRandom = random.nextLong(); return ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout());
        }
        @Override public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType layer) { return TriState.TRUE; }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static BakedQuad quad(int tint, boolean shade, boolean ao) {
        int[] data = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            data[offset] = Float.floatToIntBits(vertex == 1 || vertex == 2 ? 1 : 0);
            data[offset + 1] = Float.floatToIntBits(1);
            data[offset + 2] = Float.floatToIntBits(vertex >= 2 ? 1 : 0);
            data[offset + 3] = -1;
            data[offset + 4] = Float.floatToIntBits(vertex / 4F);
            data[offset + 5] = Float.floatToIntBits(vertex / 8F);
            data[offset + 6] = 0x00A000B0;
        }
        return new ModelQuad(data, tint, Direction.UP, null, shade, ao);
    }

    private static final class Dispatcher extends BlockRenderDispatcher implements BlockRenderDispatcherColorsAccessor {
        final BakedModel model; final BlockColors colors; int nativeCalls;
        Dispatcher(BakedModel model, BlockColors colors, AnimatedRenderer renderer) { super(null, renderer, colors); this.model = model; this.colors = colors; }
        @Override public BakedModel getBlockModel(BlockState state) { return model; }
        @Override public BlockColors stardewcraft$blockColors() { return colors; }
        @Override public void renderSingleBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            nativeCalls++; super.renderSingleBlock(state, pose, buffers, light, overlay);
        }
    }

    private static final class AnimatedRenderer extends BlockEntityWithoutLevelRenderer {
        int calls; ItemStack stack; ItemDisplayContext context; PoseStack pose; MultiBufferSource buffers; int light, overlay;
        AnimatedRenderer() { super(null, null); }
        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            calls++; this.stack = stack; this.context = context; this.pose = pose; this.buffers = buffers; this.light = light; this.overlay = overlay;
        }
    }

    private record Bulk(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {}

    private static final class Vertices implements VertexConsumer {
        final List<Bulk> values = new ArrayList<>();
        @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
            values.add(new Bulk(pose, quad, red, green, blue, alpha, light, overlay));
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { throw new AssertionError("expected native quad output"); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new AssertionError("expected native quad output"); }
        @Override public VertexConsumer setUv(float u, float v) { throw new AssertionError("expected native quad output"); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new AssertionError("expected native quad output"); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new AssertionError("expected native quad output"); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError("expected native quad output"); }
    }

    private static final class Emission implements RenderContext {
        final MeshBuilder builder = IndigoRenderer.INSTANCE.meshBuilder();
        @Override public QuadEmitter getEmitter() { return builder.getEmitter(); }
        @Override public void pushTransform(QuadTransform transform) { throw new AssertionError("Unexpected transform"); }
        @Override public void popTransform() { throw new AssertionError("Unexpected transform"); }
        @Override public ItemDisplayContext itemTransformationMode() { throw new AssertionError("not item rendering"); }
        @Override public BakedModelConsumer bakedModelConsumer() { throw new AssertionError("Deprecated fallback"); }
    }
}
