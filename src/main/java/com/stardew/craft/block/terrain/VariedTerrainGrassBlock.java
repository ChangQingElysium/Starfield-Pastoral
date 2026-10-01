package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Ordinary grass has spring decorations; dark grass retains its original state space. */
public final class VariedTerrainGrassBlock extends TerrainGrassBlock {

    public VariedTerrainGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TerrainVariants.GRASS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TerrainVariants.GRASS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return TerrainVariants.placement(super.getStateForPlacement(context), context);
    }
}
