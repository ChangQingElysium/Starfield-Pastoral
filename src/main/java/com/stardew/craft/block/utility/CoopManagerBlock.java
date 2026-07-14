package com.stardew.craft.block.utility;

import com.stardew.craft.Config;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.service.CoopManagerValidationService;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.menu.CoopManagerMenu;
import net.minecraft.SharedConstants;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

import java.util.Optional;

@SuppressWarnings("null")
public class CoopManagerBlock extends Block {
    public static final String TAG_RELOCATE = "stardewcraft_manager_relocate";
    public static final String TAG_BUILDING_ID = "buildingId";
    public static final String TAG_OWNER = "owner";
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_FAMILY = "family";
    public static final String TAG_TIER = "tier";
    public static final String TAG_ANIMAL_COUNT = "animalCount";

    public CoopManagerBlock(Properties properties) {
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
                "coop",
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
        return java.util.List.of(new ItemStack(ModBlocks.COOP_MANAGER.get()));
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
                    (containerId, playerInventory, playerEntity) -> new CoopManagerMenu(containerId, playerInventory, pos),
                    Component.translatable("container.stardew_craft.coop_manager")
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
        if (buildingId.isBlank() || owner.isBlank() || family.isBlank() || !"coop".equalsIgnoreCase(family)) {
            return;
        }
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), owner)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_owner_mismatch"));
            return;
        }

        boolean moved = AnimalWorldData.get(serverLevel).moveBuildingManagerFromItem(
            buildingId,
            serverPlayer.getUUID(),
            dimension.isBlank() ? serverLevel.dimension().location().toString() : dimension,
            pos,
            family
        );

        if (moved) {
            serverPlayer.sendSystemMessage(Component.translatable("message.stardew_craft.manager.relocate_pending"));
        }
    }

    public static boolean tryBuildOrUpgrade(ServerLevel level, BlockPos managerPos, Player player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManager(
            level.dimension().location().toString(),
            player.getUUID(),
            "coop",
            managerPos
        );

        int currentTier = existingOpt.map(record -> record.buildingType().tier()).orElse(0);
        if (currentTier >= 3) {
            player.sendSystemMessage(Component.translatable("stardewcraft.manager.max_tier",
                    Component.translatable("block.stardewcraft.coop_manager"), 3));
            maybeSendDevHints(player, currentTier, null);
            return false;
        }

        int targetTier = currentTier + 1;
        CoopManagerValidationService.ValidationResult validation = CoopManagerValidationService.validateForTier(level, managerPos, targetTier);
        if (!validation.success()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.manager.validation.failed",
                    Component.translatable("block.stardewcraft.coop_manager"), validation.message()));
            maybeSendDevHints(player, targetTier, validation);
            level.playSound(null, managerPos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8f, 1.0f);
            return false;
        }

        AnimalBuildingType targetType = AnimalBuildingType.of("coop", targetTier);
        String defaultName = "Coop Tier " + targetTier;
        String buildingId = data.createOrUpdateBuildingAtManager(
            level,
            targetType,
            player.getUUID(),
            managerPos,
            defaultName,
            validation.scan().interiorMinX(),
            validation.scan().interiorMinY(),
            validation.scan().interiorMinZ(),
            validation.scan().interiorMaxX(),
            validation.scan().interiorMaxY(),
            validation.scan().interiorMaxZ(),
            validation.scan().interiorAirCells(),
            validation.scan().boundaryDoorCells()
        );

        if (currentTier == 0) {
            player.sendSystemMessage(Component.translatable("stardewcraft.manager.building.created",
                    Component.translatable("stardewcraft.manager.building.coop"), 1, buildingId));
        } else {
            player.sendSystemMessage(Component.translatable("stardewcraft.manager.building.upgraded",
                    Component.translatable("stardewcraft.manager.building.coop"), targetTier, buildingId));
        }

        maybeSendDevHints(player, targetTier, validation);
        level.playSound(null, managerPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.1f);

        // 通知任务系统：建筑已建造/升级
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.stardew.craft.quest.StardewQuestEvents.fireBuildingExists(sp, "Coop");
        }
        return true;
    }

    public static boolean tryDemolishBuilding(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManager(
            level.dimension().location().toString(),
            player.getUUID(),
            "coop",
            managerPos
        );

        if (existingOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.no_building"));
            return false;
        }

        AnimalBuildingRecord existing = existingOpt.get();
        if (!existing.memberAnimalIds().isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "message.stardew_craft.manager.demolish_has_animals",
                existing.memberAnimalIds().size()
            ));
            return false;
        }
        int removedAnimals = data.demolishBuildingAndRemoveAnimals(existing.buildingId());

        BlockState state = level.getBlockState(managerPos);
        level.levelEvent(2001, managerPos, Block.getId(state));
        level.removeBlock(managerPos, false);
        popResource(level, managerPos, new ItemStack(ModBlocks.COOP_MANAGER.get()));
        level.playSound(null, managerPos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);

        player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.demolished", removedAnimals));
        return true;
    }

    public static boolean tryRelocateManager(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManager(
            level.dimension().location().toString(),
            player.getUUID(),
            "coop",
            managerPos
        );

        if (existingOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.stardew_craft.manager.no_building"));
            return false;
        }

        AnimalBuildingRecord existing = existingOpt.get();
        data.deactivateBuildingForRelocation(existing.buildingId());

        ItemStack managerItem = new ItemStack(ModBlocks.COOP_MANAGER.get().asItem());
        CompoundTag rootTag = managerItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag relocateTag = new CompoundTag();
        relocateTag.putString(TAG_BUILDING_ID, existing.buildingId());
        relocateTag.putString(TAG_OWNER, existing.ownerPlayerUuid());
        relocateTag.putString(TAG_DIMENSION, existing.dimensionId());
        relocateTag.putString(TAG_FAMILY, existing.buildingType().family());
        relocateTag.putInt(TAG_TIER, existing.buildingType().tier());
        relocateTag.putInt(TAG_ANIMAL_COUNT, existing.memberAnimalIds().size());
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

    private static void maybeSendDevHints(Player player, int targetTier, CoopManagerValidationService.ValidationResult validation) {
        if (!SharedConstants.IS_RUNNING_IN_IDE || !Config.COOP_DEV_HINTS.get()) {
            return;
        }

        player.sendSystemMessage(Component.translatable("stardewcraft.manager.dev.target_tier",
                Component.translatable("block.stardewcraft.coop_manager"), targetTier));
        if (validation == null) {
            return;
        }

        var req = validation.requirement();
        var scan = validation.scan();
        player.sendSystemMessage(Component.translatable("stardewcraft.manager.dev.requirements",
                req.feedTroughCount(), req.autoFeedTroughCount(), req.hayHopperCount(),
                req.incubatorCount(), req.minInteriorBlocks()));
        player.sendSystemMessage(Component.translatable("stardewcraft.manager.dev.scan",
                scan.feedTroughCount(), scan.autoFeedTroughCount(), scan.hayHopperCount(),
                scan.incubatorCount(), scan.interiorAirCount(), scan.width(), scan.length(), scan.height(),
                scan.doorCount(), scan.enclosed()));
        player.sendSystemMessage(Component.translatable("stardewcraft.manager.dev.bounds",
                scan.interiorMinX(), scan.interiorMinY(), scan.interiorMinZ(),
                scan.interiorMaxX(), scan.interiorMaxY(), scan.interiorMaxZ()));
    }
}
