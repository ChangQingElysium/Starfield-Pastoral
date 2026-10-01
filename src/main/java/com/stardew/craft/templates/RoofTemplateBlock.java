package com.stardew.craft.templates;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;

/** One occupied cell, with a roof skin and an independently removable infill. */
public class RoofTemplateBlock extends CompositeTemplateBlock {
    public RoofTemplateBlock(TemplateShape shape, Properties properties) {
        super(shape, properties.dynamicShape());
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return TemplateShapeCache.get(templateShape(), state, RoofTemplateEdges.exposed(level, pos, state));
    }

    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level,
                           net.minecraft.core.BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (state.getBlock() != oldState.getBlock()) {
            if (!level.isClientSide() && state.getBlock() instanceof SmartRoofTemplateBlock) {
                var updated = SmartRoofTemplateBlock.withShape(state, level, pos);
                if (updated != state) level.setBlock(pos, updated, net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            refreshTouchingRoofs(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level,
                            net.minecraft.core.BlockPos pos, BlockState nextState, boolean moving) {
        super.onRemove(state, level, pos, nextState, moving);
        if (state.getBlock() != nextState.getBlock()) refreshTouchingRoofs(level, pos);
    }

    static void refreshTouchingRoofs(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        // A rising roof touches the next block diagonally. Refresh that block's
        // render section too, especially when the join crosses a chunk boundary.
        for (Direction side : Direction.Plane.HORIZONTAL) for (int dy : new int[]{-1, 0, 1}) {
            var neighbor = pos.relative(side).offset(0, dy, 0);
            if (!level.hasChunkAt(neighbor)) continue;
            var state = level.getBlockState(neighbor);
            if (!level.isClientSide() && state.getBlock() instanceof SmartRoofTemplateBlock) {
                var updated = SmartRoofTemplateBlock.withShape(state, level, neighbor);
                if (updated != state) level.setBlock(neighbor, updated, net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            if (state.getBlock() instanceof RoofTemplateBlock) {
                if(level.getBlockEntity(neighbor) instanceof TemplateBlockEntity entity) entity.requestModelDataUpdate();
                level.sendBlockUpdated(neighbor, state, level.getBlockState(neighbor), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Convert the hit to the same unrotated/unflipped profile used by geometry. */
    public boolean targetsFill(BlockState state, BlockHitResult hit) {
        double x = hit.getLocation().x - hit.getBlockPos().getX();
        double y = hit.getLocation().y - hit.getBlockPos().getY();
        double z = hit.getLocation().z - hit.getBlockPos().getZ();
        boolean flipped = state.getValue(FLIPPED);
        if (flipped) y = 1 - y;
        if (hit.getDirection() == (flipped ? Direction.UP : Direction.DOWN)) return true;
        if (hit.getDirection().getAxis() == Direction.Axis.Y) return false;
        int turns = templateShape().roofForm().usesFacing()
                ? TemplateShapeCache.turnsFrom(templateShape().baseFacing(), state.getValue(FACING)) : 0;
        for (int i = 0; i < turns; i++) {
            double oldX = x;
            x = z;
            z = 1 - oldX;
        }
        StairsShape corner = state.hasProperty(SmartRoofTemplateBlock.ROOF_SHAPE)
                ? state.getValue(SmartRoofTemplateBlock.ROOF_SHAPE) : StairsShape.STRAIGHT;
        float height = templateShape().roofForm().collisionHeight((float)x, (float)z, corner,
                SmartRidgeTemplateBlock.connectionMask(state));
        return y < height + 1E-4;
    }

}
