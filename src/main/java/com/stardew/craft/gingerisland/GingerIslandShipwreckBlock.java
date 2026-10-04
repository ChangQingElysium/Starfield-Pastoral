package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Static compound hull with an open entrance; submerged cells preserve the sea. */
public final class GingerIslandShipwreckBlock extends MapDecorStaticBlock implements SimpleWaterloggedBlock {
    public GingerIslandShipwreckBlock(Properties properties, String model) {
        super(properties, model);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.WATERLOGGED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(BlockStateProperties.WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }

    @Override protected BlockState extensionStateAt(Level level, BlockPos target, BlockState main, BlockPos offset) {
        return GingerIslandBoatWater.extension(level, target, main);
    }

    @Override protected FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                              LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED))
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        BlockState updated = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        return updated.isAir() ? state.getFluidState().createLegacyBlock() : updated;
    }
}
