package com.stardew.craft.block.mastery;

import com.stardew.craft.blockentity.AnvilBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.UtilityDropHelper;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class AnvilBlock extends HorizontalMasteryBlock implements EntityBlock {
    private static final int IRIDIUM_BARS_REQUIRED = 3;

    public AnvilBlock(Properties properties) {
        super(properties, "stardewcraft:block/mastery/anvil_mastery", Direction.SOUTH);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnvilBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.ANVIL.get()) {
            return null;
        }
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> AnvilBlockEntity.serverTick(lvl, pos, st, (AnvilBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnvilBlockEntity anvil)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (tryHarvest(level, pos, serverPlayer, anvil)) {
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (anvil.isWorking()) {
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!StardewTrinketItem.isTrinket(stack)) {
            serverPlayer.displayClientMessage(Component.translatable("stardewcraft.mastery.anvil.fail"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!StardewTrinketItem.canBeReforged(stack)) {
            serverPlayer.displayClientMessage(Component.translatable("stardewcraft.mastery.anvil.wrong_trinket"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!serverPlayer.isCreative() && countIridiumBars(serverPlayer.getInventory()) < IRIDIUM_BARS_REQUIRED) {
            serverPlayer.displayClientMessage(Component.translatable("stardewcraft.mastery.anvil.bars"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        ItemStack output = StardewTrinketItem.rerollStats(stack, serverPlayer, level.getRandom());
        if (output.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("stardewcraft.mastery.anvil.wrong_trinket"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
            consumeIridiumBars(serverPlayer.getInventory(), IRIDIUM_BARS_REQUIRED);
        }
        anvil.startReforge(output);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnvilBlockEntity anvil && tryHarvest(level, pos, serverPlayer, anvil)) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AnvilBlockEntity anvil) {
                ItemStack payload = anvil.dropStoredPayload();
                if (!payload.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), payload);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static boolean tryHarvest(Level level, BlockPos pos, ServerPlayer player, AnvilBlockEntity anvil) {
        return UtilityDropHelper.tryHarvest(level, pos, player, anvil::isReady, anvil::harvestOne,
                UtilityDropHelper.PREMIUM_MACHINE_VANILLA_XP);
    }

    private static int countIridiumBars(Inventory inventory) {
        int count = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(ModItems.IRIDIUM_BAR.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeIridiumBars(Inventory inventory, int count) {
        int remaining = count;
        for (ItemStack stack : inventory.items) {
            if (remaining <= 0) {
                return;
            }
            if (!stack.is(ModItems.IRIDIUM_BAR.get())) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }
}
