package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/**
 * Server-authoritative purchase listing for a registered managed-animal type.
 *
 * @param registrationId unique owner-qualified registration ID used for diagnostics
 * @param animalTypeId managed-animal record ID registered through {@link StardewAnimalTypes}
 * @param family required building family
 * @param requiredTier minimum building tier
 * @param price purchase price
 * @param defaultName literal fallback name for an unnamed purchase
 * @param displayNameKey translation key shown in the purchase list
 * @param descriptionKey translation key for the purchase description
 * @param lockReasonKey translation key shown when the required building is unavailable
 * @param sortOrder ordering among addon entries, then by registration ID
 */
public record StardewAnimalShopEntry(
        ResourceLocation registrationId,
        String animalTypeId,
        String family,
        int requiredTier,
        int price,
        String defaultName,
        String displayNameKey,
        String descriptionKey,
        String lockReasonKey,
        int sortOrder
) {
    public StardewAnimalShopEntry {
        Objects.requireNonNull(registrationId, "registrationId");
        animalTypeId = normalize(animalTypeId, "animalTypeId");
        family = normalize(family, "family");
        defaultName = requireText(defaultName, "defaultName");
        displayNameKey = requireText(displayNameKey, "displayNameKey");
        descriptionKey = requireText(descriptionKey, "descriptionKey");
        lockReasonKey = requireText(lockReasonKey, "lockReasonKey");
        if (requiredTier < 1) {
            throw new IllegalArgumentException("requiredTier must be positive");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }

    private static String normalize(String value, String field) {
        return requireText(value, field).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
