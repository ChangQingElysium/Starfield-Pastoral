package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.WillyBoatBlockEntity;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Animate the approved baked model once, without rebuilding or copying its geometry every frame. */
public final class WillyBoatBlockEntityRenderer implements LargeDecorBlockEntityRenderer<WillyBoatBlockEntity> {
    private final BlockRenderDispatcher dispatcher;
    private final RandomSource random = RandomSource.create(0);
    private final Map<BlockState, PreparedModel> prepared = new IdentityHashMap<>();

    public WillyBoatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public AABB getRenderBoundingBox(WillyBoatBlockEntity boat) {
        return boat.getRenderBoundingBox();
    }

    @Override
    public void render(WillyBoatBlockEntity boat, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = boat.getLevel();
        var state = boat.getBlockState();
        if (level == null || state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) return;
        BakedModel model = dispatcher.getBlockModel(state);
        PreparedModel cached = prepared.get(state);
        if (cached == null || cached.source() != model) {
            // Composite models implement IDynamicBakedModel even for these static resources.
            // Cache our known state-only partition; a model reload replaces its source identity.
            cached = new PreparedModel(model, SpatialBlockModelRenderer.partition(model, state, random, 0));
            prepared.put(state, cached);
        }

        var motion = boat.motion(partialTick);
        pose.pushPose();
        try {
            pose.translate(.5, motion.heave(), .5);
            // The blockstate model already includes its facing rotation. Rotate around
            // its longitudinal waterline axis in world space without applying yaw again.
            switch (state.getValue(MapDecorStaticBlock.FACING)) {
                case NORTH -> pose.mulPose(Axis.ZP.rotationDegrees(motion.rollDegrees()));
                case EAST -> pose.mulPose(Axis.XP.rotationDegrees(-motion.rollDegrees()));
                case SOUTH -> pose.mulPose(Axis.ZP.rotationDegrees(-motion.rollDegrees()));
                case WEST -> pose.mulPose(Axis.XP.rotationDegrees(motion.rollDegrees()));
                default -> throw new IllegalStateException("Boat facing is not horizontal");
            }
            pose.translate(-.5, 0, -.5);
            var vertices = buffers.getBuffer(Sheets.cutoutBlockSheet());
            var renderer = dispatcher.getModelRenderer();
            for (var cell : cached.cells()) {
                pose.pushPose();
                try {
                    pose.translate(cell.offset.getX(), cell.offset.getY(), cell.offset.getZ());
                    renderer.tesselateBlock(level, cell, state, boat.getBlockPos().offset(cell.offset),
                            pose, vertices, false, random, 0, overlay, ModelData.EMPTY, null);
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private record PreparedModel(BakedModel source, List<SpatialBlockModelRenderer.CellModel> cells) {}
}
