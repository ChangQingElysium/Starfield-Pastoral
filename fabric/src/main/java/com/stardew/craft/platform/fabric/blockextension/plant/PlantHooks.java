package com.stardew.craft.platform.fabric.blockextension.plant;

import com.stardew.craft.platform.fabric.util.TriState;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class PlantHooks {
    private PlantHooks() {}
    public static TriState canSustainPlant(BlockState soil, BlockGetter level, BlockPos pos, Direction facing, BlockState plant) {
        return soil.getBlock() instanceof PlantSoilProvider provider ? Objects.requireNonNull(provider.canSustainPlant(soil, level, pos, facing, plant), "plant support decision") : TriState.DEFAULT;
    }
    public static TriState upwardDecision(BlockState plant, BlockGetter level, BlockPos pos) {
        BlockPos soilPos = pos.below(); return canSustainPlant(level.getBlockState(soilPos), level, soilPos, Direction.UP, plant);
    }
    public static boolean isFertile(BlockState soil, BlockGetter level, BlockPos pos) {
        return soil.getBlock() instanceof PlantSoilProvider provider ? provider.isFertile(soil, level, pos) : defaultFertility(soil);
    }
    public static boolean handlesTreeSoil(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> place,
            RandomSource random, BlockPos pos, TreeConfiguration config) {
        if (!(level instanceof LevelReader reader)) return false;
        var state = reader.getBlockState(pos);
        return state.getBlock() instanceof TreeGrowProvider provider && provider.onTreeGrow(state, reader, place, random, pos, config);
    }
    public static boolean defaultFertility(BlockState soil) { return soil.getBlock() instanceof FarmBlock && soil.getValue(FarmBlock.MOISTURE) > 0; }
}
