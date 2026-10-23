package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests of the production draw scope and Minecraft's actual AO renderer/cache.
 * Only AO selection (normally a Minecraft option) and GPU vertex upload are replaced;
 * AO calculations, light/shade cache lookups and spatial partitioning remain production code.
 * This does not measure game FPS or validate graphics-driver/shader appearance.
 */
class SpatialBlockLightingCacheTest {
    private static final BlockPos POSITION = new BlockPos(17, 64, -11);
    private static BlockState sampleState;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            Block sample = Registry.register(registry,
                    ResourceLocation.fromNamespaceAndPath("machine_rendering_test", "light_sample"),
                    new Block(Block.Properties.of().noOcclusion()) {
                        @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
                            CountingWorld world = (CountingWorld) level;
                            world.shadeReads++;
                            return world.shadeBase + Math.floorMod(pos.getX() + pos.getZ(), 3) * 0.03F;
                        }
                    });
            sample.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
            sampleState = sample.defaultBlockState();
        } finally {
            registry.freeze();
        }
    }

    @BeforeEach void clearBeforeTest() { ModelBlockRenderer.clearCache(); }
    @AfterEach void clearAfterTest() { ModelBlockRenderer.clearCache(); }

    @Test void singleCellNativeAoReadsDecreaseWithoutChangingOutput() throws Exception {
        compareCachedAndUncached(cube(1), 1);
    }

    @Test void multipleCellsShareNativeAoReadsAndPreserveCallerPose() throws Exception {
        compareCachedAndUncached(cube(2), 2);
    }

    private static void compareCachedAndUncached(BakedModel model, int expectedCells) throws Exception {
        CountingWorld uncachedWorld = new CountingWorld(4, 0.4F);
        AoRenderer uncached = new AoRenderer(true, 0);
        Output baseline = draw(uncached, uncachedWorld, model, callerPose());
        CountingWorld cachedWorld = new CountingWorld(4, 0.4F);
        AoRenderer cached = new AoRenderer(false, 0);
        Output actual = draw(cached, cachedWorld, model, callerPose());
        assertEquals(expectedCells, cached.cells);
        assertTrue(actual.quads.size() >= 48, "The real AO renderer must emit the fixture's faces");
        assertOutputEquals(baseline, actual);
        assertTrue(cachedWorld.lightReads * 3 < uncachedWorld.lightReads,
                "Native light cache must reduce real world queries: " + cachedWorld.lightReads + " / " + uncachedWorld.lightReads);
        assertTrue(cachedWorld.shadeReads * 3 < uncachedWorld.shadeReads,
                "Native shade cache must reduce actual Block.getShadeBrightness calls: " + cachedWorld.shadeReads + " / " + uncachedWorld.shadeReads);
        assertCacheCleared();
        System.out.printf("SPATIAL-AO %d cells: light reads %d -> %d; shade reads %d -> %d; identical AO output%n",
                expectedCells, uncachedWorld.lightReads, cachedWorld.lightReads, uncachedWorld.shadeReads, cachedWorld.shadeReads);
    }

    @Test void nextDrawResamplesChangedLightingAtTheSamePosition() throws Exception {
        BakedModel model = cube(1);
        CountingWorld world = new CountingWorld(2, 0.25F);
        Output first = draw(new AoRenderer(false, 0), world, model, callerPose());
        assertCacheCleared();
        world.lightBase = 9;
        world.shadeBase = 0.8F;
        world.resetCounts();
        Output changed = draw(new AoRenderer(false, 0), world, model, callerPose());
        assertTrue(world.lightReads > 0 && world.shadeReads > 0, "A later draw must query the changed world");
        assertFalse(java.util.Arrays.equals(first.quads.getFirst().lights, changed.quads.getFirst().lights));
        assertFalse(java.util.Arrays.equals(first.quads.getFirst().brightness, changed.quads.getFirst().brightness));
        assertOutputEquals(draw(new AoRenderer(true, 0), world, model, callerPose()), changed);
        assertCacheCleared();
    }

    @Test void anotherWorldAtTheSamePositionCannotInheritLightOrShade() throws Exception {
        BakedModel model = cube(2); // Same model/state also reuses the production geometry cache.
        Output first = draw(new AoRenderer(false, 0), new CountingWorld(1, 0.2F), model, callerPose());
        CountingWorld secondWorld = new CountingWorld(10, 0.75F);
        Output second = draw(new AoRenderer(false, 0), secondWorld, model, callerPose());
        assertTrue(secondWorld.lightReads > 0 && secondWorld.shadeReads > 0);
        assertFalse(java.util.Arrays.equals(first.quads.getFirst().lights, second.quads.getFirst().lights));
        assertOutputEquals(draw(new AoRenderer(true, 0), secondWorld, model, callerPose()), second);
        assertCacheCleared();
    }

    @Test void exceptionsClearNativeCachesAndBalanceSingleAndMultipleCellPoses() throws Exception {
        for (int width : new int[]{1, 2}) {
            BakedModel model = cube(width);
            PoseStack pose = callerPose();
            PoseStack.Pose caller = pose.last();
            float[] matrix = caller.pose().get(new float[16]);
            AoRenderer failing = new AoRenderer(false, width);
            assertThrows(DrawFailure.class, () -> draw(failing, new CountingWorld(1, 0.2F), model, pose));
            assertEquals(width, failing.cells, "Failure occurs after real AO has populated the cache");
            assertSame(caller, pose.last(), "A failed fragment must pop its own pose");
            assertArrayEquals(matrix, pose.last().pose().get(new float[16]));
            pose.popPose();
            assertTrue(pose.clear(), "The caller's original stack depth must survive the exception");
            assertCacheCleared();
            CountingWorld next = new CountingWorld(11, 0.85F);
            Output recovered = draw(new AoRenderer(false, 0), next, model, callerPose());
            assertOutputEquals(draw(new AoRenderer(true, 0), next, model, callerPose()), recovered);
            assertCacheCleared();
        }
    }

    @Test void emptyModelsDoNotEnterAnAoCacheScope() throws Exception {
        AoRenderer renderer = new AoRenderer(false, 0);
        CountingWorld world = new CountingWorld(4, 0.4F);
        assertTrue(draw(renderer, world, new Model(List.of()), callerPose()).quads.isEmpty());
        assertEquals(0, renderer.cells);
        assertEquals(0, world.lightReads);
        assertEquals(0, world.shadeReads);
        assertCacheCleared();
    }

    private static PoseStack callerPose() {
        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(0.2, 0.4, 0.8);
        pose.mulPose(new Quaternionf().rotationY(0.45F));
        pose.scale(0.8F, 1.2F, 0.7F);
        return pose;
    }

    private static Output draw(AoRenderer renderer, CountingWorld world, BakedModel model, PoseStack pose) {
        PoseStack.Pose caller = pose.last();
        float[] matrix = caller.pose().get(new float[16]);
        Output output = new Output();
        SpatialBlockModelRenderer.render(renderer, world, model, sampleState, POSITION, pose, output,
                false, RandomSource.create(0), 0, 0);
        assertSame(caller, pose.last());
        assertArrayEquals(matrix, pose.last().pose().get(new float[16]));
        pose.popPose();
        assertTrue(pose.clear());
        return output;
    }

    private static void assertOutputEquals(Output expected, Output actual) {
        assertEquals(expected.quads.size(), actual.quads.size());
        for (int i = 0; i < expected.quads.size(); i++) {
            QuadOutput a = expected.quads.get(i), b = actual.quads.get(i);
            assertEquals(a.face, b.face);
            assertArrayEquals(a.vertices, b.vertices, "Geometry/UV/vertex attributes changed at quad " + i);
            assertArrayEquals(a.matrix, b.matrix);
            assertArrayEquals(a.brightness, b.brightness, "Native AO brightness changed at quad " + i);
            assertArrayEquals(a.lights, b.lights, "Native AO lightmap changed at quad " + i);
            assertArrayEquals(a.color, b.color);
            assertEquals(a.overlay, b.overlay);
            assertEquals(a.readColor, b.readColor);
        }
    }

    private static void assertCacheCleared() throws Exception {
        Field threadLocal = ModelBlockRenderer.class.getDeclaredField("CACHE");
        threadLocal.setAccessible(true);
        Object cache = ((ThreadLocal<?>) threadLocal.get(null)).get();
        Field enabled = cache.getClass().getDeclaredField("enabled");
        enabled.setAccessible(true);
        assertFalse(enabled.getBoolean(cache), "Native cache must be disabled outside a draw");
        for (String name : List.of("colorCache", "brightnessCache")) {
            Field entries = cache.getClass().getDeclaredField(name);
            entries.setAccessible(true);
            assertTrue(((Map<?, ?>) entries.get(cache)).isEmpty(), "Native " + name + " retained a prior draw's samples");
        }
    }

    private static BakedModel cube(int width) {
        List<BakedQuad> quads = new ArrayList<>();
        for (int repeat = 0; repeat < 8; repeat++) for (Direction direction : Direction.values()) {
            int[] vertices = new int[32];
            for (int vertex = 0; vertex < 4; vertex++) {
                FaceInfo.VertexInfo corner = FaceInfo.fromFacing(direction).getVertexInfo(vertex);
                int index = vertex * 8;
                vertices[index] = Float.floatToRawIntBits(corner.xFace == FaceInfo.Constants.MIN_X ? 0 : width);
                vertices[index + 1] = Float.floatToRawIntBits(corner.yFace == FaceInfo.Constants.MIN_Y ? 0 : 1);
                vertices[index + 2] = Float.floatToRawIntBits(corner.zFace == FaceInfo.Constants.MIN_Z ? 0 : 1);
                vertices[index + 3] = -1;
                vertices[index + 4] = Float.floatToRawIntBits(vertex < 2 ? 0 : 1);
                vertices[index + 5] = Float.floatToRawIntBits(vertex % 3 == 0 ? 0 : 1);
            }
            // Untinted AO geometry does not sample a texture in this CPU output boundary.
            quads.add(new BakedQuad(vertices, -1, direction, null, true, true));
        }
        return new Model(List.copyOf(quads));
    }

    private record Model(List<BakedQuad> quads) implements BakedModel {
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? quads : List.of();
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static final class AoRenderer extends ModelBlockRenderer {
        private final boolean uncached;
        private final int failAtCell;
        private int cells;
        AoRenderer(boolean uncached, int failAtCell) {
            super(new BlockColors());
            this.uncached = uncached;
            this.failAtCell = failAtCell;
        }
        @Override public void tesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
                PoseStack pose, VertexConsumer vertices, boolean checkSides, RandomSource random, long seed,
                int overlay, ModelData data, RenderType type) {
            cells++;
            if (uncached) ModelBlockRenderer.clearCache();
            // Bypass only Minecraft.useAmbientOcclusion's client option; use the full native AO path.
            super.tesselateWithAO(level, model, state, pos, pose, vertices, checkSides, random, seed, overlay, data, type);
            if (cells == failAtCell) throw new DrawFailure();
        }
    }

    private static final class DrawFailure extends RuntimeException {}

    private static final class CountingWorld implements BlockAndTintGetter {
        int lightBase, lightReads, shadeReads;
        float shadeBase;
        CountingWorld(int lightBase, float shadeBase) { this.lightBase = lightBase; this.shadeBase = shadeBase; }
        void resetCounts() { lightReads = shadeReads = 0; }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) {
            lightReads++;
            return Math.min(15, lightBase + Math.floorMod(pos.getX() + 2 * pos.getY() + pos.getZ(), 3));
        }
        @Override public float getShade(Direction direction, boolean shade) {
            return shade ? switch (direction) { case DOWN -> 0.5F; case UP -> 1; case NORTH, SOUTH -> 0.8F; default -> 0.6F; } : 1;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return sampleState; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { throw new AssertionError("Fixture is untinted"); }
        @Override public LevelLightEngine getLightEngine() { throw new AssertionError("Count getBrightness directly"); }
    }

    private record QuadOutput(Direction face, int[] vertices, float[] matrix, float[] brightness,
                              int[] lights, float[] color, int overlay, boolean readColor) {}

    private static final class Output implements VertexConsumer {
        final List<QuadOutput> quads = new ArrayList<>();
        @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                float red, float green, float blue, float alpha, int[] lights, int overlay, boolean readColor) {
            quads.add(new QuadOutput(quad.getDirection(), quad.getVertices().clone(), pose.pose().get(new float[16]),
                    brightness.clone(), lights.clone(), new float[]{red, green, blue, alpha}, overlay, readColor));
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { throw unexpected(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw unexpected(); }
        @Override public VertexConsumer setUv(float u, float v) { throw unexpected(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw unexpected(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw unexpected(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw unexpected(); }
        private AssertionError unexpected() { return new AssertionError("Capture native AO output before GPU upload"); }
    }
}
