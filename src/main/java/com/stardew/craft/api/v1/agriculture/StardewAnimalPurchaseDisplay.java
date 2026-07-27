package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** Client-safe purchase-screen texture metadata for an addon animal type. */
public record StardewAnimalPurchaseDisplay(
        ResourceLocation registrationId,
        String animalTypeId,
        ResourceLocation texture,
        int textureWidth,
        int textureHeight
) {
    public StardewAnimalPurchaseDisplay {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(animalTypeId, "animalTypeId");
        Objects.requireNonNull(texture, "texture");
        animalTypeId = animalTypeId.trim().toLowerCase(Locale.ROOT);
        if (animalTypeId.isEmpty()) {
            throw new IllegalArgumentException("animalTypeId must not be blank");
        }
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive");
        }
    }
}
