package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** Server-authoritative sell and reproduction-menu rules for an addon animal type. */
public record StardewAnimalQueryDefinition(
        ResourceLocation registrationId,
        String animalTypeId,
        int sellBasePrice,
        int maximumSellPrice,
        boolean reproductionToggleAvailable
) {
    public StardewAnimalQueryDefinition {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(animalTypeId, "animalTypeId");
        animalTypeId = animalTypeId.trim().toLowerCase(Locale.ROOT);
        if (animalTypeId.isEmpty()) {
            throw new IllegalArgumentException("animalTypeId must not be blank");
        }
        if (sellBasePrice <= 0) {
            throw new IllegalArgumentException("sellBasePrice must be positive");
        }
        if (maximumSellPrice <= 0) {
            throw new IllegalArgumentException("maximumSellPrice must be positive");
        }
    }

    public int sellPrice(int friendship) {
        double ratio = Math.max(0, Math.min(1000, friendship)) / 1000.0D;
        return Math.min(maximumSellPrice, (int) Math.floor(sellBasePrice * (ratio + 0.3D)));
    }
}
