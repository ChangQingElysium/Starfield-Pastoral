package com.stardew.craft.block.decor;

import com.stardew.craft.festival.fair.FairWheelGameService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class FairWheelBlock extends MapDecorStaticBlock {
    public FairWheelBlock(Properties properties, String modelId) {
        super(properties, modelId, true);
    }

    @Override
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                           @Nonnull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) {
            return;
        }

        Direction facing = state.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx(), offset.dy(), offset.dz());
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(this)
                && extensionState.getValue(PART) == Part.EXTENSION
                && extensionState.getValue(FACING) == facing) {
                continue;
            }
            if (!extensionState.canBeReplaced()) {
                continue;
            }
            level.setBlock(extensionPos, state.setValue(PART, Part.EXTENSION), 3);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level,
                                             @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand,
                                             @Nonnull BlockHitResult hit) {
        BlockPos mainPos = mainPosForInteraction(level, pos, state);
        if (mainPos == null && state.is(this)) {
            mainPos = pos;
        }
        if (mainPos == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FairWheelGameService.open(serverPlayer);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                               @Nonnull Player player, @Nonnull BlockHitResult hit) {
        BlockPos mainPos = mainPosForInteraction(level, pos, state);
        if (mainPos == null && state.is(this)) {
            mainPos = pos;
        }
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FairWheelGameService.open(serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    private BlockPos mainPosForInteraction(Level level, BlockPos pos, BlockState state) {
        BlockPos mainPos = state.getValue(PART) == Part.EXTENSION ? findMainPos(level, pos, state) : pos;
        if (mainPos == null) {
            return null;
        }
        BlockState mainState = level.getBlockState(mainPos);
        return mainState.is(this) && mainState.getValue(PART) == Part.MAIN ? mainPos : null;
    }

    @Override
    protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        return List.of();
    }
}
