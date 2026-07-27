package com.stardew.craft.animal.data;

import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.model.AnimalProduceLedgerEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalProduceLedgerTest {
    private static final ResourceLocation EGG =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "egg_white");

    @Test
    void entryRoundTripPreservesProjectionIdentity() {
        AnimalProduceLedgerEntry original = new AnimalProduceLedgerEntry(
                7L,
                "coop_1",
                12L,
                44,
                EGG,
                2,
                "stardewcraft:farm",
                new BlockPos(4, 65, -8).asLong()
        );

        AnimalProduceLedgerEntry loaded =
                AnimalProduceLedgerEntry.load(original.save());

        assertEquals(original, loaded);
        assertTrue(loaded.isProjected());
        assertEquals(new BlockPos(4, 65, -8), loaded.projectedPos());
        assertFalse(loaded.withoutProjection().isProjected());
    }

    @Test
    void worldDataPersistsPendingAndProjectedProductsWithoutCollapsingDuplicates() {
        AnimalWorldData data = worldDataWithCoop();
        assertEquals(2, data.submitAnimalProduce(
                "coop_1",
                12L,
                44,
                EGG,
                1,
                2
        ).size());
        assertTrue(data.markAnimalProduceProjected(
                1L,
                "stardewcraft:farm",
                new BlockPos(1, 65, 1)
        ));

        CompoundTag saved = data.save(new CompoundTag(), null);
        AnimalWorldData loaded = AnimalWorldData.load(saved, null);

        assertEquals(2, loaded.getAnimalProduceLedger().size());
        assertTrue(loaded.getAnimalProduce(1L).orElseThrow().isProjected());
        assertFalse(loaded.getAnimalProduce(2L).orElseThrow().isProjected());
        assertEquals(1, loaded.getAnimalProduce(1L).orElseThrow().quality());

        assertEquals(1, loaded.submitAnimalProduce(
                "coop_1",
                12L,
                45,
                EGG,
                0,
                1
        ).size());
        assertTrue(loaded.getAnimalProduce(3L).isPresent());
    }

    @Test
    void projectionCanOnlyBeReleasedByItsExactOwningView() {
        AnimalWorldData data = worldDataWithCoop();
        data.submitAnimalProduce("coop_1", 12L, 44, EGG, 0, 1);
        BlockPos projection = new BlockPos(2, 65, 2);
        data.markAnimalProduceProjected(
                1L,
                "stardewcraft:farm",
                projection
        );

        assertFalse(data.releaseAnimalProduceProjection(
                1L,
                "stardewcraft:farm",
                projection.above()
        ));
        assertTrue(data.getAnimalProduce(1L).orElseThrow().isProjected());

        assertTrue(data.releaseAnimalProduceProjection(
                1L,
                "stardewcraft:farm",
                projection
        ));
        assertFalse(data.getAnimalProduce(1L).orElseThrow().isProjected());
        assertTrue(data.completeAnimalProduce(1L));
        assertTrue(data.getAnimalProduceLedger().isEmpty());
    }

    @Test
    void ledgerFirstProjectionReservationCanRecoverAfterRestart() {
        AnimalWorldData data = worldDataWithCoop();
        data.submitAnimalProduce(
                "coop_1", 12L, 44, EGG, 0, 1);
        BlockPos interruptedProjection =
                new BlockPos(2, 65, 2);
        assertTrue(data.markAnimalProduceProjected(
                1L,
                "stardewcraft:farm",
                interruptedProjection
        ));

        AnimalWorldData restarted = AnimalWorldData.load(
                data.save(new CompoundTag(), null),
                null
        );
        assertTrue(restarted.releaseAnimalProduceProjection(
                1L,
                "stardewcraft:farm",
                interruptedProjection
        ));
        assertTrue(restarted.markAnimalProduceProjected(
                1L,
                "stardewcraft:farm",
                interruptedProjection.east()
        ));
        assertEquals(
                interruptedProjection.east(),
                restarted.getAnimalProduce(1L)
                        .orElseThrow()
                        .projectedPos()
        );
    }

    @Test
    void outdoorProducePersistsItsAnchorAndRejectsAutoCollection() {
        AnimalWorldData data = worldDataWithCoop();
        BlockPos anchor = new BlockPos(12, 64, -7);
        AnimalProduceLedgerEntry submitted = data.submitAnimalProduceNear(
                "coop_1",
                12L,
                44,
                EGG,
                0,
                1,
                "stardewcraft:farm",
                anchor,
                5
        ).getFirst();

        AnimalWorldData loaded = AnimalWorldData.load(
                data.save(new CompoundTag(), null),
                null
        );
        AnimalProduceLedgerEntry entry =
                loaded.getAnimalProduce(submitted.entryId()).orElseThrow();

        assertFalse(entry.autoCollectEligible());
        assertTrue(entry.hasPreferredAnchor());
        assertEquals("stardewcraft:farm", entry.preferredDimensionId());
        assertEquals(anchor, entry.preferredPos());
        assertEquals(5, entry.preferredRadius());
    }

    @Test
    void pendingBirthPromptSurvivesWorldDataReload() {
        AnimalWorldData data = worldDataWithCoop();
        String owner = UUID.randomUUID().toString();
        long eventId = data.queueAnimalBirth(
                owner,
                "coop_1",
                12L,
                "white_chicken",
                44
        ).eventId();

        AnimalWorldData loaded = AnimalWorldData.load(
                data.save(new CompoundTag(), null),
                null
        );

        assertEquals(
                eventId,
                loaded.getPendingBirthsForOwner(owner)
                        .getFirst()
                        .eventId()
        );
        assertEquals(1, loaded.getPendingBirthCountForBuilding("coop_1"));
        assertTrue(loaded.completePendingBirth(eventId));
    }

    private static AnimalWorldData worldDataWithCoop() {
        AnimalBuildingRecord building = new AnimalBuildingRecord(
                "coop_1",
                UUID.randomUUID().toString(),
                AnimalBuildingType.COOP_TIER_1,
                "Coop",
                "stardewcraft:farm",
                BlockPos.ZERO,
                8,
                -4,
                64,
                -4,
                4,
                68,
                4,
                4,
                0,
                true,
                false,
                Set.of(),
                Set.of(),
                Set.of(12L)
        );
        CompoundTag root = new CompoundTag();
        ListTag buildings = new ListTag();
        buildings.add(building.save());
        root.put("buildings", buildings);
        return AnimalWorldData.load(root, null);
    }
}
