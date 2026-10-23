package com.stardew.craft.platform.fabric.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class PersistentEntityDataStorageTest {
    @Test
    void entityStateIsIndependentAndReturnsTheSameMutableCompound() {
        PersistentEntityDataStorage first = new PersistentEntityDataStorage();
        PersistentEntityDataStorage second = new PersistentEntityDataStorage();
        first.get().putInt("counter", 7);

        assertSame(first.get(), first.get());
        assertEquals(7, first.get().getInt("counter"));
        assertFalse(second.get().contains("counter"));
    }

    @Test
    void uninitializedStateDoesNotCreateASaveKeyButAnAccessedEmptyTagDoes() {
        PersistentEntityDataStorage storage = new PersistentEntityDataStorage();
        CompoundTag before = new CompoundTag();
        storage.save(before);
        assertFalse(before.contains(PersistentEntityDataStorage.SAVE_KEY));

        storage.get();
        CompoundTag after = new CompoundTag();
        storage.save(after);
        assertTrue(after.getCompound(PersistentEntityDataStorage.SAVE_KEY).isEmpty());
        assertTrue(after.contains(PersistentEntityDataStorage.SAVE_KEY));
    }

    @Test
    void neoForgeDataRoundTripsUnknownNestedTagsWithoutAliasingTheSave() throws IOException {
        CompoundTag data = new CompoundTag();
        data.putInt("StardewMonsterHeldLoot", 349);
        data.putUUID("owner", java.util.UUID.fromString("2d1087b8-7657-4086-bfa6-857457e69055"));
        CompoundTag nested = new CompoundTag();
        nested.putByteArray("opaque", new byte[] {1, 4, -2});
        ListTag values = new ListTag();
        values.add(StringTag.valueOf("keep unknown entries"));
        nested.put("values", values);
        data.put("other_mod", nested);
        CompoundTag input = entityTag(PersistentEntityDataStorage.SAVE_KEY, data);
        PersistentEntityDataStorage storage = new PersistentEntityDataStorage();
        storage.load(input);
        input.getCompound(PersistentEntityDataStorage.SAVE_KEY).putInt("outside_mutation", 1);
        assertEquals(data, storage.get());

        CompoundTag output = new CompoundTag();
        output.putString("CustomName", "unchanged");
        storage.save(output);
        assertEquals(data, output.getCompound(PersistentEntityDataStorage.SAVE_KEY));
        assertEquals("unchanged", output.getString("CustomName"));
        output.getCompound(PersistentEntityDataStorage.SAVE_KEY).getCompound("other_mod").putInt("outside", 3);
        assertFalse(storage.get().getCompound("other_mod").contains("outside"));

        CompoundTag cleanSave = new CompoundTag();
        storage.save(cleanSave);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(cleanSave, new DataOutputStream(bytes));
        CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        PersistentEntityDataStorage restored = new PersistentEntityDataStorage();
        restored.load(decoded);
        assertEquals(storage.get(), restored.get());
    }

    @Test
    void forgeOnlyInputRemainsReadableAndKeepsBothKeysCurrent() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("counter", 2);
        PersistentEntityDataStorage storage = new PersistentEntityDataStorage();
        storage.load(entityTag(PersistentEntityDataStorage.LEGACY_SAVE_KEY, legacy));
        storage.get().putInt("counter", 3);

        CompoundTag saved = new CompoundTag();
        storage.save(saved);
        assertEquals(3, saved.getCompound(PersistentEntityDataStorage.SAVE_KEY).getInt("counter"));
        assertEquals(3, saved.getCompound(PersistentEntityDataStorage.LEGACY_SAVE_KEY).getInt("counter"));
        assertNotSame(saved.get(PersistentEntityDataStorage.SAVE_KEY), saved.get(PersistentEntityDataStorage.LEGACY_SAVE_KEY));
        PersistentEntityDataStorage reloaded = new PersistentEntityDataStorage();
        reloaded.load(saved);
        reloaded.get().putInt("counter", 4);
        CompoundTag secondSave = new CompoundTag();
        reloaded.save(secondSave);
        assertEquals(4, secondSave.getCompound(PersistentEntityDataStorage.SAVE_KEY).getInt("counter"));
        assertEquals(4, secondSave.getCompound(PersistentEntityDataStorage.LEGACY_SAVE_KEY).getInt("counter"));
    }

    @Test
    void dualKeysUseNeoForgeAuthorityAndPreserveConflictingLegacyData() {
        CompoundTag modern = new CompoundTag();
        modern.putInt("counter", 8);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("counter", 2);
        legacy.putString("legacy_only", "preserve");
        CompoundTag input = entityTag(PersistentEntityDataStorage.SAVE_KEY, modern);
        input.put(PersistentEntityDataStorage.LEGACY_SAVE_KEY, legacy.copy());
        PersistentEntityDataStorage storage = new PersistentEntityDataStorage();
        storage.load(input);
        assertEquals(8, storage.get().getInt("counter"));
        assertFalse(storage.get().contains("legacy_only"));
        storage.get().putInt("counter", 9);

        CompoundTag saved = new CompoundTag();
        storage.save(saved);
        assertEquals(9, saved.getCompound(PersistentEntityDataStorage.SAVE_KEY).getInt("counter"));
        assertEquals(legacy, saved.getCompound(PersistentEntityDataStorage.LEGACY_SAVE_KEY));
    }

    @Test
    void anAbsentKeyDoesNotClearExistingNeoForgeDataDuringReload() {
        PersistentEntityDataStorage storage = new PersistentEntityDataStorage();
        storage.get().putInt("existing", 5);
        storage.load(new CompoundTag());

        assertEquals(5, storage.get().getInt("existing"));
    }

    @Test
    void cloneCopiesOnlyPlayerPersistedAndDoesNotShareMutableTags() {
        PersistentEntityDataStorage original = new PersistentEntityDataStorage();
        original.get().putLong("temporary_combat_end_tick", 100);
        CompoundTag persisted = new CompoundTag();
        persisted.putInt("quest_marker", 6);
        original.get().put(PersistentEntityDataStorage.PLAYER_PERSISTED_KEY, persisted);
        PersistentEntityDataStorage replacement = new PersistentEntityDataStorage();
        replacement.get().putString("replacement_owned", "keep");
        original.copyPlayerPersistedTo(replacement);

        assertEquals(6, replacement.get().getCompound(PersistentEntityDataStorage.PLAYER_PERSISTED_KEY).getInt("quest_marker"));
        assertFalse(replacement.get().contains("temporary_combat_end_tick"));
        assertEquals("keep", replacement.get().getString("replacement_owned"));
        replacement.get().getCompound(PersistentEntityDataStorage.PLAYER_PERSISTED_KEY).putInt("quest_marker", 7);
        assertEquals(6, persisted.getInt("quest_marker"));
    }

    @Test
    void cloningWithoutPlayerPersistedDoesNotInitializeReplacementData() {
        PersistentEntityDataStorage replacement = new PersistentEntityDataStorage();
        new PersistentEntityDataStorage().copyPlayerPersistedTo(replacement);
        CompoundTag saved = new CompoundTag();
        replacement.save(saved);

        assertFalse(saved.contains(PersistentEntityDataStorage.SAVE_KEY));
    }

    @Test
    void malformedPrimaryDataIsPreservedAndCannotBeSilentlyOverwritten() {
        CompoundTag input = new CompoundTag();
        input.putString(PersistentEntityDataStorage.SAVE_KEY, "opaque payload");
        PersistentEntityDataStorage storage = new PersistentEntityDataStorage();
        storage.load(input);
        assertThrows(IllegalStateException.class, storage::get);
        CompoundTag saved = new CompoundTag();
        storage.save(saved);

        assertEquals("opaque payload", saved.getString(PersistentEntityDataStorage.SAVE_KEY));
    }

    private static CompoundTag entityTag(String key, CompoundTag data) {
        CompoundTag result = new CompoundTag();
        result.put(key, data.copy());
        return result;
    }
}
