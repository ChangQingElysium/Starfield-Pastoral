package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable metadata needed before a managed animal entity exists.
 *
 * @param registrationId unique owner-qualified registration ID used for diagnostics
 * @param animalTypeId exact ID stored in managed-animal records
 * @param family building family accepted by this animal, such as {@code coop} or {@code barn}
 * @param daysToMature number of daily updates before the animal becomes an adult
 */
public record StardewAnimalTypeDefinition(
        ResourceLocation registrationId,
        String animalTypeId,
        String family,
        int daysToMature
) {
    public StardewAnimalTypeDefinition {
        Objects.requireNonNull(registrationId, "registrationId");
        animalTypeId = normalize(animalTypeId, "animalTypeId");
        family = normalize(family, "family");
        if (daysToMature < 0) {
            throw new IllegalArgumentException("daysToMature must be non-negative");
        }
    }

    private static String normalize(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
