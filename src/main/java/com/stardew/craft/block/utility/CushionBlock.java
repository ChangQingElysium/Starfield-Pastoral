package com.stardew.craft.block.utility;

import com.stardew.craft.entity.seat.SofaSeatEntity;
import com.stardew.craft.entity.seat.CushionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

@SuppressWarnings("null")
public class CushionBlock extends MapUtilityStaticBlock {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, WoodenChestColorPalette.size() - 1);
    public CushionBlock(Properties properties, String modelId) {
        super(properties, modelId);
        registerDefaultState(defaultBlockState()
            .setValue(PART, Part.MAIN)
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(COLOR, WoodenChestColorPalette.defaultColorIndex()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        CushionEntity cushion = CushionEntity.migrateLegacyBlock(
            (net.minecraft.server.level.ServerLevel) level, pos, state, player);
        if (cushion == null) {
            return InteractionResult.PASS;
        }
        return cushion.trySit(player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SofaSeatEntity.removeForPos((net.minecraft.server.level.ServerLevel) level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
