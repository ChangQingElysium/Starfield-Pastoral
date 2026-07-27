package com.stardew.craft.manager;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalGrowthManagerPersistenceTest {
    @Test
    void reproductionCheckpointSurvivesReload() {
        CompoundTag source = new CompoundTag();
        source.putInt("lastReproductionProcessedAbsDay", 87);

        AnimalGrowthManager loaded = AnimalGrowthManager.load(source, null);
        CompoundTag saved = loaded.save(new CompoundTag(), null);

        assertEquals(87, saved.getInt("lastReproductionProcessedAbsDay"));
    }

    @Test
    void legacySaveUsesMigrationSentinelInsteadOfReplayingCurrentDay() {
        AnimalGrowthManager loaded =
                AnimalGrowthManager.load(new CompoundTag(), null);
        CompoundTag saved = loaded.save(new CompoundTag(), null);

        assertEquals(-1, saved.getInt("lastReproductionProcessedAbsDay"));
    }
}
