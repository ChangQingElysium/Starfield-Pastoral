package com.stardew.craft.platform.fabric.blockextension.face;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface ExternalFaceHidingProvider {
    default boolean supportsExternalFaceHiding(BlockState state) { return false; }
    default boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction direction) { return false; }
}
