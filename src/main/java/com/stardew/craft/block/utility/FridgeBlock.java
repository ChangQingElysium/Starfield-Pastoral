package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.FridgeBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class FridgeBlock extends MapUtilityStaticBlock implements EntityBlock {
    public FridgeBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new FridgeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && state.getValue(PART) == Part.MAIN && type == ModBlockEntities.FRIDGE.get()
                ? (world, pos, blockState, entity) -> FridgeBlockEntity.clientTick(world, pos, blockState, (FridgeBlockEntity) entity)
                : null;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FridgeBlockEntity fridge) fridge.recheckOpeners();
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int value) {
        return level.getBlockEntity(pos) instanceof FridgeBlockEntity fridge && fridge.triggerEvent(id, value);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos mainPos = state.getValue(PART) == Part.EXTENSION ? findMainPos(level, pos, state) : pos;
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(mainPos);
        if (!(be instanceof FridgeBlockEntity fridge)) {
            return InteractionResult.PASS;
        }
        player.openMenu(fridge);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving && !level.isClientSide && state.getValue(PART) == Part.MAIN) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FridgeBlockEntity fridge) {
                fridge.dropAllContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
