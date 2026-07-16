package com.stardew.craft.animal.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedAnimalEntitySanitizerTest {
    @Test
    void keepsUnrelatedEntitiesAndOneManagedEntityPerId() {
        ListTag entities = new ListTag();
        CompoundTag item = entity("minecraft:item", 0L, false);
        CompoundTag first = entity("stardewcraft:white_chicken", 7L, true);
        entities.add(item);
        entities.add(first);
        entities.add(entity("stardewcraft:white_chicken", 7L, true));

        ManagedAnimalEntitySanitizer.Result result = ManagedAnimalEntitySanitizer.sanitize(entities);

        assertTrue(result.changed());
        assertEquals(1, result.duplicateCount());
        assertEquals(2, result.sanitized().size());
        assertSame(item, result.sanitized().get(0));
        assertSame(first, result.sanitized().get(1));
    }

    @Test
    void removesInvalidManagedIds() {
        ListTag entities = new ListTag();
        entities.add(entity("stardewcraft:cow", 0L, true));
        entities.add(entity("stardewcraft:goat", -4L, true));

        ManagedAnimalEntitySanitizer.Result result = ManagedAnimalEntitySanitizer.sanitize(entities);

        assertEquals(2, result.invalidCount());
        assertTrue(result.sanitized().isEmpty());
    }

    @Test
    void capsOnlyManagedAnimalsWithoutDroppingOtherEntities() {
        ListTag entities = new ListTag();
        for (long id = 1; id <= ManagedAnimalEntitySanitizer.MAX_STARDEW_ANIMALS_PER_ENTITY_CHUNK + 5L; id++) {
            entities.add(entity("stardewcraft:duck", id, true));
        }
        CompoundTag playerDroppedItem = entity("minecraft:item", 0L, false);
        entities.add(playerDroppedItem);

        ManagedAnimalEntitySanitizer.Result result = ManagedAnimalEntitySanitizer.sanitize(entities);

        assertEquals(5, result.excessCount());
        assertEquals(ManagedAnimalEntitySanitizer.MAX_STARDEW_ANIMALS_PER_ENTITY_CHUNK + 1, result.sanitized().size());
        assertSame(playerDroppedItem, result.sanitized().get(result.sanitized().size() - 1));
    }

    @Test
    void returnsAnUnchangedResultForCleanLists() {
        ListTag entities = new ListTag();
        entities.add(entity("minecraft:pig", 9L, false));
        entities.add(entity("stardewcraft:pig", 9L, true));

        ManagedAnimalEntitySanitizer.Result result = ManagedAnimalEntitySanitizer.sanitize(entities);

        assertFalse(result.changed());
        assertEquals(0, result.removedCount());
    }

    private static CompoundTag entity(String id, long managedId, boolean managed) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        if (managed) {
            tag.putLong(ManagedAnimalEntitySanitizer.MANAGED_ID_TAG, managedId);
            tag.putString(ManagedAnimalEntitySanitizer.MANAGED_TYPE_TAG, "test");
        }
        return tag;
    }
}
