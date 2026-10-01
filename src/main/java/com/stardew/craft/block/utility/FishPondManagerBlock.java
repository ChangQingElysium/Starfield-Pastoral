package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.stardew.craft.port.PortBlockInteraction;


@SuppressWarnings("null")
public class FishPondManagerBlock extends BuildingManagerModelBlock implements PortBlockInteraction {

    public FishPondManagerBlock(Properties properties) {
        super(properties, "stardewcraft:block/fish_pond_manager");
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
                if (player instanceof ServerPlayer receiver) com.stardew.craft.network.GlobalHudMessagePayload.sendTo(receiver, Component.translatable("message.stardew_craft.manager.break_blocked"));
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

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var record = com.stardew.craft.building.runtime.FishPondPrefabs.at(serverPlayer.serverLevel(),pos);
            if (record == null) {
                com.stardew.craft.building.runtime.BuildingPlacementService.message(serverPlayer,"prefab_only");
                return InteractionResult.CONSUME;
            }
            if (!com.stardew.craft.building.runtime.BuildingService.canManage(serverPlayer,record)) {
                com.stardew.craft.building.runtime.BuildingPlacementService.message(serverPlayer,"permission");
                return InteractionResult.CONSUME;
            }
            if (record.phase()!=com.stardew.craft.building.runtime.BuildingRecord.Phase.READY) {
                com.stardew.craft.building.runtime.BuildingManagerInteraction.open(serverPlayer,pos);
                return InteractionResult.CONSUME;
            }
            com.stardew.craft.building.runtime.FishPondPrefabs.bind(serverPlayer.serverLevel(),record);
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
        return com.stardew.craft.building.runtime.BuildingManagerInteraction.open(player, managerPos);
    }

    public static boolean tryDemolishPond(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        var record = com.stardew.craft.building.runtime.FishPondPrefabs.at(level,managerPos);
        return record != null && com.stardew.craft.building.runtime.BuildingService.canManage(player,record)
                && com.stardew.craft.building.runtime.BuildingDemolition.perform(player,record);
    }
}
