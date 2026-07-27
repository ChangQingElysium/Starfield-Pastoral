package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Read-only server context supplied to an addon animal reproduction rule. */
public final class StardewAnimalReproductionContext {
    private final ServerLevel level;
    private final AnimalBuildingRecord building;
    private final FarmAnimalRecord animal;
    private final int absoluteDaysPlayed;

    public StardewAnimalReproductionContext(
            ServerLevel level,
            AnimalBuildingRecord building,
            FarmAnimalRecord animal,
            int absoluteDaysPlayed
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.building = Objects.requireNonNull(building, "building");
        this.animal = Objects.requireNonNull(animal, "animal");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
    }

    StardewAnimalReproductionContext(FarmAnimalRecord animal, int absoluteDaysPlayed) {
        this.level = null;
        this.building = null;
        this.animal = Objects.requireNonNull(animal, "animal");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
    }

    public ServerLevel level() {
        return level;
    }

    public long animalId() {
        return animal.animalId();
    }

    public String animalTypeId() {
        return animal.animalTypeId();
    }

    public String buildingId() {
        return animal.buildingId();
    }

    public String buildingFamily() {
        return building == null ? "" : building.buildingType().family();
    }

    public int absoluteDaysPlayed() {
        return absoluteDaysPlayed;
    }

    public int friendship() {
        return animal.friendship();
    }

    public int ageDays() {
        return animal.ageDays();
    }

    public int daysToMature() {
        return animal.daysToMature();
    }
}
