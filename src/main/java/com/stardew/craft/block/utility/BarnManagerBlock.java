package com.stardew.craft.block.utility;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewBuildingData;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.service.AnimalBuildingConstructionService;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import com.stardew.craft.animal.service.AnimalProducePlacementService;
import com.stardew.craft.animal.service.BarnManagerValidationService;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.menu.BarnManagerMenu;
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
public class BarnManagerBlock extends Block {
    public static final String TAG_RELOCATE = CoopManagerBlock.TAG_RELOCATE;
    public static final String TAG_BUILDING_ID = CoopManagerBlock.TAG_BUILDING_ID;
    public static final String TAG_OWNER = CoopManagerBlock.TAG_OWNER;
    public static final String TAG_DIMENSION = CoopManagerBlock.TAG_DIMENSION;
    public static final String TAG_FAMILY = CoopManagerBlock.TAG_FAMILY;
    public static final String TAG_TIER = CoopManagerBlock.TAG_TIER;
    public static final String TAG_ANIMAL_COUNT = CoopManagerBlock.TAG_ANIMAL_COUNT;
    public static final String TAG_STRUCTURE_REVISION =
            CoopManagerBlock.TAG_STRUCTURE_REVISION;

    public BarnManagerBlock(Properties properties) {
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
                "barn",
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
        return java.util.List.of(new ItemStack(ModBlocks.BARN_MANAGER.get()));
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
                    (containerId, playerInventory, playerEntity) -> new BarnManagerMenu(containerId, playerInventory, pos),
                    Component.translatable("container.stardew_craft.barn_manager")
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
            refundFailedRelocation(
                    level, pos, stack, serverPlayer);
            return;
        }

