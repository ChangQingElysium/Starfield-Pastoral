package com.stardew.craft.block.utility;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.service.SiloManagerValidationService;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.menu.SiloManagerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.SimpleMenuProvider;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Optional;

@SuppressWarnings("null")
public class SiloManagerBlock extends Block {
    public static final String TAG_RELOCATE = CoopManagerBlock.TAG_RELOCATE;
    public static final String TAG_BUILDING_ID = CoopManagerBlock.TAG_BUILDING_ID;
    public static final String TAG_OWNER = CoopManagerBlock.TAG_OWNER;
    public static final String TAG_DIMENSION = CoopManagerBlock.TAG_DIMENSION;
    public static final String TAG_FAMILY = CoopManagerBlock.TAG_FAMILY;
    public static final String TAG_TIER = CoopManagerBlock.TAG_TIER;
    public static final String TAG_ANIMAL_COUNT = CoopManagerBlock.TAG_ANIMAL_COUNT;

    public SiloManagerBlock(Properties properties) {
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
            AnimalWorldData data = AnimalWorldData.get(serverLevel);
            boolean hasBinding = data.findBuildingByManagerAnyOwner(
                serverLevel.dimension().location().toString(),
                "silo",
                pos
            ).isPresent();
            if (hasBinding) {
                player.displayClientMessage(Component.translatable("message.stardew_craft.manager.break_blocked"), true);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    // 未绑定建筑时，被玩家破坏后掉落自身（无需 loot table）
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
                                              net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.List.of(new ItemStack(ModBlocks.SILO_MANAGER.get()));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!stack.isEmpty() && stack.is(com.stardew.craft.item.ModItems.HAY.get())) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
                return ItemInteractionResult.CONSUME;
            }
            AnimalWorldData data = AnimalWorldData.get(serverLevel);
            java.util.UUID hayOwner = resolveHayOwner(serverLevel, pos, data, serverPlayer.getUUID());
            if (data.getHayCapacity(hayOwner) <= 0) {
                serverPlayer.displayClientMessage(
                    Component.translatable("stardewcraft.hud.hay_silo_full"), true);
                return ItemInteractionResult.CONSUME;
            }
            int requested = stack.getCount();
            int stored = data.storeHay(hayOwner, requested);
            if (stored <= 0) {
                serverPlayer.displayClientMessage(
                    Component.translatable("stardewcraft.hud.hay_silo_full"), true);
                return ItemInteractionResult.CONSUME;
            }
            stack.shrink(stored);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
            com.stardew.craft.network.HayHarvestHudMessagePacket.sendTo(serverPlayer, stored, false);
            return ItemInteractionResult.sidedSuccess(false);
        }
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
                    (containerId, playerInventory, playerEntity) -> new SiloManagerMenu(containerId, playerInventory, pos),
                    Component.translatable("container.stardew_craft.silo_manager")
                )
            );
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_RELOCATE, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag relocateTag = tag.getCompound(TAG_RELOCATE);
        if (relocateTag == null) {
            return;
        }

        String buildingId = relocateTag.getString(TAG_BUILDING_ID);
        String owner = relocateTag.getString(TAG_OWNER);
        String dimension = relocateTag.getString(TAG_DIMENSION);
        String family = relocateTag.getString(TAG_FAMILY);
        if (buildingId.isBlank() || owner.isBlank() || family.isBlank() || !"silo".equalsIgnoreCase(family)) {
            return;
        }
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), owner)) {
            refundRelocationItem(stack, serverLevel, pos, serverPlayer);
            serverLevel.removeBlock(pos, false);
            serverPlayer.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_owner_mismatch"));
            return;
        }

        SiloManagerValidationService.ValidationResult validation = SiloManagerValidationService.validate(serverLevel, pos);
        if (!validation.success()) {
            refundRelocationItem(stack, serverLevel, pos, serverPlayer);
            serverLevel.removeBlock(pos, false);
            serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.manager.relocation.failed",
                    Component.translatable("block.stardewcraft.silo_manager"), validation.message()));
            return;
        }

        boolean moved = AnimalWorldData.get(serverLevel).moveBuildingManagerFromItem(
            buildingId,
            serverPlayer.getUUID(),
            dimension.isBlank() ? serverLevel.dimension().location().toString() : dimension,
            pos,
            family,
            validation.minX(),
            validation.minY(),
            validation.minZ(),
            validation.maxX(),
            validation.maxY(),
            validation.maxZ()
        );

        if (moved) {
            serverPlayer.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_pending"));
            return;
        }

        refundRelocationItem(stack, serverLevel, pos, serverPlayer);
        serverLevel.removeBlock(pos, false);
    }

    private static void refundRelocationItem(ItemStack placementStack,
                                             ServerLevel level,
                                             BlockPos pos,
                                             ServerPlayer player) {
        if (!placementStack.isEmpty()) {
            placementStack.grow(1);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return;
        }

        ItemStack managerItem = new ItemStack(ModBlocks.SILO_MANAGER.get().asItem());
        if (!player.getInventory().add(managerItem)) {
            popResource(level, pos, managerItem);
        }
    }

    public static boolean tryBuild(ServerLevel level, BlockPos managerPos, Player player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        java.util.UUID owner = com.stardew.craft.core.FarmAreaResolver.getOwnerAt(managerPos);
        if (owner == null) {
            owner = player.getUUID();
        }
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManagerAnyOwner(
            level.dimension().location().toString(),
            "silo",
            managerPos
        );

        if (existingOpt.isPresent()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.manager.building.already_built",
                    Component.translatable("stardewcraft.manager.building.silo")));
            return false;
        }

        SiloManagerValidationService.ValidationResult validation = SiloManagerValidationService.validate(level, managerPos);
        if (!validation.success()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.manager.validation.failed",
                    Component.translatable("block.stardewcraft.silo_manager"), validation.message()));
            level.playSound(null, managerPos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8f, 1.0f);
            return false;
        }

        AnimalBuildingType targetType = AnimalBuildingType.SILO_TIER_1;
        String buildingId = data.createOrUpdateBuildingAtManager(
            level,
            targetType,
            owner,
            managerPos,
            "Silo",
            validation.minX(),
            validation.minY(),
            validation.minZ(),
            validation.maxX(),
            validation.maxY(),
            validation.maxZ(),
            Collections.emptySet(),
            Collections.emptySet()
        );

        player.sendSystemMessage(Component.translatable("stardewcraft.manager.building.created",
                Component.translatable("stardewcraft.manager.building.silo"), 1, buildingId));
        level.playSound(null, managerPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.1f);

        // 通知任务系统：建筑已建造
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.stardew.craft.quest.StardewQuestEvents.fireBuildingExists(sp, "Silo");
        }
        return true;
    }

    private static java.util.UUID resolveHayOwner(ServerLevel level,
                                                  BlockPos managerPos,
                                                  AnimalWorldData data,
                                                  java.util.UUID fallbackOwner) {
        Optional<AnimalBuildingRecord> existing = data.findBuildingByManagerAnyOwner(
            level.dimension().location().toString(),
            "silo",
            managerPos
        );
        if (existing.isPresent()) {
            try {
                return java.util.UUID.fromString(existing.get().ownerPlayerUuid());
            } catch (IllegalArgumentException ignored) {
                return fallbackOwner;
            }
        }
        java.util.UUID farmOwner = com.stardew.craft.core.FarmAreaResolver.getOwnerAt(managerPos);
        return farmOwner == null ? fallbackOwner : farmOwner;
    }

    public static boolean tryDemolishBuilding(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManagerAnyOwner(
            level.dimension().location().toString(),
            "silo",
            managerPos
        );

        if (existingOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.no_building"));
            return false;
        }

        AnimalBuildingRecord existing = existingOpt.get();
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(player.getUUID(), existing.ownerPlayerUuid())) {
            player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_owner_mismatch"));
            return false;
        }
        int removedAnimals = data.demolishBuildingAndRemoveAnimals(existing.buildingId());

        BlockState state = level.getBlockState(managerPos);
        level.levelEvent(2001, managerPos, Block.getId(state));
        level.removeBlock(managerPos, false);
        popResource(level, managerPos, new ItemStack(ModBlocks.SILO_MANAGER.get()));
        level.playSound(null, managerPos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);

        player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.demolished", removedAnimals));
        return true;
    }

    public static boolean tryRelocateManager(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManagerAnyOwner(
            level.dimension().location().toString(),
            "silo",
            managerPos
        );

        if (existingOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.no_building"));
            return false;
        }

        AnimalBuildingRecord existing = existingOpt.get();
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(player.getUUID(), existing.ownerPlayerUuid())) {
            player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_owner_mismatch"));
            return false;
        }
        data.deactivateBuildingForRelocation(existing.buildingId());

        ItemStack managerItem = new ItemStack(ModBlocks.SILO_MANAGER.get().asItem());
        CompoundTag rootTag = managerItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag relocateTag = new CompoundTag();
        relocateTag.putString(TAG_BUILDING_ID, existing.buildingId());
        relocateTag.putString(TAG_OWNER, existing.ownerPlayerUuid());
        relocateTag.putString(TAG_DIMENSION, existing.dimensionId());
        relocateTag.putString(TAG_FAMILY, existing.buildingType().family());
        relocateTag.putInt(TAG_TIER, existing.buildingType().tier());
        relocateTag.putInt(TAG_ANIMAL_COUNT, 0);
        rootTag.put(TAG_RELOCATE, relocateTag);
        managerItem.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));

        if (!player.getInventory().add(managerItem)) {
            popResource(level, managerPos, managerItem);
        }

        BlockState state = level.getBlockState(managerPos);
        level.levelEvent(2001, managerPos, Block.getId(state));
        level.removeBlock(managerPos, false);
        level.playSound(null, managerPos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
        player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_pickup"));
        return true;
    }
}
