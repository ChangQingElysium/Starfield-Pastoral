package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.server.level.ServerLevel;

/**
 * Transaction boundary for changing an animal's authoritative home and its
 * loaded entity projection.
 */
public final class AnimalHomeTransferService {
    private AnimalHomeTransferService() {
    }

    public enum Result {
        MOVED,
        REJECTED,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }

    public static Result transfer(
            ServerLevel level,
            long animalId,
            String targetBuildingId,
            String operatorPlayerUuid
    ) {
        AnimalWorldData data = AnimalWorldData.get(level);
        FarmAnimalRecord animal = data.getAnimal(animalId).orElse(null);
        if (animal == null) {
            return Result.REJECTED;
        }
        String sourceBuildingId = animal.buildingId();
        AnimalBuildingRecord source = data
                .getBuilding(sourceBuildingId).orElse(null);
        AnimalBuildingRecord target = data
                .getBuilding(targetBuildingId).orElse(null);
        String currentDimension =
                level.dimension().location().toString();
        if (source == null || target == null
                || !currentDimension.equals(source.dimensionId())
                || !currentDimension.equals(target.dimensionId())) {
            return Result.REJECTED;
        }

        if (!data.moveAnimalToBuilding(
                animalId, targetBuildingId, operatorPlayerUuid)) {
            return Result.REJECTED;
        }
        FarmAnimalRecord moved = data.getAnimal(animalId).orElse(null);
        if (moved != null
                && AnimalEntitySyncService.relocateNow(level, moved)
                        != null) {
            return Result.MOVED;
        }

        boolean restored = data.moveAnimalToBuilding(
                animalId, sourceBuildingId, null);
        if (!restored) {
            StardewCraft.LOGGER.error(
                    "[ANIMAL_HOME_TRANSFER] Failed to roll back animal {} from {} to {}",
                    animalId,
                    targetBuildingId,
                    sourceBuildingId);
            return Result.ROLLBACK_FAILED;
        }
        data.getAnimal(animalId).ifPresent(record ->
                AnimalEntitySyncService.relocateNow(level, record));
        StardewCraft.LOGGER.warn(
                "[ANIMAL_HOME_TRANSFER] Rolled back animal {} because target projection failed",
                animalId);
        return Result.ROLLED_BACK;
    }
}
