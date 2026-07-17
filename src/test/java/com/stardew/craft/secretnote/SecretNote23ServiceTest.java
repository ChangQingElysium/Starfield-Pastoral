package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNote23ServiceTest {
    @Test
    void bearKnowledgeTriplesOnlyVanillaKnowledgeBerries() {
        assertEquals(3.0, SecretNote23Service.sellPriceMultiplier(true, item("salmonberry")));
        assertEquals(3.0, SecretNote23Service.sellPriceMultiplier(true, item("blackberry")));
        assertEquals(1.0, SecretNote23Service.sellPriceMultiplier(false, item("salmonberry")));
        assertEquals(1.0, SecretNote23Service.sellPriceMultiplier(true, item("blueberry")));
    }

    @Test
    void legacyMailFlagAndSpecialItemBothUnlockBearKnowledge() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        assertFalse(SecretNote23Service.hasBearKnowledge(data));

        data.addSpecialItem(SecretNote23Service.SPECIAL_ITEM_ID);
        assertTrue(SecretNote23Service.hasBearKnowledge(data));

        PlayerStardewData legacyData = new PlayerStardewData(UUID.randomUUID());
        legacyData.addMailFlag(SecretNoteStoryFlags.BEAR_KNOWLEDGE);
        assertTrue(SecretNote23Service.hasBearKnowledge(legacyData));
    }

    private static ResourceLocation item(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", path);
    }
}
