package com.stardew.craft.port;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.21 {@code new ClipContext(from, to, block, fluid, CollisionContext)}. 1.20.1 only builds the collision context
 * from an entity; this subclass keeps the given context and uses it exactly where 1.21 does
 * ({@link #getBlockShape}). Fluid picking and endpoints are inherited unchanged.
 */
public final class PortClipContext extends ClipContext {
    private final ClipContext.Block blockShape;
    private final CollisionContext collisionContext;

    public PortClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid,
            CollisionContext collisionContext) {
        super(from, to, block, fluid, null);
        this.blockShape = block;
        this.collisionContext = collisionContext;
    }

    @Override
    public VoxelShape getBlockShape(BlockState blockState, BlockGetter level, BlockPos pos) {
        return blockShape.get(blockState, level, pos, collisionContext);
    }
}
