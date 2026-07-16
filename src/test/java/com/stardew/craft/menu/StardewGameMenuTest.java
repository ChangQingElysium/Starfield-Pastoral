package com.stardew.craft.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewGameMenuTest {
    @Test
    void mainInventoryIndicesMapToTheFirstTwentySevenMenuSlots() {
        assertEquals(0, StardewGameMenu.menuSlotForInventoryIndex(9));
        assertEquals(26, StardewGameMenu.menuSlotForInventoryIndex(35));
    }

    @Test
    void hotbarIndicesMapAfterTheMainInventory() {
        assertEquals(27, StardewGameMenu.menuSlotForInventoryIndex(0));
        assertEquals(35, StardewGameMenu.menuSlotForInventoryIndex(8));
    }

    @Test
    void invalidInventoryIndicesAreRejected() {
        assertEquals(-1, StardewGameMenu.menuSlotForInventoryIndex(-1));
        assertEquals(-1, StardewGameMenu.menuSlotForInventoryIndex(36));
    }
}
