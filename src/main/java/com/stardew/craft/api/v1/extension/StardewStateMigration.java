package com.stardew.craft.api.v1.extension;

import net.minecraft.nbt.CompoundTag;

/**
 * Builds a candidate payload for one explicitly owned namespaced-state key.
 *
 * <p>The supplied payload is a defensive copy. Returning a payload does not
 * mutate persistent state; callers must explicitly apply the resulting
 * {@link StardewStateMigrationPreview}.
 */
@FunctionalInterface
public interface StardewStateMigration {
    CompoundTag migrate(
            int storedVersion,
            int targetVersion,
            CompoundTag payload
    ) throws Exception;
}
