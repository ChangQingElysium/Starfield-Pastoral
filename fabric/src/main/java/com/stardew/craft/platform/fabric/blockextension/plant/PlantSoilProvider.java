package com.stardew.craft.platform.fabric.blockextension.plant;

import com.stardew.craft.platform.fabric.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface PlantSoilProvider {
    default TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, BlockState plant) { return TriState.DEFAULT; }
    default boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) { return PlantHooks.defaultFertility(state); }
}
