package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.ToolAction;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/** Permanent authored grass; retains spreading, bonemeal and snowy-state behavior. */
public class TerrainGrassBlock extends GrassBlock {

    public TerrainGrassBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction ability, boolean simulate) {
        // Paths/farmland can later turn into dirt, so do not open either conversion route.
        if (ability == ToolActions.HOE_TILL || ability == ToolActions.SHOVEL_FLATTEN) return null;
        return super.getToolModifiedState(state, context, ability, simulate);
    }

    @Override
    public boolean onTreeGrow(BlockState state, LevelReader level, BiConsumer<BlockPos, BlockState> place,
                              RandomSource random, BlockPos pos, TreeConfiguration config) {
        // A handled hook lets the tree grow without replacing the supporting grass with dirt.
        return true;
    }
}
