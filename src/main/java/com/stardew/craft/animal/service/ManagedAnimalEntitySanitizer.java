package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;

/**
 * Sanitizes the raw entity list before managed animal entities are constructed.
 */
public final class ManagedAnimalEntitySanitizer {
    static final String MANAGED_ID_TAG = "stardewManagedAnimalId";
    static final String MANAGED_TYPE_TAG = "stardewManagedAnimalType";
    static final int MAX_STARDEW_ANIMALS_PER_ENTITY_CHUNK = 128;

    private ManagedAnimalEntitySanitizer() {
    }

    public static Result sanitize(ListTag entities) {
        Set<Long> managedIds = new HashSet<>();
        ListTag sanitized = new ListTag();
        int keptManagedAnimals = 0;
        int duplicateCount = 0;
        int invalidCount = 0;
        int excessCount = 0;

        for (Tag tag : entities) {
            if (!(tag instanceof CompoundTag entityTag) || !isManagedStardewAnimal(entityTag)) {
                sanitized.add(tag);
                continue;
            }

            long managedId = entityTag.getLong(MANAGED_ID_TAG);
            if (managedId <= 0L) {
                invalidCount++;
                continue;
            }
            if (!managedIds.add(managedId)) {
                duplicateCount++;
                continue;
            }
            if (keptManagedAnimals >= MAX_STARDEW_ANIMALS_PER_ENTITY_CHUNK) {
                excessCount++;
                continue;
            }

            sanitized.add(tag);
            keptManagedAnimals++;
        }

        return new Result(
            entities,
            sanitized,
            keptManagedAnimals,
            duplicateCount,
            invalidCount,
            excessCount
        );
    }

    private static boolean isManagedStardewAnimal(CompoundTag tag) {
        return tag.getString("id").startsWith(StardewCraft.MODID + ":")
            && tag.contains(MANAGED_ID_TAG, Tag.TAG_LONG)
            && tag.contains(MANAGED_TYPE_TAG, Tag.TAG_STRING);
    }

    public record Result(
        ListTag original,
        ListTag sanitized,
        int keptManagedAnimals,
        int duplicateCount,
        int invalidCount,
        int excessCount
    ) {
        public int removedCount() {
            return duplicateCount + invalidCount + excessCount;
        }

        public boolean changed() {
            return removedCount() > 0;
        }
    }
}
