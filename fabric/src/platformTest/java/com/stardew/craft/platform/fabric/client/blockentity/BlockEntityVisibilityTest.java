package com.stardew.craft.platform.fabric.client.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityVisibilityTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void defaultBoundsMatchTheAnchorUnitCubeAndNativeDistanceDefaultsRemainIntact() {
        var entity = new TestEntity(new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        BlockEntityRenderer<TestEntity> renderer = (be, tick, pose, buffers, light, overlay) -> {};
        assertEquals(new AABB(entity.getBlockPos()), renderer.getRenderBoundingBox(entity));
        assertFalse(renderer.shouldRenderOffScreen(entity)); assertEquals(64, renderer.getViewDistance());
        assertTrue(renderer.shouldRender(entity, Vec3.atCenterOf(entity.getBlockPos()).add(63.9, 0, 0)));
        assertFalse(renderer.shouldRender(entity, Vec3.atCenterOf(entity.getBlockPos()).add(64, 0, 0)));
    }

    @Test void anExtendedModelsVisibleEdgeSurvivesCullingWhenItsAnchorIsOutsideTheFrustum() {
        var entity = new TestEntity(new BlockPos(30, 0, 0), Blocks.STONE.defaultBlockState());
        var renderer = new BoundsRenderer(new AABB(0, 0, 0, 31, 1, 1)); var frustum = frustum();
        assertFalse(frustum.isVisible(new AABB(entity.getBlockPos())));
        assertTrue(BlockEntityVisibility.isVisible(renderer, entity, frustum));
        renderer.bounds = new AABB(29, 0, 0, 31, 1, 1);
        assertFalse(BlockEntityVisibility.isVisible(renderer, entity, frustum));
    }

    @Test void globalRenderersStillRespectTheirFiniteModelBounds() {
        var entity = new TestEntity(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        var renderer = new BoundsRenderer(new AABB(30, 0, 0, 31, 1, 1)); renderer.offscreen = true;
        assertTrue(renderer.shouldRenderOffScreen(entity));
        assertFalse(BlockEntityVisibility.isVisible(renderer, entity, frustum()));
        renderer.bounds = new AABB(-1, -1, -1, 1, 1, 1);
        assertTrue(BlockEntityVisibility.isVisible(renderer, entity, frustum()));
    }

    @Test void allInfiniteBoundsBypassNativeFrustumArithmeticWithoutSpecialObjectIdentity() {
        var renderer = new BoundsRenderer(new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertTrue(BlockEntityVisibility.isVisible(renderer, new TestEntity(BlockPos.ZERO, Blocks.STONE.defaultBlockState()), null));
    }

    @Test void vanillaRenderersKeepTheirExistingSpecialVisibilityAndMissingRenderersNeverRender() {
        var entity = new TestEntity(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<TestEntity> nativeRenderer = (be, tick, pose, buffers, light, overlay) -> {};
        assertTrue(BlockEntityVisibility.isVisible(nativeRenderer, entity, null));
        assertFalse(BlockEntityVisibility.isVisible((net.minecraft.client.renderer.blockentity.BlockEntityRenderer<TestEntity>) null, entity, null));
    }

    @Test void eachOwnedRendererIsCollectedOnceIntoExactlyOneSectionOrGlobalList() {
        var entity = new TestEntity(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        var renderer = new BoundsRenderer(new AABB(BlockPos.ZERO));
        var normal = new SectionCompiler.Results();
        assertTrue(BlockEntityVisibility.collect(normal, entity, renderer));
        assertEquals(List.of(entity), normal.blockEntities); assertTrue(normal.globalBlockEntities.isEmpty());
        assertEquals(1, renderer.offscreenQueries);
        renderer.offscreen = true; renderer.offscreenQueries = 0;
        var global = new SectionCompiler.Results();
        assertTrue(BlockEntityVisibility.collect(global, entity, renderer));
        assertEquals(List.of(entity), global.globalBlockEntities); assertTrue(global.blockEntities.isEmpty());
        assertEquals(1, renderer.offscreenQueries);
    }

    @Test void sectionCollectionLeavesOtherRenderersAndMissingRenderersToTheNativeImplementation() {
        var entity = new TestEntity(BlockPos.ZERO, Blocks.STONE.defaultBlockState()); var results = new SectionCompiler.Results();
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<TestEntity> nativeRenderer = (be, tick, pose, buffers, light, overlay) -> {};
        assertFalse(BlockEntityVisibility.collect(results, entity, nativeRenderer));
        assertFalse(BlockEntityVisibility.collect(results, entity, null));
        assertTrue(results.blockEntities.isEmpty()); assertTrue(results.globalBlockEntities.isEmpty());
    }

    @Test void aStateAwareRendererKeepsItsNativeShouldRenderDecisionSeparatelyFromBounds() {
        var entity = new TestEntity(BlockPos.ZERO, Blocks.REDSTONE_LAMP.defaultBlockState());
        var renderer = new BoundsRenderer(new AABB(BlockPos.ZERO)) {
            @Override public boolean shouldRender(TestEntity be, Vec3 camera) {
                return be.getBlockState().getValue(BlockStateProperties.LIT) && super.shouldRender(be, camera);
            }
        };
        assertTrue(BlockEntityVisibility.isVisible(renderer, entity, frustum()));
        assertFalse(renderer.shouldRender(entity, Vec3.ZERO));
        entity.setBlockState(entity.getBlockState().setValue(BlockStateProperties.LIT, true));
        assertTrue(renderer.shouldRender(entity, Vec3.ZERO)); assertFalse(renderer.shouldRender(entity, new Vec3(100, 0, 0)));
    }

    @Test void aNullCustomBoundFailsClearlyInsteadOfSilentlyDisablingCulling() {
        assertThrows(NullPointerException.class, () -> BlockEntityVisibility.isVisible(new BoundsRenderer(null),
                new TestEntity(BlockPos.ZERO, Blocks.STONE.defaultBlockState()), frustum()));
    }

    @Test void aDerivedRendererCanExtendTheUnitDefaultThroughTheSameDirectSuperCallAsProductionMachines() {
        RaisedRenderer renderer = (be, tick, pose, buffers, light, overlay) -> {};
        var entity = new TestEntity(new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        assertEquals(new AABB(entity.getBlockPos()).inflate(1.25, .5, 1.25).expandTowards(0, 2, 0), renderer.getRenderBoundingBox(entity));
    }

    private interface RaisedRenderer extends BlockEntityRenderer<TestEntity> {
        @Override default AABB getRenderBoundingBox(TestEntity entity) {
            return BlockEntityRenderer.super.getRenderBoundingBox(entity).inflate(1.25, .5, 1.25).expandTowards(0, 2, 0);
        }
    }

    private static Frustum frustum() { var result = new Frustum(new Matrix4f(), new Matrix4f().ortho(-5, 5, -5, 5, -5, 5)); result.prepare(0, 0, 0); return result; }
    private static final class TestEntity extends BlockEntity {
        TestEntity(BlockPos pos, BlockState state) { super(net.minecraft.world.level.block.entity.BlockEntityType.CHEST, pos, state); }
        @Override public boolean isValidBlockState(BlockState state) { return true; }
    }
    private static class BoundsRenderer implements BlockEntityRenderer<TestEntity> {
        AABB bounds; boolean offscreen; int offscreenQueries;
        BoundsRenderer(AABB bounds) { this.bounds = bounds; }
        @Override public AABB getRenderBoundingBox(TestEntity be) { return bounds; }
        @Override public boolean shouldRenderOffScreen(TestEntity be) { offscreenQueries++; return offscreen; }
        @Override public void render(TestEntity be, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) { fail("headless visibility check invoked rendering"); }
    }
}
