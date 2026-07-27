package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Stable metadata for a crop whose blocks and persistence may be owned by an addon.
 *
 * <p>The descriptor deliberately does not require {@code StardewCropBlock}, an age property or a
 * particular block geometry.
 */
public record StardewCropType(
        ResourceLocation id,
        String translationKey,
        int visualStageCount,
        List<ResourceLocation> blockIds,
        @Nullable StardewCropData data
) {
    public StardewCropType {
        id = Objects.requireNonNull(id, "id");
        translationKey = Objects.requireNonNull(translationKey, "translationKey");
        if (translationKey.isBlank()) {
            throw new IllegalArgumentException("Crop translation key cannot be blank");
        }
        if (visualStageCount <= 0) {
            throw new IllegalArgumentException("Crop visual stage count must be positive");
        }
        Objects.requireNonNull(blockIds, "blockIds");
        blockIds = List.copyOf(new ArrayList<>(new LinkedHashSet<>(blockIds)));
        if (blockIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Crop type must declare at least one recognized block ID");
        }
    }
}
