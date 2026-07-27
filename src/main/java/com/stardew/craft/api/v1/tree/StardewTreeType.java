package com.stardew.craft.api.v1.tree;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Stable metadata for a tree type whose runtime shape is owned by the core mod or an addon.
 *
 * <p>The descriptor deliberately does not require a particular block class or tree geometry.
 */
public record StardewTreeType(
        ResourceLocation id,
        Kind kind,
        String translationKey,
        int maturityDays,
        int visualStageCount,
        boolean tapperEligible
) {
    public StardewTreeType {
        id = Objects.requireNonNull(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        translationKey = Objects.requireNonNull(translationKey, "translationKey");
        if (translationKey.isBlank()) {
            throw new IllegalArgumentException("Tree translation key cannot be blank");
        }
        if (maturityDays < 0) {
            throw new IllegalArgumentException("Tree maturity days cannot be negative");
        }
        if (visualStageCount <= 0) {
            throw new IllegalArgumentException("Tree visual stage count must be positive");
        }
    }

    public enum Kind {
        FRUIT,
        WILD,
        OTHER
    }
}
