package com.stardew.craft.secretnote;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretNote23ServiceTest {
    @Test
    void bearKnowledgeTriplesOnlyVanillaKnowledgeBerries() {
        assertEquals(3.0, SecretNote23Service.sellPriceMultiplier(true, item("salmonberry")));
        assertEquals(3.0, SecretNote23Service.sellPriceMultiplier(true, item("blackberry")));
        assertEquals(1.0, SecretNote23Service.sellPriceMultiplier(false, item("salmonberry")));
        assertEquals(1.0, SecretNote23Service.sellPriceMultiplier(true, item("blueberry")));
    }

    private static ResourceLocation item(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", path);
    }
}
