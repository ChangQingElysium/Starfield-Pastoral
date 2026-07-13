package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public final class AnimalEntitySyncService {
    private static final AABB FULL_LEVEL_BOX = new AABB(-30_000_000, -64, -30_000_000, 30_000_000, 320, 30_000_000);

    private AnimalEntitySyncService() {
    }

    public record SyncResult(int updated, int spawned, int orphansRemoved) {
    }

    public static SyncResult syncAll(ServerLevel level) {
        AnimalWorldData data = AnimalWorldData.get(level);
        CollectionState state = collectLoaded(level);
        int updated = 0;
        int spawned = 0;

        // Detect orphan animals whose building no longer exists
        List<Long> orphanIds = new ArrayList<>();
        for (FarmAnimalRecord record : data.getAnimals()) {
            if (record.buildingId() != null && !record.buildingId().isBlank()
                && data.getBuilding(record.buildingId()).isEmpty()
                && data.getBuildingIncludingInactive(record.buildingId()).isEmpty()) {
                orphanIds.add(record.animalId());
            }
        }
        for (long orphanId : orphanIds) {
            BaseCoopAnimalEntity orphanEntity = state.byManagedId.remove(orphanId);
            if (orphanEntity != null) {
                orphanEntity.discard();
            }
            data.removeAnimal(orphanId);
            StardewCraft.LOGGER.info("[ANIMAL_SYNC] Removed orphan animal {} (building gone)", orphanId);
        }

        for (FarmAnimalRecord record : data.getAnimals()) {
            BaseCoopAnimalEntity entity = state.byManagedId.get(record.animalId());
            if (entity == null) {
                entity = spawnEntityForRecord(level, data, record);
                if (entity != null) {
                    spawned++;
                    updated++;
                }
                continue;
            }
            applyRecord(entity, record);
            updated++;
        }

        return new SyncResult(updated, spawned, orphanIds.size());
    }

    public static BaseCoopAnimalEntity spawnOrSyncSingle(ServerLevel level, FarmAnimalRecord record) {
        BaseCoopAnimalEntity existing = findLoadedByManagedId(level, record.animalId());
        if (existing != null) {
            applyRecord(existing, record);
            return existing;
        }
        return spawnEntityForRecord(level, AnimalWorldData.get(level), record);
    }

    private static BaseCoopAnimalEntity spawnEntityForRecord(ServerLevel level,
                                                             AnimalWorldData data,
                                                             FarmAnimalRecord record) {
        AnimalBuildingRecord building = data.getBuilding(record.buildingId()).orElse(null);
        if (building == null) {
            StardewCraft.LOGGER.debug("[ANIMAL_SYNC] Skipping spawn for animal {} - building {} is missing or inactive",
                record.animalId(), record.buildingId());
            return null;
        }
        if (!level.dimension().location().toString().equals(building.dimensionId())) {
            return null;
        }

        // The persisted entity may still exist in an unloaded chunk. Spawning anywhere else
        // would create a duplicate, so managed animals never fall back to the world spawn.
        BlockPos managerPos = building.managerPos();
        if (!level.isLoaded(managerPos)) {
            StardewCraft.LOGGER.debug("[ANIMAL_SYNC] Skipping spawn for animal {} - building chunk not loaded at {}",
                record.animalId(), managerPos);
            return null;
        }

        EntityType<? extends BaseCoopAnimalEntity> type = resolveEntityType(record.animalTypeId());
        if (type == null) {
            StardewCraft.LOGGER.warn("[ANIMAL_SYNC] Unknown animal type: {}", record.animalTypeId());
            return null;
        }

        BaseCoopAnimalEntity entity = type.create(level);
        if (entity == null) {
            StardewCraft.LOGGER.warn("[ANIMAL_SYNC] Failed to create entity for type {}", record.animalTypeId());
            return null;
        }

        BlockPos spawnPos = findSpawnPos(level, building);
        entity.moveTo(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            level.random.nextFloat() * 360.0F,
            0.0F
        );
        applyRecord(entity, record);
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static void applyRecord(BaseCoopAnimalEntity entity, FarmAnimalRecord record) {
        entity.setManagedAnimalId(record.animalId());
        entity.setManagedAnimalType(record.animalTypeId());
        entity.setBaby(record.isBaby());
        entity.setPersistenceRequired();

        String customName = record.customName();
        if (customName == null || customName.isBlank()) {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            return;
        }

        entity.setCustomName(Component.literal(customName));
        entity.setCustomNameVisible(true);
    }

    private static BaseCoopAnimalEntity findLoadedByManagedId(ServerLevel level, long animalId) {
        if (animalId <= 0L) {
            return null;
        }
        for (BaseCoopAnimalEntity entity : level.getEntitiesOfClass(BaseCoopAnimalEntity.class, FULL_LEVEL_BOX)) {
            if (entity.getManagedAnimalId() == animalId) {
                return entity;
            }
        }
        return null;
    }

    private static BlockPos findSpawnPos(ServerLevel level, AnimalBuildingRecord building) {
        BlockPos base = building.managerPos().above();
        if (canStand(level, base)) {
            return base;
        }

        int maxRange = Math.max(1, Math.min(8, building.range()));
        for (int radius = 1; radius <= maxRange; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = base.offset(dx, 0, dz);
                    if (!building.isInBounds(candidate)) {
                        continue;
                    }
                    if (canStand(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return base;
    }

    private static boolean canStand(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static EntityType<? extends BaseCoopAnimalEntity> resolveEntityType(String animalTypeId) {
        return switch (animalTypeId) {
            case "white_chicken" -> ModEntities.WHITE_CHICKEN.get();
            case "golden_chicken" -> ModEntities.GOLDEN_CHICKEN.get();
            case "duck" -> ModEntities.DUCK.get();
            case "void_chicken" -> ModEntities.VOID_CHICKEN.get();
            case "rabbit" -> ModEntities.RABBIT.get();
            case "ostrich" -> ModEntities.OSTRICH.get();
            case "dinosaur" -> ModEntities.DINOSAUR.get();
            case "cow" -> ModEntities.COW.get();
            case "goat" -> ModEntities.GOAT.get();
            case "sheep" -> ModEntities.SHEEP.get();
            case "pig" -> ModEntities.PIG.get();
            default -> null;
        };
    }

    private static CollectionState collectLoaded(ServerLevel level) {
        Map<Long, BaseCoopAnimalEntity> byManagedId = new HashMap<>();
        int duplicatesRemoved = 0;
        for (BaseCoopAnimalEntity entity : level.getEntitiesOfClass(BaseCoopAnimalEntity.class, FULL_LEVEL_BOX)) {
            long managedId = entity.getManagedAnimalId();
            if (managedId > 0L) {
                BaseCoopAnimalEntity existing = byManagedId.putIfAbsent(managedId, entity);
                if (existing != null) {
                    entity.discard();
                    duplicatesRemoved++;
                }
            }
        }
        if (duplicatesRemoved > 0) {
            StardewCraft.LOGGER.warn("[ANIMAL_SYNC] Removed {} loaded duplicate animal entities", duplicatesRemoved);
        }
        return new CollectionState(byManagedId);
    }

    private record CollectionState(Map<Long, BaseCoopAnimalEntity> byManagedId) {
    }
}
