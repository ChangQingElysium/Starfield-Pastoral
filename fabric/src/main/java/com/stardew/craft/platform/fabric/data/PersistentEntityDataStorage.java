package com.stardew.craft.platform.fabric.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * One instance belongs to one entity; there is no static entity-data map.
 *
 * NeoForge 21.1.217's Entity patch uses NeoForgeData, while Forge 1.20.1 used
 * ForgeData. A legacy-only compound is read and kept current under both keys.
 * When both compounds exist, NeoForgeData is authoritative and the separate
 * ForgeData payload is preserved without merging conflicting entries.
 */
public final class PersistentEntityDataStorage {
    public static final String SAVE_KEY = "NeoForgeData";
    public static final String LEGACY_SAVE_KEY = "ForgeData";
    public static final String PLAYER_PERSISTED_KEY = "PlayerPersisted";

    private CompoundTag data;
    private Tag preservedLegacyData;
    private Tag opaquePrimaryData;
    private boolean writeLegacyMirror;

    public CompoundTag get() {
        if (opaquePrimaryData != null && !writeLegacyMirror) {
            throw new IllegalStateException("Entity NeoForgeData is not a compound; refusing to overwrite it");
        }
        if (data == null) {
            data = new CompoundTag();
        }
        return data;
    }

    public void load(CompoundTag entityTag) {
        Tag primary = entityTag.get(SAVE_KEY);
        Tag legacy = entityTag.get(LEGACY_SAVE_KEY);
        // NeoForge leaves an existing persistent tag intact if the load input
        // has no persistent data. Fresh entities still remain uninitialized.
        if (primary == null && legacy == null) {
            return;
        }
        preservedLegacyData = legacy == null ? null : legacy.copy();
        opaquePrimaryData = primary != null && !(primary instanceof CompoundTag) ? primary.copy() : null;
        writeLegacyMirror = false;
        if (primary instanceof CompoundTag compound) {
            data = compound.copy();
            // A previously written compatibility mirror should keep following
            // edits after reload. Distinct legacy payloads remain independent.
            writeLegacyMirror = compound.equals(legacy);
        } else if (legacy instanceof CompoundTag compound) {
            data = compound.copy();
            writeLegacyMirror = true;
        }
    }

    public void save(CompoundTag entityTag) {
        if (opaquePrimaryData != null) {
            entityTag.put(SAVE_KEY, opaquePrimaryData.copy());
        } else if (data != null) {
            entityTag.put(SAVE_KEY, data.copy());
        }
        if (writeLegacyMirror) {
            entityTag.put(LEGACY_SAVE_KEY, data.copy());
        } else if (preservedLegacyData != null) {
            entityTag.put(LEGACY_SAVE_KEY, preservedLegacyData.copy());
        }
    }

    public void copyPlayerPersistedTo(PersistentEntityDataStorage replacement) {
        Tag persisted = get().get(PLAYER_PERSISTED_KEY);
        if (persisted != null) {
            replacement.get().put(PLAYER_PERSISTED_KEY, persisted.copy());
        }
    }
}
