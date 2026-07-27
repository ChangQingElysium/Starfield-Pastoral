package com.stardew.craft.animal.model;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalBuildingAutomationCheckpointTest {
    @Test
    void automaticFeedDayCheckpointSurvivesSaveAndLoad() {
        AnimalBuildingRecord building = building();
        building.setLastAutoFeedProcessedAbsDay(73);

        AnimalBuildingRecord loaded =
                AnimalBuildingRecord.load(building.save());

        assertEquals(73, loaded.lastAutoFeedProcessedAbsDay());
    }

    @Test
    void legacyBuildingStartsBeforeAnyAutomaticFeedDay() {
        AnimalBuildingRecord loaded =
                AnimalBuildingRecord.load(building().save());

        assertEquals(-1, loaded.lastAutoFeedProcessedAbsDay());
    }

    @Test
    void invalidStructurePersistsItsLastGoodSnapshotAndPausesGameplay() {
        AnimalBuildingRecord building = building();
        building.markStructureValidated(7L);
        building.markStructureInvalid("missing door");

        AnimalBuildingRecord loaded =
                AnimalBuildingRecord.load(building.save());

        assertEquals(8L, loaded.structureRevision());
        assertEquals(
                AnimalBuildingRecord.ValidationState.INVALID,
                loaded.validationState()
        );
        assertEquals("missing door", loaded.validationIssue());
        assertFalse(loaded.isGameplayEnabled());
        assertEquals(building.interiorAirCells(), loaded.interiorAirCells());
    }

    @Test
    void validatedBuildingExposesImmutableSourceCapabilities() {
        AnimalBuildingRecord building = building();
        AnimalBuildingCapabilities capabilities = building.capabilities();

        assertTrue(building.isGameplayEnabled());
        assertEquals("coop", capabilities.family());
        assertEquals(3, capabilities.tier());
        assertEquals(12, capabilities.animalCapacity());
        assertTrue(capabilities.automaticFeed());
        assertFalse(capabilities.allowsPregnancy());
    }

    @Test
    void structureInvalidationExcludesInteriorUtilityAndProduceCells() {
        AnimalBuildingRecord building = building();

        assertFalse(building.isStructuralCell(new BlockPos(0, 65, 0)));
        assertTrue(building.isStructuralCell(new BlockPos(4, 65, 0)));
        assertTrue(building.isStructuralCell(new BlockPos(4, 64, 0)));
        assertTrue(building.isStructuralCell(new BlockPos(5, 65, 0)));
        assertFalse(building.isStructuralCell(new BlockPos(20, 65, 0)));
    }

    @Test
    void constructionDeadlinePersistsAndKeepsGameplayPausedUntilDue() {
        AnimalBuildingRecord building = building();
        building.beginConstruction(42);

        AnimalBuildingRecord loaded =
                AnimalBuildingRecord.load(building.save());

        assertEquals(
                AnimalBuildingRecord.ValidationState.CONSTRUCTING,
                loaded.validationState());
        assertEquals(42, loaded.constructionCompletesAbsDay());
        assertTrue(loaded.hasPendingConstruction());
        assertFalse(loaded.isGameplayEnabled());
        assertFalse(loaded.completeConstruction(41));
        assertTrue(loaded.completeConstruction(42));
        assertFalse(loaded.hasPendingConstruction());
        assertTrue(loaded.isGameplayEnabled());
    }

    @Test
    void utilityScanCellIncludesAdjacentDeviceLayer() {
        AnimalBuildingRecord building = building();

        assertTrue(building.isUtilityScanCell(new BlockPos(5, 65, 0)));
        assertFalse(building.isUtilityScanCell(new BlockPos(6, 65, 0)));
    }

    private static AnimalBuildingRecord building() {
        return new AnimalBuildingRecord(
                "deluxe_coop",
                "",
                AnimalBuildingType.COOP_TIER_3,
                "Deluxe Coop",
                "stardewcraft:farm",
                BlockPos.ZERO,
                8,
                -4,
                64,
                -4,
                4,
                68,
                4,
                12,
                12,
                true,
                false,
                Set.of(new BlockPos(0, 65, 0).asLong()),
                Set.of(new BlockPos(5, 65, 0).asLong()),
                Set.of()
        );
    }
}
