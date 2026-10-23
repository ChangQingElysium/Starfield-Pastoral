package com.stardew.craft.platform.fabric.blockextension.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public interface RailBehaviorProvider {
    default boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) { return true; }
    default boolean isFlexibleRail(BlockState state, BlockGetter level, BlockPos pos) { return !((BaseRailBlock) state.getBlock()).isStraight(); }
    default boolean isValidRailShape(RailShape shape) { return true; }
}
