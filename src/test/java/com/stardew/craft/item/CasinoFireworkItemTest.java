package com.stardew.craft.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CasinoFireworkItemTest {
    @Test
    void usesOriginalObjectPriceAndMiscCategory() {
        assertEquals(50, CasinoFireworkItem.ORIGINAL_SELL_PRICE);
        assertEquals("stardewcraft.type.misc", CasinoFireworkItem.CATEGORY_KEY);
    }
}
