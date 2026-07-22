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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public final class AnimalEntitySyncService {
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
            applyAuthoritativeState(entity, record);
            updated++;
        }

        return new SyncResult(updated, spawned, orphanIds.size());
    }

    /**
     * Ensures a projection exists immediately for an explicit lifecycle action
     * such as purchase, incubation or moving home. This may load the target
     * building chunk; passive reconciliation must use {@link #syncAll(ServerLevel)}.
     */
    public static BaseCoopAnimalEntity ensurePresentNow(ServerLevel level, FarmAnimalRecord record) {
        AnimalWorldData data = AnimalWorldData.get(level);
        AnimalBuildingRecord building = data.getBuilding(record.buildingId()).orElse(null);
        if (building == null
            || !level.dimension().location().toString().equals(building.dimensionId())) {
            return null;
        }
        BlockPos managerPos = building.managerPos();
        level.getChunk(managerPos.getX() >> 4, managerPos.getZ() >> 4);

        BaseCoopAnimalEntity existing = findLoaded(level, record.animalId());
        if (existing != null) {
            applyAuthoritativeState(existing, record);
            return existing;
        }
        return spawnEntityForRecord(level, data, record);
    }

    /** Updates an existing projection without creating an entity for an unloaded animal. */
    public static BaseCoopAnimalEntity updateLoaded(ServerLevel level, FarmAnimalRecord record) {
        BaseCoopAnimalEntity existing = findLoaded(level, record.animalId());
        if (existing != null) {
            applyAuthoritativeState(existing, record);
        }
        return existing;
    }

    /** Removes the loaded projection while leaving the authoritative record untouched. */
    public static BaseCoopAnimalEntity removeLoaded(ServerLevel level, long animalId) {
        BaseCoopAnimalEntity existing = findLoaded(level, animalId);
        if (existing != null) {
            existing.discard();
        }
        return existing;
    }

    /** Rebuilds a loaded animal at its authoritative building after a home change. */
    public static BaseCoopAnimalEntity relocateNow(ServerLevel level, FarmAnimalRecord record) {
        removeLoaded(level, record.animalId());
        return ensurePresentNow(level, record);
    }

    private static BaseCoopAnimalEntity spawnEntityForRecord(ServerLevel level,
                                                             AnimalWorldData data,
                                                             FarmAnimalRecord record) {
        AnimalBuildingRecord building = data.getBuilding(record.buildingId()).orElse(null);
        if (building == null) {
            return null;
        }
        if (!level.dimension().location().toString().equals(building.dimensionId())) {
            return null;
        }

        // The persisted entity may still exist in an unloaded chunk. Spawning anywhere else
        // would create a duplicate, so managed animals never fall back to the world spawn.
        BlockPos managerPos = building.managerPos();
        if (!level.isLoaded(managerPos)) {
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
        applyAuthoritativeState(entity, record);
        return level.addFreshEntity(entity) ? entity : null;
    }

    public static void applyAuthoritativeState(BaseCoopAnimalEntity entity, FarmAnimalRecord record) {
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

    public static BaseCoopAnimalEntity findLoaded(ServerLevel level, long animalId) {
        return ManagedAnimalRuntimeIndex.find(level, animalId);
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

    static EntityType<? extends BaseCoopAnimalEntity> resolveEntityType(String animalTypeId) {
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
        for (BaseCoopAnimalEntity entity : ManagedAnimalRuntimeIndex.snapshot(level)) {
            long managedId = entity.getManagedAnimalId();
            if (managedId > 0L) {
                byManagedId.put(managedId, entity);
            }
        }
        return new CollectionState(byManagedId);
    }

    private record CollectionState(Map<Long, BaseCoopAnimalEntity> byManagedId) {
    }
}
