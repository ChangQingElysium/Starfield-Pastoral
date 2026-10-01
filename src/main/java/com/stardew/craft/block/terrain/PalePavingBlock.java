package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Light bus-stop paving; its only connected donor is the mod grass block. */
public final class PalePavingBlock extends Block {
    public PalePavingBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(TerrainVariants.PAVING, 0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(TerrainVariants.PAVING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return TerrainVariants.placement(defaultBlockState(), c); }
}
