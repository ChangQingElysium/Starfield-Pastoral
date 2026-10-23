package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.geometry.BlockGeometryBakingContext;
import io.github.fabricators_of_create.porting_lib.models.geometry.IUnbakedGeometry;
import io.github.fabricators_of_create.porting_lib.models.geometry.extensions.BlockModelExtension;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrdinaryModelRenderTypesTest {
    private static Gson gson;
    private static TextureAtlasSprite sprite;
    private static final Supplier<RandomSource> RANDOM = () -> RandomSource.create(42);

    @BeforeAll static void setup() throws Exception {
        Method setup = ModelTransformationDeserializerTest.class.getDeclaredMethod("setup");
        setup.setAccessible(true); setup.invoke(null);
        Class<?> fixture = Class.forName(ModelTransformationDeserializerTest.class.getName() + "$FixtureDeserializer");
        var constructor = fixture.getDeclaredConstructor(); constructor.setAccessible(true);
        Method makeGson = ModelTransformationDeserializerTest.class.getDeclaredMethod("modelGson", fixture, boolean.class);
        makeGson.setAccessible(true); gson = (Gson) makeGson.invoke(null, constructor.newInstance(), true);
        Method makeSprite = CompositeGeometryTest.class.getDeclaredMethod("sprite");
        makeSprite.setAccessible(true); sprite = (TextureAtlasSprite) makeSprite.invoke(null);
        if (RendererAccess.INSTANCE.getRenderer() == null) RendererAccess.INSTANCE.registerRenderer(IndigoRenderer.INSTANCE);
    }

    @Test void realCircleAndFootprintJsonNowEmitTranslucentFrApiGeometryWithTheirNativeVerticesAndShade() throws Exception {
        for (String name : List.of("magic_warp_circle", "shadow_footprint")) {
            BlockModel model = gson.fromJson(Files.readString(modelsRoot().resolve("block/" + name + ".json")), BlockModel.class);
            BakedModel nativeModel = model.bake(null, model, m -> sprite, BlockModelRotation.X0_Y0, true);
            assertEquals(List.of(RenderType.solid()), BakedModels.getRenderTypes(nativeModel,
                    Blocks.STONE.defaultBlockState(), RANDOM.get(), ModelData.EMPTY).asList(), "Old ordinary path ignores parsed JSON hint");
            BakedModel hinted = OrdinaryModelRenderTypes.apply(model, nativeModel);
            assertFalse(hinted.isVanillaAdapter());
            assertEquals(List.of(RenderType.translucent()), BakedModels.getRenderTypes(hinted,
                    Blocks.STONE.defaultBlockState(), RANDOM.get(), ModelData.EMPTY).asList());
            Output output = new Output(ItemDisplayContext.NONE);
            hinted.emitBlockQuads(view(), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RANDOM, output.context);
            List<BakedQuad> raw = nativeModel.getQuads(null, null, RANDOM.get());
            List<float[]> positions = new ArrayList<>();
            output.mesh().forEach(quad -> {
                assertEquals(BlendMode.TRANSLUCENT, quad.material().blendMode());
                assertTrue(quad.material().disableDiffuse());
                assertEquals(net.fabricmc.fabric.api.util.TriState.FALSE, quad.material().ambientOcclusion());
                assertNull(quad.cullFace());
                for (int i = 0; i < 4; i++) positions.add(new float[]{quad.x(i), quad.y(i), quad.z(i)});
            });
            assertEquals(1, raw.size()); assertEquals(4, positions.size());
            for (int i = 0; i < 4; i++) {
                int[] vertices = raw.getFirst().getVertices();
                assertArrayEquals(new float[]{Float.intBitsToFloat(vertices[i * 8]), Float.intBitsToFloat(vertices[i * 8 + 1]),
                        Float.intBitsToFloat(vertices[i * 8 + 2])}, positions.get(i));
            }
        }
    }

    @Test void absentUnknownAndCustomGeometryHintsKeepTheirExistingNativeProducer() {
        BakedModel original = nativeModel(); ElementModel model = new ElementModel();
        assertSame(original, OrdinaryModelRenderTypes.apply(model, original));
        model.custom.setRenderTypeHint(ResourceLocation.fromNamespaceAndPath("unknown", "not_registered"));
        assertSame(original, OrdinaryModelRenderTypes.apply(model, original));
        model.custom.setRenderTypeHint(ResourceLocation.withDefaultNamespace("translucent"));
        model.custom.setCustomGeometry(new CustomGeometry());
        assertSame(original, OrdinaryModelRenderTypes.apply(model, original));
        for (BlockModel marker : List.of(ModelBakery.BLOCK_ENTITY_MARKER, ModelBakery.GENERATION_MARKER)) {
            ElementModel special = new ElementModel(); special.parent = marker;
            special.custom.setRenderTypeHint(ResourceLocation.withDefaultNamespace("translucent"));
            assertSame(original, OrdinaryModelRenderTypes.apply(special, original));
        }
    }

    @Test void childHintOverridesInheritedHintWhileMissingChildHintInheritsItsParent() {
        ElementModel parent = new ElementModel(); parent.custom.setRenderTypeHint(ResourceLocation.withDefaultNamespace("cutout"));
        ElementModel child = new ElementModel(); child.parent = parent;
        BakedModel original = nativeModel();
        assertEquals(List.of(RenderType.cutout()), BakedModels.getRenderTypes(OrdinaryModelRenderTypes.apply(child, original),
                Blocks.STONE.defaultBlockState(), RANDOM.get(), ModelData.EMPTY).asList());
        child.custom.setRenderTypeHint(ResourceLocation.withDefaultNamespace("translucent"));
        assertEquals(List.of(RenderType.translucent()), BakedModels.getRenderTypes(OrdinaryModelRenderTypes.apply(child, original),
                Blocks.STONE.defaultBlockState(), RANDOM.get(), ModelData.EMPTY).asList());
    }

    @Test void namedBlockLayersBecomeActualFrApiMaterialsRatherThanOnlyGetterValues() {
        for (String name : List.of("solid", "cutout", "cutout_mipped", "translucent")) {
            ElementModel model = hintedModel(name); BakedModel wrapped = OrdinaryModelRenderTypes.apply(model, nativeModel());
            var context = model.custom; var group = context.getRenderType(context.getRenderTypeHint());
            Output output = new Output(ItemDisplayContext.NONE);
            wrapped.emitBlockQuads(view(), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RANDOM, output.context);
            int[] count = {0}; output.mesh().forEach(quad -> {
                assertEquals(BlendMode.fromRenderLayer(group.block()), quad.material().blendMode());
                count[0]++;
            }); assertEquals(1, count[0]);
        }
    }

    @Test void itemGuiAndNonDirectBufferPathsRetainTheNamedEntityRenderTypesAndEmitOnce() {
        ElementModel model = hintedModel("translucent"); BakedModel original = nativeModel();
        BakedModel wrapped = OrdinaryModelRenderTypes.apply(model, original);
        var group = model.custom.getRenderType(model.custom.getRenderTypeHint());
        assertEquals(List.of(group.entity()), BakedModels.getRenderTypes(wrapped, ItemStack.EMPTY, true));
        assertEquals(List.of(group.entityFabulous()), BakedModels.getRenderTypes(wrapped, ItemStack.EMPTY, false));
        for (ItemDisplayContext mode : List.of(ItemDisplayContext.GUI, ItemDisplayContext.GROUND)) {
            Output output = new Output(mode);
            wrapped.emitItemQuads(new ItemStack(Items.GLASS), RANDOM, output.context);
            int[] count = {0}; output.mesh().forEach(quad -> { assertEquals(BlendMode.TRANSLUCENT, quad.material().blendMode()); count[0]++; });
            assertEquals(1, count[0], mode.toString());
        }
        assertSame(original.getTransforms(), wrapped.getTransforms()); assertSame(original.getOverrides(), wrapped.getOverrides());
        assertSame(original.getParticleIcon(), wrapped.getParticleIcon()); assertEquals(original.isGui3d(), wrapped.isGui3d());
        assertEquals(original.usesBlockLight(), wrapped.usesBlockLight()); assertEquals(original.isCustomRenderer(), wrapped.isCustomRenderer());
    }

    @Test void enclosingNonDynamicWrapperKeepsItsOwnQuadEditsInBlockAndItemEmission() {
        BakedModel hinted = OrdinaryModelRenderTypes.apply(hintedModel("translucent"), nativeModel());
        BakedQuad original = hinted.getQuads(null, null, RANDOM.get()).getFirst();
        int[] moved = original.getVertices().clone();
        for (int i = 0; i < 4; i++) moved[i * 8] = Float.floatToRawIntBits(Float.intBitsToFloat(moved[i * 8]) + 2);
        BakedQuad replacement = new BakedQuad(moved, original.getTintIndex(), original.getDirection(), sprite, original.isShade());
        // RecessedOverlay, FloatingBody and spatial CellModel use this actual wrapper contract.
        BakedModel wrapper = new BakedModelWrapper<>(hinted) {
            @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
                return side == null ? List.of(replacement) : List.of();
            }
            @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
                return getQuads(state, side, random);
            }
        };
        for (boolean item : List.of(false, true)) {
            Output output = new Output(item ? ItemDisplayContext.GUI : ItemDisplayContext.NONE);
            if (item) wrapper.emitItemQuads(new ItemStack(Items.STONE), RANDOM, output.context);
            else wrapper.emitBlockQuads(view(), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RANDOM, output.context);
            int[] count = {0}; output.mesh().forEach(quad -> {
                assertEquals(BlendMode.TRANSLUCENT, quad.material().blendMode());
                for (int i = 0; i < 4; i++) assertTrue(quad.x(i) >= 2F);
                count[0]++;
            }); assertEquals(1, count[0]);
        }
    }

    @Test void unrelatedNativeFrApiMeshesStillForwardWithoutFlattening() {
        List<String> calls = new ArrayList<>();
        BakedModel nativeMesh = (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isVanillaAdapter" -> false;
                    case "emitBlockQuads", "emitItemQuads" -> { calls.add(method.getName()); yield null; }
                    default -> throw new AssertionError("Native mesh must not be queried as vanilla: " + method);
                });
        BakedModel wrapper = new BakedModelWrapper<>(nativeMesh);
        wrapper.emitBlockQuads(view(), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RANDOM, null);
        wrapper.emitItemQuads(ItemStack.EMPTY, RANDOM, null);
        assertEquals(List.of("emitBlockQuads", "emitItemQuads"), calls);
    }

    @Test void hintMixinOnlyLoadsOnThePhysicalClient() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("stardewcraft.fabric.mixins.json")) {
            var config = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
            String name = "client.model.mixin.OrdinaryBlockModelRenderTypeMixin";
            assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(v -> v.getAsString().equals(name)));
            assertTrue(config.getAsJsonArray("mixins").asList().stream().noneMatch(v -> v.getAsString().equals(name)));
        }
    }

    private static ElementModel hintedModel(String name) {
        ElementModel model = new ElementModel(); model.custom.setRenderTypeHint(ResourceLocation.withDefaultNamespace(name)); return model;
    }
    private static BakedModel nativeModel() {
        BlockModel model = gson.fromJson("{textures:{particle:'minecraft:block/stone',stone:'minecraft:block/stone'},elements:[{from:[0,0,0],to:[16,16,16],faces:{up:{texture:'#stone'}}}]}", BlockModel.class);
        return model.bake(null, model, m -> sprite, BlockModelRotation.X0_Y0, true);
    }
    private static BlockAndTintGetter view() {
        return (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class, FabricBlockView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockEntityRenderData")) return ModelData.EMPTY;
                    throw new AssertionError(method);
                });
    }
    private static Path modelsRoot() {
        for (Path root : List.of(Path.of("../src/main/resources/assets/stardewcraft/models"), Path.of("src/main/resources/assets/stardewcraft/models")))
            if (Files.isDirectory(root)) return root;
        throw new AssertionError("Tracked model fixtures are required");
    }
    private static final class ElementModel extends BlockModel implements BlockModelExtension {
        final BlockGeometryBakingContext custom = new BlockGeometryBakingContext(this);
        ElementModel() { super(null, List.of(), Map.of(), true, GuiLight.FRONT, ItemTransforms.NO_TRANSFORMS, List.of()); }
        @Override public BlockGeometryBakingContext port_lib$getCustomData() { return custom; }
    }
    private static final class CustomGeometry implements IUnbakedGeometry<CustomGeometry> {
        @Override public BakedModel bake(io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryBakingContext context,
                net.minecraft.client.resources.model.ModelBaker baker, java.util.function.Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> textures,
                net.minecraft.client.resources.model.ModelState state, ItemOverrides overrides) { throw new AssertionError(); }
    }
    private static final class Output {
        final net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder builder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        final RenderContext context;
        Output(ItemDisplayContext mode) {
            context = (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(), new Class<?>[]{RenderContext.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getEmitter" -> builder.getEmitter();
                        case "itemTransformationMode" -> mode;
                        default -> throw new AssertionError(method);
                    });
        }
        Mesh mesh() { return builder.build(); }
    }
}
