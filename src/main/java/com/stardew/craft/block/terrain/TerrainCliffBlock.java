package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Full stone cube; stone arrangement and moss are persisted independently of the season. */
public final class TerrainCliffBlock extends Block {

    public TerrainCliffBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TerrainVariants.CLIFF, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TerrainVariants.CLIFF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return TerrainVariants.placement(defaultBlockState(), context);
    }
}
