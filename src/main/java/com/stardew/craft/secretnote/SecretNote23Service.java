package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Source-parity state and sell-price rule unlocked by vanilla event 2120303. */
public final class SecretNote23Service {
    public static final String NOTE_ID = "stardewcraft:23";
    public static final double BERRY_PRICE_MULTIPLIER = 3.0;

    private static final Set<ResourceLocation> KNOWLEDGE_BERRIES = Set.of(
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "salmonberry"),
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "blackberry")
    );

    private SecretNote23Service() {
    }

    public static boolean hasBearKnowledge(PlayerStardewData data) {
        return data != null && data.hasMailFlag(SecretNoteStoryFlags.BEAR_KNOWLEDGE);
    }

    public static double sellPriceMultiplier(boolean hasBearKnowledge, ResourceLocation itemId) {
        return hasBearKnowledge && KNOWLEDGE_BERRIES.contains(itemId)
                ? BERRY_PRICE_MULTIPLIER
                : 1.0;
    }
}
