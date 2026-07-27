package com.stardew.craft.animal.service;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewBuildingData;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalNameRules;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
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

        String finalName = resolveName(
                animalTypeId, customName);

        FarmAnimalRecord record = worldData.createAnimal(animalTypeId, finalName, buildingId, AnimalAcquisitionSource.PURCHASE);
        requireProjectionOrRollback(level, worldData, record);
        return record;
    }

    public static FarmAnimalRecord pregnancy(ServerLevel level,
                                             String animalTypeId,
                                             String buildingId) {
        AnimalWorldData worldData = AnimalWorldData.get(level);
        AnimalBuildingRecord building = worldData.getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));

        validateBuilding(level, animalTypeId, building);
        FarmAnimalRecord record = worldData.createAnimal(
                animalTypeId,
                resolveName(animalTypeId, null),
                buildingId,
                AnimalAcquisitionSource.PREGNANCY);
        requireProjectionOrRollback(level, worldData, record);
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
        String finalName = resolveName(
                animalTypeId, customName);
        FarmAnimalRecord record = worldData.createAnimal(animalTypeId, finalName, buildingId, AnimalAcquisitionSource.INCUBATION);
        requireProjectionOrRollback(level, worldData, record);
        return record;
    }

    private static void validateBuilding(ServerLevel level, String animalTypeId, AnimalBuildingRecord building) {
        String family = AnimalTypeCatalog.require(animalTypeId).family();
        if (!building.buildingType().family().equals(family)) {
            throw new IllegalStateException("Animal type " + animalTypeId
                + " requires " + family + " but building is " + building.buildingType().family());
        }

        EntityType<? extends com.stardew.craft.entity.animal.BaseCoopAnimalEntity> entityType =
                AnimalEntitySyncService.resolveEntityType(animalTypeId);
        if (entityType == null) {
            throw new IllegalStateException(
                    "Animal type " + animalTypeId + " has no registered managed entity type");
        }

        StardewBuildingData publicData = StardewAgricultureDataApi.building(
                level, building.managerPos(), level.getBlockState(building.managerPos()));
        if (publicData == null || publicData.acceptedAnimals().isEmpty()) {
            return;
        }
        if (!publicData.acceptedAnimals().contains(BuiltInRegistries.ENTITY_TYPE.getKey(entityType))) {
            throw new IllegalStateException("Animal type " + animalTypeId
                    + " is not accepted by building " + building.buildingId());
        }
    }

    private static String defaultName(String animalTypeId) {
        var definition = FarmAnimalDefinitions.find(animalTypeId);
        return definition == null ? animalTypeId : definition.defaultName();
    }

    private static String resolveName(
            String animalTypeId,
            String requestedName
    ) {
        String normalized =
                AnimalNameRules.normalize(requestedName);
        String resolved = normalized.isBlank()
                ? defaultName(animalTypeId)
                : normalized;
        if (!AnimalNameRules.isValidExplicitName(resolved)) {
            throw new IllegalArgumentException(
                    "Invalid animal name");
        }
        return resolved;
    }

    private static void requireProjectionOrRollback(
            ServerLevel level,
            AnimalWorldData worldData,
            FarmAnimalRecord record
    ) {
        try {
            if (AnimalEntitySyncService.ensurePresentNow(
                    level, record) != null) {
                return;
            }
            throw new IllegalStateException(
                    "Animal type " + record.animalTypeId()
                            + " could not create a managed entity");
        } catch (RuntimeException exception) {
            AnimalEntitySyncService.removeLoaded(
                    level, record.animalId());
            worldData.removeAnimal(record.animalId());
            throw exception;
        }
    }
}
