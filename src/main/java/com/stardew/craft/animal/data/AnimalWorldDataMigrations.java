package com.stardew.craft.animal.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Explicit, idempotent migrations for the authoritative animal-world save.
 *
 * <p>Version 0 is every save written before the parity refactor. Migrations only add fields whose
 * legacy meaning is known; they never invent produce, parentage or world projections.
 */
public final class AnimalWorldDataMigrations {
    public static final String VERSION_FIELD =
            "animalSchemaVersion";
    public static final int CURRENT_VERSION = 3;

    private AnimalWorldDataMigrations() {
    }

    public static MigrationResult migrate(CompoundTag source) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "Animal save tag must not be null");
        }
        CompoundTag migrated = source.copy();
        int fromVersion = migrated.contains(VERSION_FIELD, Tag.TAG_INT)
                ? migrated.getInt(VERSION_FIELD)
                : 0;
        if (fromVersion < 0) {
            throw new IllegalArgumentException(
                    "Animal save schema version must not be negative");
        }
        if (fromVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Animal save schema " + fromVersion
                            + " is newer than supported schema "
                            + CURRENT_VERSION);
        }

        int version = fromVersion;
        while (version < CURRENT_VERSION) {
            switch (version) {
                case 0 -> migrateLegacyRecordsToV1(migrated);
                case 1 -> migrateLifecycleCollectionsToV2(migrated);
                case 2 -> migrateProjectionAndAddonStateToV3(migrated);
                default -> throw new IllegalStateException(
                        "Missing animal migration from schema "
                                + version);
            }
            version++;
            migrated.putInt(VERSION_FIELD, version);
        }
        return new MigrationResult(
                migrated,
                fromVersion,
                version,
                fromVersion != version
        );
    }

    private static void migrateLegacyRecordsToV1(
            CompoundTag root
    ) {
        ListTag animals = ensureList(root, "animals");
        for (int i = 0; i < animals.size(); i++) {
            CompoundTag animal = animals.getCompound(i);
            putStringIfMissing(
                    animal, "acquisitionSource", "PURCHASE");
            putStringIfMissing(
                    animal, "ownerPlayerUuid", "");
            putLongIfMissing(animal, "parentAnimalId", -1L);
            putBooleanIfMissing(
                    animal, "allowReproduction", true);
            putIntIfMissing(
                    animal, "lastProcessedAbsDay", 0);
        }

        ListTag buildings = ensureList(root, "buildings");
        for (int i = 0; i < buildings.size(); i++) {
            CompoundTag building = buildings.getCompound(i);
            putLongIfMissing(
                    building, "structureRevision", 1L);
            putIntIfMissing(
                    building,
                    "lastAutoFeedProcessedAbsDay",
                    -1);
            if (!building.contains(
                    "validationState", Tag.TAG_STRING)) {
                boolean active = !building.contains("active")
                        || building.getBoolean("active");
                building.putString(
                        "validationState",
                        active ? "VALID" : "RELOCATING");
            }
        }
    }

    private static void migrateLifecycleCollectionsToV2(
            CompoundTag root
    ) {
        ensureList(root, "animalProduceLedger");
        ensureList(root, "pendingAnimalBirths");
        ensureList(root, "hayByOwner");
        putLongIfMissing(root, "nextBuildingId", 1L);
        putLongIfMissing(root, "nextAnimalId", 1L);
        putLongIfMissing(
                root, "nextProduceLedgerId", 1L);
        putLongIfMissing(
                root, "nextPendingBirthId", 1L);
    }

    private static void migrateProjectionAndAddonStateToV3(
            CompoundTag root
    ) {
        // Projection anchors and addonData are optional by design. This
        // version boundary records that their absence means "not projected"
        // and "no addon state", rather than an incomplete migration.
        ensureList(root, "animals");
    }

    private static ListTag ensureList(
            CompoundTag root,
            String field
    ) {
        if (!root.contains(field, Tag.TAG_LIST)) {
            root.put(field, new ListTag());
        }
        return root.getList(field, Tag.TAG_COMPOUND);
    }

    private static void putStringIfMissing(
            CompoundTag tag,
            String field,
            String value
    ) {
        if (!tag.contains(field, Tag.TAG_STRING)) {
            tag.putString(field, value);
        }
    }

    private static void putIntIfMissing(
            CompoundTag tag,
            String field,
            int value
    ) {
        if (!tag.contains(field, Tag.TAG_INT)) {
            tag.putInt(field, value);
        }
    }

    private static void putLongIfMissing(
            CompoundTag tag,
            String field,
            long value
    ) {
        if (!tag.contains(field, Tag.TAG_LONG)) {
            tag.putLong(field, value);
        }
    }

    private static void putBooleanIfMissing(
            CompoundTag tag,
            String field,
            boolean value
    ) {
        if (!tag.contains(field, Tag.TAG_BYTE)) {
            tag.putBoolean(field, value);
        }
    }

    public record MigrationResult(
            CompoundTag tag,
            int fromVersion,
            int toVersion,
            boolean changed
    ) {
    }
}
