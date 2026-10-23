package com.stardew.craft.platform.fabric.blockextension.plant;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public interface TreeGrowProvider {
    default boolean onTreeGrow(BlockState state, LevelReader level, BiConsumer<BlockPos, BlockState> place, RandomSource random, BlockPos pos, TreeConfiguration config) { return false; }
}
