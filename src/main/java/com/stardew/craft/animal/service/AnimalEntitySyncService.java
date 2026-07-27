package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypes;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

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

        // A missing building quarantines the authoritative record. Only its
        // loaded projection is removed; automatic reconciliation never
        // deletes animal gameplay data.
        java.util.LinkedHashSet<Long> orphanIds =
                new java.util.LinkedHashSet<>();
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
            StardewCraft.LOGGER.warn(
                    "[ANIMAL_SYNC] Preserved animal record {} in quarantine because its building is missing",
                    orphanId);
        }

        for (FarmAnimalRecord record : data.getAnimals()) {
            if (orphanIds.contains(record.animalId())) {
                continue;
            }
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
            if (record.updateProjectionAnchor(
                    level.dimension().location().toString(),
                    entity.blockPosition())) {
                data.markChanged();
            }
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
            ManagedAnimalRuntimeIndex.remove(level, existing);
            existing.discard();
        }
        return existing;
    }

    /** Rebuilds a loaded animal at its authoritative building after a home change. */
    public static BaseCoopAnimalEntity relocateNow(ServerLevel level, FarmAnimalRecord record) {
        removeLoaded(level, record.animalId());
        return ensurePresentNow(level, record);
    }

    public static int relocateBuildingAnimalsNow(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        AnimalWorldData data = AnimalWorldData.get(level);
        int relocated = 0;
        for (Long animalId : building.memberAnimalIds()) {
            FarmAnimalRecord record =
                    data.getAnimal(animalId).orElse(null);
            if (record != null
                    && relocateNow(level, record) != null) {
                relocated++;
            }
        }
        return relocated;
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
        if (record.hasProjectionAnchor()
                && level.dimension().location().toString()
                        .equals(record.projectionDimensionId())
                && !level.isLoaded(record.projectionPos())) {
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

        Entity created = ((EntityType<?>) type).create(level);
        if (!(created instanceof BaseCoopAnimalEntity entity)) {
            StardewCraft.LOGGER.warn(
                    "[ANIMAL_SYNC] Entity type for {} must create BaseCoopAnimalEntity, got {}",
                    record.animalTypeId(),
                    created == null ? "null" : created.getClass().getName()
            );
            return null;
        }

        BlockPos spawnPos = findSpawnPos(
                level, building, record);
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

    private static BlockPos findSpawnPos(
            ServerLevel level,
            AnimalBuildingRecord building,
            FarmAnimalRecord record
    ) {
        if (record.hasProjectionAnchor()
                && level.dimension().location().toString()
                        .equals(record.projectionDimensionId())) {
            BlockPos anchor = record.projectionPos();
            if (canStand(level, anchor)) {
                return anchor;
            }
        }
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

    @SuppressWarnings("unchecked")
    static EntityType<? extends BaseCoopAnimalEntity> resolveEntityType(String animalTypeId) {
        FarmAnimalDefinition definition = FarmAnimalDefinitions.find(animalTypeId);
        if (definition != null
                && BuiltInRegistries.ENTITY_TYPE.containsKey(definition.entityTypeId())) {
            // The created instance is checked in spawnEntityForRecord. This permits a code mod to
            // register its managed entity normally and bind it through the same data-pack schema.
            return (EntityType<? extends BaseCoopAnimalEntity>) (EntityType<?>)
                    BuiltInRegistries.ENTITY_TYPE.get(definition.entityTypeId());
        }
        return StardewAnimalTypes.entityType(animalTypeId);
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
