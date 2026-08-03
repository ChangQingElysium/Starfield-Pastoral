package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.service.FishPondColorSyncService;
import com.stardew.craft.fishpond.service.FishPondManagerValidationService;
import com.stardew.craft.fishpond.service.FishPondWaterService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

@SuppressWarnings("null")
public class FishPondManagerBlock extends Block {

    public FishPondManagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state,
                                       Level level,
                                       BlockPos pos,
                                       Player player,
                                       boolean willHarvest,
                                       FluidState fluid) {
        if (level instanceof ServerLevel serverLevel) {
            boolean hasBinding = FishPondWorldData.get(serverLevel)
                .findPondByManagerAnyOwner(serverLevel.dimension().location().toString(), pos)
                .isPresent();
            if (hasBinding) {
                player.displayClientMessage(Component.translatable("message.stardew_craft.manager.break_blocked"), true);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
                                              net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.List.of(new ItemStack(ModBlocks.FISH_POND_MANAGER.get()));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                new SimpleMenuProvider(
                    (containerId, playerInventory, playerEntity) -> new com.stardew.craft.menu.FishPondManagerMenu(containerId, playerInventory, pos),
                    Component.translatable("container.stardew_craft.fish_pond_manager")
                )
            );
        }
        return InteractionResult.CONSUME;
    }

    public static boolean tryCreateOrRefreshPond(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        java.util.UUID farmOwner = com.stardew.craft.farm.FarmResourceOwnership
                .resolveManageableOwner(level, managerPos, player);
        if (farmOwner == null) {
            return false;
        }
        FishPondWorldData worldData = FishPondWorldData.get(level);
        worldData.reconcileFarmOwnership(level);
        Optional<com.stardew.craft.fishpond.model.FishPondRecord> existingAnyOwner =
            worldData.findPondByManagerAnyOwner(level.dimension().location().toString(), managerPos);
        if (existingAnyOwner.isPresent()
            && !farmOwner.toString().equals(existingAnyOwner.get().ownerPlayerUuid())) {
            player.displayClientMessage(
                Component.translatable("message.stardew_craft.manager.relocate_owner_mismatch"),
                true
            );
            return false;
        }

        FishPondManagerValidationService.ValidationResult validation =
            FishPondManagerValidationService.validate(level, managerPos);
        Optional<com.stardew.craft.fishpond.model.FishPondRecord> existingOwn =
            worldData.findPondByManager(level.dimension().location().toString(), farmOwner, managerPos);

        if (!validation.ok()) {
            player.displayClientMessage(validation.message(), true);
            return false;
        }

        BlockPos bucketPos = validation.scan().bucketPositions().iterator().next();
        worldData.createOrUpdatePondAtManager(
            level,
            farmOwner,
            managerPos,
            bucketPos,
            validation.scan().netPositions(),
            validation.scan().waterCells(),
            validation.scan().minX(),
            validation.scan().minY(),
            validation.scan().minZ(),
            validation.scan().maxX(),
            validation.scan().maxY(),
            validation.scan().maxZ()
        );
        FishPondWaterService.rebindPondWater(level, existingOwn.orElse(null), validation.scan().waterCells());
        FishPondColorSyncService.broadcastSnapshot(level);

        return true;
    }

    public static boolean tryDemolishPond(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        java.util.UUID farmOwner = com.stardew.craft.farm.FarmResourceOwnership
                .resolveManageableOwner(level, managerPos, player);
        if (farmOwner == null) {
            return false;
        }
        FishPondWorldData worldData = FishPondWorldData.get(level);
        worldData.reconcileFarmOwnership(level);
        Optional<com.stardew.craft.fishpond.model.FishPondRecord> removed = worldData.removePondByManager(
            level.dimension().location().toString(),
            farmOwner,
            managerPos
        );
        if (removed.isEmpty()) {
            return false;
        }

        FishPondWaterService.removePondWater(level, removed.get());
        FishPondColorSyncService.broadcastSnapshot(level);
        BlockState state = level.getBlockState(managerPos);
        level.levelEvent(2001, managerPos, Block.getId(state));
        level.removeBlock(managerPos, false);
        popResource(level, managerPos, new ItemStack(ModBlocks.FISH_POND_MANAGER.get()));
        return true;
    }
}
