package com.stardew.craft.animal.model;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAnimalRecordTest {
    @Test
    void newAnimalsAllowReproductionAndPersistOwnershipAndParentage() {
        FarmAnimalRecord record = new FarmAnimalRecord(
                42L,
                "cow",
                "Bessie",
                "barn-1",
                AnimalAcquisitionSource.PURCHASE,
                1,
                0,
                1,
                0,
                5
        );
        record.setOwnerPlayerUuid("00000000-0000-0000-0000-000000000001");
        record.setParentAnimalId(12L);

        assertTrue(record.allowReproduction());

        FarmAnimalRecord loaded = FarmAnimalRecord.load(record.save());
        assertEquals(record.ownerPlayerUuid(), loaded.ownerPlayerUuid());
        assertEquals(12L, loaded.parentAnimalId());
        assertTrue(loaded.allowReproduction());
    }

    @Test
    void projectionAnchorPersistsButCanBeClearedForHomeChanges() {
        FarmAnimalRecord record = new FarmAnimalRecord(
                8L,
                "white_chicken",
                "Hen",
                "coop-1",
                AnimalAcquisitionSource.PURCHASE,
                1,
                0,
                1,
                3,
                3);
        BlockPos anchor = new BlockPos(12, 65, -4);

        assertTrue(record.updateProjectionAnchor(
                "stardewcraft:farm", anchor));
        assertFalse(record.updateProjectionAnchor(
                "stardewcraft:farm", anchor));

        FarmAnimalRecord loaded =
                FarmAnimalRecord.load(record.save());
        assertTrue(loaded.hasProjectionAnchor());
        assertEquals("stardewcraft:farm",
                loaded.projectionDimensionId());
        assertEquals(anchor, loaded.projectionPos());
        assertTrue(loaded.clearProjectionAnchor());
        assertFalse(loaded.hasProjectionAnchor());
    }

    @Test
    void absentAddonAnimalTypeRoundTripsWithoutBeingRewritten() {
        FarmAnimalRecord record = new FarmAnimalRecord(
                91L,
                "absent_addon:goose",
                "Archived Goose",
                "coop-legacy",
                AnimalAcquisitionSource.PURCHASE,
                1,
                17,
                3,
                5,
                4);
        record.setOwnerPlayerUuid(
                "00000000-0000-0000-0000-000000000091");
        record.setCurrentProduceId(
                "absent_addon:goose_egg");

        FarmAnimalRecord loaded =
                FarmAnimalRecord.load(record.save());

        assertEquals(
                "absent_addon:goose",
                loaded.animalTypeId());
        assertEquals(
                "absent_addon:goose_egg",
                loaded.currentProduceId());
        assertEquals(record.save(), loaded.save());
    }
}
