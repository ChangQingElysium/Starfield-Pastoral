package com.stardew.craft.animal.service;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;

/** Read-only projections shared by the coop and barn manager menus. */
public final class AnimalBuildingViewService {
    private AnimalBuildingViewService() {
    }

    public static int managedAnimalCount(
            AnimalWorldData worldData,
            AnimalBuildingRecord building
    ) {
        long recordCount = worldData.getAnimals().stream()
                .filter(animal -> building.buildingId().equals(animal.buildingId()))
                .count();
        return Math.max(building.memberAnimalIds().size(), Math.toIntExact(recordCount));
    }
}
