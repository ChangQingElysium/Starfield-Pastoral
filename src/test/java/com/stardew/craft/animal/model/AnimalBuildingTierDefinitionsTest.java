package com.stardew.craft.animal.model;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalBuildingTierDefinitionsTest {
    @Test
    void sourceCostsAndConstructionDaysMatchBundledBuildingsData() {
        Map<String, int[]> expected = Map.of(
                "coop:1", values(3, 4000, 300, 100),
                "coop:2", values(2, 10000, 400, 150),
                "coop:3", values(2, 20000, 500, 200),
                "barn:1", values(3, 6000, 350, 150),
                "barn:2", values(2, 12000, 450, 200),
                "barn:3", values(2, 25000, 550, 300));

        expected.forEach((key, values) -> {
            String[] parts = key.split(":");
            AnimalBuildingTierDefinition definition =
                    AnimalBuildingTierDefinitions.require(
                            parts[0],
                            Integer.parseInt(parts[1]));
            assertEquals(values[0], definition.buildDays(), key);
            assertEquals(values[1], definition.money(), key);
            assertEquals(values[2],
                    definition.materials().get(0).count(), key);
            assertEquals(values[3],
                    definition.materials().get(1).count(), key);
        });
    }

    @Test
    void capabilitiesComeFromTheDataPackDefinition() {
        AnimalBuildingTierDefinition bigBarn =
                AnimalBuildingTierDefinitions.require("barn", 2);
        AnimalBuildingTierDefinition deluxeCoop =
                AnimalBuildingTierDefinitions.require("coop", 3);

        assertEquals(8, bigBarn.capacity());
        assertTrue(bigBarn.allowsPregnancy());
        assertFalse(bigBarn.automaticFeed());
        assertEquals(12, deluxeCoop.capacity());
        assertFalse(deluxeCoop.allowsPregnancy());
        assertTrue(deluxeCoop.automaticFeed());
    }

    @Test
    void reloadValidationRejectsUnknownMaterialItems() {
        ResourceLocation dataId =
                ResourceLocation.fromNamespaceAndPath(
                        "example", "unknown_material");
        AnimalBuildingTierDefinition definition =
                new AnimalBuildingTierDefinition(
                        dataId,
                        false,
                        "coop",
                        1,
                        4,
                        0,
                        false,
                        false,
                        3,
                        4000,
                        List.of(new AnimalBuildingTierDefinition.Material(
                                ResourceLocation.fromNamespaceAndPath(
                                        "example", "missing_item"),
                                100)));

        assertThrows(
                IllegalArgumentException.class,
                () -> AnimalBuildingTierDefinitions
                        .validateRuntimeReferences(
                                new AnimalBuildingTierDefinitions.Snapshot(
                                        Map.of("coop:1", definition),
                                        1L)));
    }

    private static int[] values(
            int days,
            int money,
            int wood,
            int stone
    ) {
        return new int[]{days, money, wood, stone};
    }
}
