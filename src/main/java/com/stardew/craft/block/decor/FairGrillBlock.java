package com.stardew.craft.block.decor;

import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class FairGrillBlock extends MapDecorStaticBlock {
    public FairGrillBlock(Properties properties, String modelId) {
        super(properties, modelId, true);
    }

    @Override
    public void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
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
    public ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level,
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
            tryClaimBurger(serverPlayer, mainPos);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
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
            tryClaimBurger(serverPlayer, mainPos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private void tryClaimBurger(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel serverLevel) || !FairFestivalService.canUseFairInteraction(player)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.fair.grill.closed"), true);
            return;
        }

        StardewTimeManager time = StardewTimeManager.get();
        int dateKey = time.getAbsoluteDay();
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.getLastFairGrillBurgerDateKey() == dateKey) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.fair.grill.claimed"), true);
            return;
        }

        ItemStack burger = new ItemStack(ModItems.COOKING_DISHES.get("survival_burger").get());
        if (!player.addItem(burger)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.fair.grill.inventory_full"), true);
            return;
        }

        data.setLastFairGrillBurgerDateKey(dateKey);
        player.level().playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
        player.displayClientMessage(Component.translatable("message.stardewcraft.fair.grill.received"), true);
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
}
