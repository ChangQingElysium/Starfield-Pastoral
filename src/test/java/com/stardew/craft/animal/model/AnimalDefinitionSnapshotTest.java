package com.stardew.craft.animal.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimalDefinitionSnapshotTest {
    @Test
    void bundledAnimalAndBuildingSnapshotsAreCrossCompatible() {
        assertDoesNotThrow(() ->
                AnimalDefinitionSnapshot.validateCrossReferences(
                        FarmAnimalDefinitions.currentSnapshot(),
                        AnimalBuildingTierDefinitions.currentSnapshot()
                ));
    }

    @Test
    void missingRequiredBuildingTierRejectsWholeCandidatePair() {
        var animals =
                FarmAnimalDefinitions.currentSnapshot();
        var buildings =
                new AnimalBuildingTierDefinitions.Snapshot(
                        Map.of(), 123L);

        assertThrows(
                IllegalArgumentException.class,
                () -> AnimalDefinitionSnapshot
                        .validateCrossReferences(
                                animals, buildings)
        );
    }
}
