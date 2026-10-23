package com.stardew.craft.platform.fabric.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Implemented only by blocks whose native light emission is explicitly bridged. */
public interface LightEmissionProvider {
    int getLightEmission(BlockState state, BlockGetter level, BlockPos pos);
    default boolean hasDynamicLightEmission(BlockState state) { return false; }
}
