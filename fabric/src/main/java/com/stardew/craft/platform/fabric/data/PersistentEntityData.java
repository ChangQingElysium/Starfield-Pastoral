package com.stardew.craft.platform.fabric.data;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Fabric counterpart of NeoForge's per-entity mutable persistent CompoundTag. */
public final class PersistentEntityData {
    private PersistentEntityData() {
    }

    public static CompoundTag get(Entity entity) {
        return storage(entity).get();
    }

    /**
     * NeoForge ServerPlayer.restoreFrom copies only PlayerPersisted, for both
     * death respawns and keep-everything clones. Transient root keys are excluded.
     */
    public static void copyPlayerPersisted(Entity original, Entity replacement) {
        storage(original).copyPlayerPersistedTo(storage(replacement));
    }

    private static PersistentEntityDataStorage storage(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity instanceof PersistentEntityDataAccess access)) {
            throw new IllegalStateException("Persistent entity data mixin is missing for "
                    + entity.getClass().getName());
        }
        return access.stardewcraft$persistentEntityData();
    }
}
