package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Client-safe NPC name, relationship and portrait metadata. */
public record StardewNpcDisplay(
        ResourceLocation npcId,
        String nameTranslationKey,
        ResourceLocation portraitTexture,
        int portraitSheetWidth,
        int portraitSheetHeight,
        ResourceLocation mugshotTexture,
        int mugshotSheetWidth,
        int mugshotSheetHeight,
        String relationshipTranslationKey,
        boolean datable
) {
    public StardewNpcDisplay {
        npcId = Objects.requireNonNull(npcId, "npcId");
        nameTranslationKey = requireText(nameTranslationKey, "nameTranslationKey");
        portraitTexture = Objects.requireNonNull(portraitTexture, "portraitTexture");
        mugshotTexture = Objects.requireNonNull(mugshotTexture, "mugshotTexture");
        relationshipTranslationKey = Objects.requireNonNull(
                relationshipTranslationKey, "relationshipTranslationKey");
        if (portraitSheetWidth <= 0 || portraitSheetHeight <= 0) {
            throw new IllegalArgumentException(
                    "portrait sheet dimensions must be positive");
        }
        if (mugshotSheetWidth <= 0 || mugshotSheetHeight <= 0) {
            throw new IllegalArgumentException(
                    "mugshot sheet dimensions must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