        String buildingId = relocateTag.getString(TAG_BUILDING_ID);
        String owner = relocateTag.getString(TAG_OWNER);
        String dimension = relocateTag.getString(TAG_DIMENSION);
        String family = relocateTag.getString(TAG_FAMILY);
        long expectedRevision =
                relocateTag.getLong(TAG_STRUCTURE_REVISION);
        if (buildingId.isBlank() || owner.isBlank()
                || family.isBlank()
                || !"barn".equalsIgnoreCase(family)
                || expectedRevision <= 0L) {
            rejectStaleRelocation(level, pos, serverPlayer);
            return;
        }
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), owner)) {
            rejectRelocation(
                    level,
                    pos,
                    serverPlayer,
                    "message.stardew_craft.manager.relocate_owner_mismatch");
            return;
        }
        String currentDimension =
                serverLevel.dimension().location().toString();
        if (!dimension.isBlank()
                && !dimension.equals(currentDimension)) {
            refundFailedRelocation(
                    level, pos, stack, serverPlayer);
            return;
        }

        AnimalWorldData data = AnimalWorldData.get(serverLevel);
        AnimalBuildingRecord existing = data
                .getBuilding(buildingId).orElse(null);
        if (existing == null
                || existing.structureRevision()
                        != expectedRevision) {
            rejectStaleRelocation(level, pos, serverPlayer);
            return;
        }
        BarnManagerValidationService.ValidationResult validation =
                BarnManagerValidationService.validateForTier(
                        serverLevel,
                        pos,
                        existing.buildingType().tier());
        if (!validation.success()) {
            refundFailedRelocation(
                    level, pos, stack, serverPlayer);
            return;
        }
        AnimalBuildingType type = existing.buildingType();
        StardewBuildingData publicData =
                StardewAgricultureDataApi.building(
                        serverLevel,
                        pos,
                        serverLevel.getBlockState(pos));
        int capacity = publicData == null
                ? type.defaultCapacity()
                : publicData.capacity();
        BlockPos oldManagerPos = existing.managerPos();
        AnimalProducePlacementService
                .releaseProjectionsForBuildingRelocation(
                        serverLevel, data, existing);
        boolean moved = data.rebindValidatedBuildingManager(
                buildingId,
                serverPlayer.getUUID(),
                currentDimension,
                pos,
                family,
                expectedRevision,
                validation.scan().interiorMinX(),
                validation.scan().interiorMinY(),
                validation.scan().interiorMinZ(),
                validation.scan().interiorMaxX(),
                validation.scan().interiorMaxY(),
                validation.scan().interiorMaxZ(),
                validation.scan().interiorAirCells(),
                validation.scan().boundaryDoorCells(),
                capacity);
        if (!moved) {
            refundFailedRelocation(
                    level, pos, stack, serverPlayer);
            return;
        }
        if (!oldManagerPos.equals(pos)) {
            serverLevel.removeBlock(oldManagerPos, false);
        }
        data.getBuilding(buildingId).ifPresent(rebound ->
                AnimalEntitySyncService
                        .relocateBuildingAnimalsNow(
                                serverLevel, rebound));
    }

    private static void refundFailedRelocation(
            Level level,
            BlockPos pos,
            ItemStack stack,
            ServerPlayer player
    ) {
        ItemStack refund = stack.copy();
        refund.setCount(1);
        level.removeBlock(pos, false);
        if (!player.getInventory().add(refund)) {
            popResource(level, pos, refund);
        }
        com.stardew.craft.network.ObjectDialogueService.show(
                player,
                "message.stardew_craft.manager.relocate_failed_refunded");
    }

    private static void rejectStaleRelocation(
            Level level,
            BlockPos pos,
            ServerPlayer player
    ) {
        rejectRelocation(
                level,
                pos,
                player,
                "message.stardew_craft.manager.relocate_stale");
    }

    private static void rejectRelocation(
            Level level,
            BlockPos pos,
            ServerPlayer player,
            String messageKey
    ) {
        level.removeBlock(pos, false);
        com.stardew.craft.network.ObjectDialogueService.show(
                player,
                messageKey);
    }

    public static boolean tryBuildOrUpgrade(ServerLevel level, BlockPos managerPos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManager(
            level.dimension().location().toString(),
            player.getUUID(),
            "barn",
            managerPos
        );

        int currentTier = existingOpt.map(record -> record.buildingType().tier()).orElse(0);
        if (existingOpt.filter(AnimalBuildingRecord::hasPendingConstruction)
                .filter(record -> record.validationState()
                        == AnimalBuildingRecord.ValidationState.CONSTRUCTING)
                .isPresent()) {
            return false;
        }
        boolean revalidating = existingOpt
                .map(record -> !record.isGameplayEnabled())
                .orElse(false);
        if (!revalidating && currentTier >= 3) {
            return false;
        }

        int targetTier = revalidating
                ? currentTier
                : currentTier + 1;
        BarnManagerValidationService.ValidationResult validation = BarnManagerValidationService.validateForTier(level, managerPos, targetTier);
        if (!validation.success()) {
            if (revalidating) {
                existingOpt.ifPresent(existing ->
                        data.markBuildingValidationFailed(
                                existing.buildingId(),
                                validation.message().getString()));
            }
            level.playSound(null, managerPos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.8f, 1.0f);
            return false;
        }

        AnimalBuildingType targetType = AnimalBuildingType.of("barn", targetTier);
        StardewBuildingData publicData = StardewAgricultureDataApi.building(
                level, managerPos, level.getBlockState(managerPos));
        int capacity = publicData == null ? targetType.defaultCapacity() : publicData.capacity();
        String defaultName = "Barn Tier " + targetTier;
        java.util.function.Supplier<String> applyStructure = () ->
                data.createOrUpdateBuildingAtManager(
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
                        validation.scan().boundaryDoorCells(),
                        capacity);

        if (revalidating) {
            String buildingId = applyStructure.get();
            data.checkpointPausedAnimalsAt(
                    buildingId,
                    AnimalBuildingConstructionService
                            .currentAbsoluteDay());
            level.playSound(null, managerPos, SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 0.6f, 1.1f);
            return true;
        }

        AnimalBuildingConstructionService.StartResult construction =
                AnimalBuildingConstructionService.start(
                        level,
                        serverPlayer,
                        targetType,
                        currentTier == 0,
                        applyStructure);
        if (!construction.started()) {
            com.stardew.craft.network.payload.HudHintPayload.send(
                    serverPlayer,
                    "stardewcraft.manager.construction.missing_cost");
            level.playSound(null, managerPos, SoundEvents.VILLAGER_NO,
                    SoundSource.BLOCKS, 0.8f, 1.0f);
            return false;
        }
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                serverPlayer,
                Component.translatable(
                        "stardewcraft.manager.construction.started",
                        Component.translatable(
                                "stardewcraft.manager.building.barn"),
                        targetTier,
                        targetType.definition().buildDays()));
        level.playSound(null, managerPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.1f);

        return true;
    }

    public static boolean tryDemolishBuilding(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManager(
            level.dimension().location().toString(),
            player.getUUID(),
            "barn",
            managerPos
        );

        if (existingOpt.isEmpty()) {
            return false;
        }

        AnimalBuildingRecord existing = existingOpt.get();
        if (!existing.memberAnimalIds().isEmpty()) {
            return false;
        }
        data.demolishBuildingAndRemoveAnimals(existing.buildingId());

        BlockState state = level.getBlockState(managerPos);
        level.levelEvent(2001, managerPos, Block.getId(state));
        level.removeBlock(managerPos, false);
        popResource(level, managerPos, new ItemStack(ModBlocks.BARN_MANAGER.get()));
        level.playSound(null, managerPos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);

        return true;
    }

    public static boolean tryRelocateManager(ServerLevel level, BlockPos managerPos, ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(level);
        Optional<AnimalBuildingRecord> existingOpt = data.findBuildingByManager(
            level.dimension().location().toString(),
            player.getUUID(),
            "barn",
            managerPos
        );

        if (existingOpt.isEmpty()) {
            return false;
        }

        AnimalBuildingRecord existing = existingOpt.get();
        if (existing.hasPendingConstruction()) {
            return false;
        }
        ItemStack managerItem = new ItemStack(ModBlocks.BARN_MANAGER.get().asItem());
        CompoundTag rootTag = managerItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag relocateTag = new CompoundTag();
        relocateTag.putString(TAG_BUILDING_ID, existing.buildingId());
        relocateTag.putString(TAG_OWNER, existing.ownerPlayerUuid());
        relocateTag.putString(TAG_DIMENSION, existing.dimensionId());
        relocateTag.putString(TAG_FAMILY, existing.buildingType().family());
        relocateTag.putInt(TAG_TIER, existing.buildingType().tier());
        relocateTag.putInt(TAG_ANIMAL_COUNT, existing.memberAnimalIds().size());
        relocateTag.putLong(
                TAG_STRUCTURE_REVISION,
                existing.structureRevision());
        rootTag.put(TAG_RELOCATE, relocateTag);
        managerItem.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));

        if (!player.getInventory().add(managerItem)) {
            popResource(level, managerPos, managerItem);
        }

        level.playSound(null, managerPos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
        return true;
    }
}
