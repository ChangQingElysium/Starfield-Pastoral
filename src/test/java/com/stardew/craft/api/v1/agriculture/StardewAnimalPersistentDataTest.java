package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewAnimalPersistentDataTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void registeredAndUnknownEntriesRoundTripWithoutSharingMutableNbt() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation registeredId = id("goose_genetics_" + suffix);
        ResourceLocation absentAddonId = ResourceLocation.fromNamespaceAndPath(
                "absent_animal_addon", "legacy_state_" + suffix);
        StardewAnimalPersistentData.Key key =
                StardewAnimalPersistentData.register(registeredId, 3);

        CompoundTag registeredPayload = new CompoundTag();
        registeredPayload.putString("color", "silver");
        CompoundTag absentPayload = new CompoundTag();
        absentPayload.putInt("nest", 4);

        CompoundTag addonData = new CompoundTag();
        addonData.put(registeredId.toString(), entry(1, registeredPayload));
        addonData.put(absentAddonId.toString(), entry(7, absentPayload));
        CompoundTag malformedButPreserved = new CompoundTag();
        malformedButPreserved.putString("raw", "keep");
        addonData.put("not a resource location", malformedButPreserved);

        CompoundTag animalTag = animal().save();
        animalTag.put("addonData", addonData);
        FarmAnimalRecord loaded = FarmAnimalRecord.load(animalTag);

        StardewAnimalPersistentData.Value value =
                StardewAnimalPersistentData.read(loaded, key).orElseThrow();
        assertEquals(1, value.storedVersion());
        assertEquals("silver", value.payload().getString("color"));
        CompoundTag callerCopy = value.payload();
        callerCopy.putString("color", "changed");
        assertEquals(
                "silver",
                StardewAnimalPersistentData.read(loaded, key)
                        .orElseThrow().payload().getString("color")
        );

        assertTrue(loaded.persistentData().storedIds().contains(registeredId));
        assertTrue(loaded.persistentData().storedIds().contains(absentAddonId));
        var diagnostics = loaded.persistentData().diagnostics();
        assertEquals(Set.of(absentAddonId), diagnostics.orphanedIds());
        assertEquals(Set.of(registeredId),
                diagnostics.legacyVersionIds());
        assertEquals(List.of("not a resource location"),
                diagnostics.invalidEntryNames());
        CompoundTag savedEntries = loaded.save().getCompound("addonData");
        assertEquals(addonData, savedEntries);
        assertEquals("keep",
                savedEntries.getCompound("not a resource location").getString("raw"));
    }

    @Test
    void oldAnimalRecordsRemainFreeOfSyntheticAddonData() {
        FarmAnimalRecord loaded = FarmAnimalRecord.load(animal().save());

        assertTrue(loaded.persistentData().storedIds().isEmpty());
        assertFalse(loaded.save().contains("addonData"));
    }

    @Test
    void persistentDataRegistrationIdsCannotBeReused() {
        ResourceLocation registrationId = id("duplicate_" + IDS.incrementAndGet());
        StardewAnimalPersistentData.register(registrationId, 1);

        assertThrows(IllegalStateException.class, () ->
                StardewAnimalPersistentData.register(registrationId, 2));
    }

    private static CompoundTag entry(int version, CompoundTag payload) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("version", version);
        entry.put("payload", payload.copy());
        return entry;
    }

    private static FarmAnimalRecord animal() {
        return new FarmAnimalRecord(
                IDS.incrementAndGet(),
                "animal_persistent_test:goose",
                "",
                "test_coop",
                AnimalAcquisitionSource.PURCHASE,
                1,
                0,
                1,
                2,
                5
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("animal_persistent_test", path);
    }
}
