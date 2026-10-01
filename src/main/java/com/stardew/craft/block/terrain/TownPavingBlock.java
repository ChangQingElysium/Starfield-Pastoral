package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Full stone cube; only the chosen stone arrangement is persisted. */
public final class TownPavingBlock extends Block {

    public TownPavingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TerrainVariants.PAVING, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TerrainVariants.PAVING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return TerrainVariants.placement(defaultBlockState(), context);
    }
}
