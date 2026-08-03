package com.stardew.craft.animal.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalWorldDataMigrationTest {
    @Test
    void repairsClickingPlayerOwnershipFromBuildingLocation() {
        AnimalWorldData loaded = AnimalWorldData.load(legacyFixture(), null);
        UUID farmOwner = UUID.fromString(
                "00000000-0000-0000-0000-000000000099");

        assertEquals(1, loaded.reconcileFarmOwnership(
                "stardewcraft:farm", ignored -> farmOwner));
        assertEquals(farmOwner.toString(), loaded.getBuilding("coop_9")
                .orElseThrow().ownerPlayerUuid());
        assertEquals(farmOwner.toString(), loaded.getAnimal(42L)
                .orElseThrow().ownerPlayerUuid());
        assertEquals(0, loaded.reconcileFarmOwnership(
                "stardewcraft:farm", ignored -> farmOwner));
    }

    @Test
    void legacyFixtureMigratesIdempotentlyWithoutIdReuse() {
        CompoundTag legacy = legacyFixture();

        AnimalWorldDataMigrations.MigrationResult first =
                AnimalWorldDataMigrations.migrate(legacy);
        AnimalWorldDataMigrations.MigrationResult second =
                AnimalWorldDataMigrations.migrate(first.tag());

        assertEquals(0, first.fromVersion());
        assertEquals(
                AnimalWorldDataMigrations.CURRENT_VERSION,
                first.toVersion());
        assertTrue(first.changed());
        assertFalse(second.changed());
        assertEquals(first.tag(), second.tag());
        assertFalse(legacy.contains(
                AnimalWorldDataMigrations.VERSION_FIELD));

        AnimalWorldData loaded =
                AnimalWorldData.load(legacy, null);
        var animal = loaded.getAnimal(42L).orElseThrow();
        assertTrue(animal.allowReproduction());
        assertEquals(-1L, animal.parentAnimalId());
        assertEquals("",
                animal.ownerPlayerUuid());
        assertEquals(
                java.util.Set.of(42L),
                loaded.getBuilding("coop_9")
                        .orElseThrow().memberAnimalIds());

        CompoundTag saved = new CompoundTag();
        loaded.save(saved, null);
        assertEquals(
                AnimalWorldDataMigrations.CURRENT_VERSION,
                saved.getInt(
                        AnimalWorldDataMigrations.VERSION_FIELD));
        assertEquals(43L, saved.getLong("nextAnimalId"));
        assertEquals(10L, saved.getLong("nextBuildingId"));
    }

    @Test
    void futureSchemaIsRejectedInsteadOfSilentlyDowngraded() {
        CompoundTag future = new CompoundTag();
        future.putInt(
                AnimalWorldDataMigrations.VERSION_FIELD,
                AnimalWorldDataMigrations.CURRENT_VERSION + 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> AnimalWorldDataMigrations.migrate(future));
    }

    @Test
    void duplicateAnimalIdsAreRejectedInsteadOfOverwritten() {
        CompoundTag root = legacyFixture();
        root.getList(
                "animals",
                net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(root.getList(
                        "animals",
                        net.minecraft.nbt.Tag.TAG_COMPOUND)
                        .getCompound(0).copy());

        assertThrows(
                IllegalArgumentException.class,
                () -> AnimalWorldData.load(root, null));
    }

    @Test
    void duplicateBuildingIdsAreRejectedInsteadOfOverwritten() {
        CompoundTag root = legacyFixture();
        root.getList(
                "buildings",
                net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(root.getList(
                        "buildings",
                        net.minecraft.nbt.Tag.TAG_COMPOUND)
                        .getCompound(0).copy());

        assertThrows(
                IllegalArgumentException.class,
                () -> AnimalWorldData.load(root, null));
    }

    @Test
    void exhaustedAnimalIdSpaceIsRejectedInsteadOfWrapping() {
        CompoundTag root = legacyFixture();
        root.getList(
                "animals",
                net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .putLong("animalId", Long.MAX_VALUE);

        assertThrows(
                IllegalArgumentException.class,
                () -> AnimalWorldData.load(root, null));
    }

    @Test
    void exhaustedStoredCounterCannotWrapDuringAllocation() {
        CompoundTag root = legacyFixture();
        root.putLong("nextPendingBirthId", Long.MAX_VALUE);
        AnimalWorldData loaded = AnimalWorldData.load(root, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> loaded.queueAnimalBirth(
                        "00000000-0000-0000-0000-000000000000",
                        "coop_9",
                        42L,
                        "white_chicken",
                        1));
    }

    private static CompoundTag legacyFixture() {
        String path =
                "/fixtures/animal/legacy_world_schema_v0.snbt";
        try (InputStream stream =
                     AnimalWorldDataMigrationTest.class
                             .getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError(
                        "Missing migration fixture " + path);
            }
            return TagParser.parseTag(
                    new String(
                            stream.readAllBytes(),
                            StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
