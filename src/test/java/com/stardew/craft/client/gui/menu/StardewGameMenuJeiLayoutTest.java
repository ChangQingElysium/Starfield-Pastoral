package com.stardew.craft.client.gui.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewGameMenuJeiLayoutTest {
    @Test
    void jeiOnlyAppearsOnInventoryAndCraftingTabs() {
        assertTrue(StardewGameMenuScreen.tabSupportsJei(0));
        assertTrue(StardewGameMenuScreen.tabSupportsJei(4));

        for (int tab : new int[] {1, 2, 3, 5, 6, 7, 8, 9}) {
            assertFalse(StardewGameMenuScreen.tabSupportsJei(tab),
                    () -> "JEI must stay hidden on V-menu tab " + tab);
        }
    }

}
