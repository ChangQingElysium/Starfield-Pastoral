package com.stardew.craft.animal.data;

import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalBuildingLifecycleTest {
    private static final UUID OWNER =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000007");

    @Test
    void loadRepairsMembershipAndConstructionCompletionSkipsPausedDays() {
        AnimalBuildingRecord coop = building(
                "coop_a", BlockPos.ZERO, Set.of(999L));
        coop.beginConstruction(20);
        FarmAnimalRecord animal = animal(7L, "coop_a");
        animal.setLastProcessedAbsDay(12);

        AnimalWorldData data = load(coop, animal);

        assertEquals(Set.of(7L),
                data.getBuildingIncludingInactive("coop_a")
                        .orElseThrow().memberAnimalIds());
        assertTrue(data.completeDueConstructions(19).isEmpty());
        assertEquals(1, data.completeDueConstructions(20).size());
        assertEquals(20,
                data.getAnimal(7L).orElseThrow()
                        .lastProcessedAbsDay());
        assertTrue(data.getBuilding("coop_a").isPresent());
    }

    @Test
    void pausedSourceCannotPartiallyMoveAnimal() {
        AnimalBuildingRecord source = building(
                "coop_a", BlockPos.ZERO, Set.of(7L));
        source.markStructureInvalid("wall changed");
        AnimalBuildingRecord target = building(
                "coop_b", new BlockPos(20, 64, 0), Set.of());
        AnimalWorldData data = load(
                new AnimalBuildingRecord[]{source, target},
                new FarmAnimalRecord[]{animal(7L, "coop_a")});

        assertFalse(data.moveAnimalToBuilding(
                7L, "coop_b", null));
        assertEquals("coop_a",
                data.getAnimal(7L).orElseThrow().buildingId());
        assertEquals(Set.of(7L),
                data.getBuildingIncludingInactive("coop_a")
                        .orElseThrow().memberAnimalIds());
        assertTrue(data.getBuildingIncludingInactive("coop_b")
                .orElseThrow().memberAnimalIds().isEmpty());
    }

    @Test
    void validatedManagerRebindIsRevisionGuardedAndAtomic() {
        AnimalBuildingRecord source = building(
                "coop_a", BlockPos.ZERO, Set.of(7L));
        AnimalWorldData data = load(
                source, animal(7L, "coop_a"));
        BlockPos destination = new BlockPos(40, 64, 0);

        assertFalse(data.rebindValidatedBuildingManager(
                "coop_a",
                OWNER,
                "stardewcraft:farm",
                destination,
                "coop",
                99L,
                36, 64, -4,
                44, 68, 4,
                Set.of(),
                Set.of(),
                4));
        AnimalBuildingRecord unchanged = data
                .getBuilding("coop_a").orElseThrow();
        assertEquals(BlockPos.ZERO, unchanged.managerPos());
        assertEquals(1L, unchanged.structureRevision());

        assertTrue(data.rebindValidatedBuildingManager(
                "coop_a",
                OWNER,
                "stardewcraft:farm",
                destination,
                "coop",
                1L,
                36, 64, -4,
                44, 68, 4,
                Set.of(),
                Set.of(),
                4));
        AnimalBuildingRecord rebound = data
                .getBuilding("coop_a").orElseThrow();
        assertEquals(destination, rebound.managerPos());
        assertEquals(2L, rebound.structureRevision());
        assertEquals(Set.of(7L), rebound.memberAnimalIds());
        assertEquals("coop_a",
                data.getAnimal(7L).orElseThrow().buildingId());
    }

    private static AnimalBuildingRecord building(
            String id,
            BlockPos manager,
            Set<Long> members
    ) {
        return new AnimalBuildingRecord(
                id,
                OWNER.toString(),
                AnimalBuildingType.COOP_TIER_1,
                id,
                "stardewcraft:farm",
                manager,
                4,
                manager.getX() - 4,
                64,
                manager.getZ() - 4,
                manager.getX() + 4,
                68,
                manager.getZ() + 4,
                4,
                0,
                true,
                false,
                Set.of(),
                Set.of(),
                members);
    }

    private static FarmAnimalRecord animal(
            long id,
            String buildingId
    ) {
        return new FarmAnimalRecord(
                id,
                "white_chicken",
                "Hen",
                buildingId,
                AnimalAcquisitionSource.PURCHASE,
                1,
                0,
                1,
                5,
                3);
    }

    private static AnimalWorldData load(
            AnimalBuildingRecord building,
            FarmAnimalRecord animal
    ) {
        return load(
                new AnimalBuildingRecord[]{building},
                new FarmAnimalRecord[]{animal});
    }

    private static AnimalWorldData load(
            AnimalBuildingRecord[] buildings,
            FarmAnimalRecord[] animals
    ) {
        CompoundTag root = new CompoundTag();
        ListTag buildingTags = new ListTag();
        for (AnimalBuildingRecord building : buildings) {
            buildingTags.add(building.save());
        }
        root.put("buildings", buildingTags);
        ListTag animalTags = new ListTag();
        for (FarmAnimalRecord animal : animals) {
            animalTags.add(animal.save());
        }
        root.put("animals", animalTags);
        return AnimalWorldData.load(root, null);
    }
}
