package com.stardew.craft.api.v1.extension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable, non-authoritative preview of one namespaced-state migration.
 *
 * <p>Applying a preview rechecks the source version and payload. A preview
 * therefore cannot overwrite state that changed after it was created.
 */
public record StardewStateMigrationPreview(
        ResourceLocation scope,
        ResourceLocation id,
        int storedVersion,
        int targetVersion,
        CompoundTag sourcePayload,
        CompoundTag migratedPayload
) {
    public StardewStateMigrationPreview {
        scope = Objects.requireNonNull(scope, "scope");
        id = Objects.requireNonNull(id, "id");
        if (storedVersion < 0) {
            throw new IllegalArgumentException(
                    "storedVersion must be non-negative");
        }
        if (targetVersion <= storedVersion) {
            throw new IllegalArgumentException(
                    "targetVersion must be newer than storedVersion");
        }
        sourcePayload = Objects.requireNonNull(
                sourcePayload, "sourcePayload").copy();
        migratedPayload = Objects.requireNonNull(
                migratedPayload, "migratedPayload").copy();
    }

    @Override
    public CompoundTag sourcePayload() {
        return sourcePayload.copy();
    }

    @Override
    public CompoundTag migratedPayload() {
        return migratedPayload.copy();
    }
}
