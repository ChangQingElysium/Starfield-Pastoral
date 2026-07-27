package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalNameRules;
import com.stardew.craft.animal.model.AnimalPendingBirth;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative completion transaction for an overnight animal birth. */
public final class AnimalBirthService {
    private AnimalBirthService() {
    }

    public static ClaimResult claim(
            ServerLevel level,
            ServerPlayer player,
            long eventId,
            String requestedName
    ) {
        AnimalWorldData data = AnimalWorldData.get(level);
        AnimalPendingBirth event = data.getPendingBirth(eventId).orElse(null);
        if (event == null) {
            return ClaimResult.NOT_FOUND;
        }
        if (!event.ownerPlayerUuid().equals(player.getUUID().toString())) {
            return ClaimResult.NOT_OWNER;
        }
        String name = AnimalNameRules.normalize(requestedName);
        if (!AnimalNameRules.isValidExplicitName(name)) {
            return ClaimResult.INVALID_NAME;
        }
        if (data.hasAnyAnimalWithName(name)) {
            return ClaimResult.DUPLICATE_NAME;
        }
        AnimalBuildingRecord building =
                data.getBuilding(event.buildingId()).orElse(null);
        if (building == null || !building.hasCapacity()) {
            return ClaimResult.HOME_UNAVAILABLE;
        }
        if (AnimalTypeCatalog.find(event.animalTypeId()) == null) {
            return ClaimResult.TYPE_UNAVAILABLE;
        }

        FarmAnimalRecord baby = null;
        try {
            baby = data.createAnimal(
                    event.animalTypeId(),
                    name,
                    event.buildingId(),
                    AnimalAcquisitionSource.PREGNANCY
            );
            baby.setParentAnimalId(event.parentAnimalId());
            FarmAnimalRecord parent =
                    data.getAnimal(event.parentAnimalId()).orElse(null);
            baby.setOwnerPlayerUuid(
                    parent != null
                            && !parent.ownerPlayerUuid().isBlank()
                            ? parent.ownerPlayerUuid()
                            : event.ownerPlayerUuid()
            );
            if (AnimalEntitySyncService.ensurePresentNow(
                    level, baby) == null) {
                throw new IllegalStateException(
                        "Could not project newborn animal "
                                + baby.animalId());
            }
        } catch (RuntimeException exception) {
            if (baby != null) {
                AnimalEntitySyncService.removeLoaded(
                        level, baby.animalId());
                data.removeAnimal(baby.animalId());
            }
            StardewCraft.LOGGER.error(
                    "[ANIMAL_BIRTH] Kept pending birth {} because animal projection failed",
                    eventId,
                    exception);
            return ClaimResult.TYPE_UNAVAILABLE;
        }
        data.completePendingBirth(eventId);
        return ClaimResult.CREATED;
    }

    public enum ClaimResult {
        CREATED,
        NOT_FOUND,
        NOT_OWNER,
        INVALID_NAME,
        DUPLICATE_NAME,
        HOME_UNAVAILABLE,
        TYPE_UNAVAILABLE
    }
}
