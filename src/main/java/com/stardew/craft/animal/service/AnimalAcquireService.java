package com.stardew.craft.animal.service;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewBuildingData;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

public final class AnimalAcquireService {
    private AnimalAcquireService() {}

    public static FarmAnimalRecord purchase(ServerLevel level,
                                            String animalTypeId,
                                            String customName,
                                            String buildingId) {
        AnimalWorldData worldData = AnimalWorldData.get(level);
        AnimalBuildingRecord building = worldData.getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));

        validateBuilding(level, animalTypeId, building);

        String finalName = (customName == null || customName.isBlank())
            ? defaultName(animalTypeId)
            : customName;

        FarmAnimalRecord record = worldData.createAnimal(animalTypeId, finalName, buildingId, AnimalAcquisitionSource.PURCHASE);
        ensureBuildingChunkLoaded(level, building);
        AnimalEntitySyncService.spawnOrSyncSingle(level, record);
        return record;
    }

    public static FarmAnimalRecord pregnancy(ServerLevel level,
                                             String animalTypeId,
                                             String buildingId) {
        AnimalWorldData worldData = AnimalWorldData.get(level);
        AnimalBuildingRecord building = worldData.getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));

        validateBuilding(level, animalTypeId, building);
        FarmAnimalRecord record = worldData.createAnimal(animalTypeId, defaultName(animalTypeId), buildingId, AnimalAcquisitionSource.PREGNANCY);
        ensureBuildingChunkLoaded(level, building);
        AnimalEntitySyncService.spawnOrSyncSingle(level, record);
        return record;
    }

    public static FarmAnimalRecord incubation(ServerLevel level,
                                              String animalTypeId,
                                              String buildingId) {
        return incubation(level, animalTypeId, null, buildingId);
    }

    public static FarmAnimalRecord incubation(ServerLevel level,
                                              String animalTypeId,
                                              String customName,
                                              String buildingId) {
        AnimalWorldData worldData = AnimalWorldData.get(level);
        AnimalBuildingRecord building = worldData.getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));

        validateBuilding(level, animalTypeId, building);
        String finalName = (customName == null || customName.isBlank())
            ? defaultName(animalTypeId)
            : customName;
        FarmAnimalRecord record = worldData.createAnimal(animalTypeId, finalName, buildingId, AnimalAcquisitionSource.INCUBATION);
        ensureBuildingChunkLoaded(level, building);
        AnimalEntitySyncService.spawnOrSyncSingle(level, record);
        return record;
    }

    private static void ensureBuildingChunkLoaded(ServerLevel level, AnimalBuildingRecord building) {
        if (level == null || building == null) {
            return;
        }
        int chunkX = building.managerPos().getX() >> 4;
        int chunkZ = building.managerPos().getZ() >> 4;
        level.getChunk(chunkX, chunkZ);
    }

    private static void validateBuilding(ServerLevel level, String animalTypeId, AnimalBuildingRecord building) {
        String family = AnimalTypeCatalog.resolve(animalTypeId).family();
        if (!building.buildingType().family().equals(family)) {
            throw new IllegalStateException("Animal type " + animalTypeId
                + " requires " + family + " but building is " + building.buildingType().family());
        }

        StardewBuildingData publicData = StardewAgricultureDataApi.building(
                level, building.managerPos(), level.getBlockState(building.managerPos()));
        if (publicData == null || publicData.acceptedAnimals().isEmpty()) {
            return;
        }
        EntityType<? extends com.stardew.craft.entity.animal.BaseCoopAnimalEntity> entityType =
                AnimalEntitySyncService.resolveEntityType(animalTypeId);
        if (entityType == null || !publicData.acceptedAnimals().contains(BuiltInRegistries.ENTITY_TYPE.getKey(entityType))) {
            throw new IllegalStateException("Animal type " + animalTypeId
                    + " is not accepted by building " + building.buildingId());
        }
    }

    private static String defaultName(String animalTypeId) {
        return animalTypeId;
    }
}
