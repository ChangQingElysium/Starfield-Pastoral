package com.stardew.craft.integration.jei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewJeiMenuBoundsTest {
    @Test
    void hiddenTabsReserveTheWholeScreenInsteadOfContainerBounds() {
        StardewJeiPlugin.JeiScreenBounds bounds = StardewJeiPlugin.menuJeiBounds(
                false, 1920, 1080, 528, 180, 864, 720);

        assertEquals(new StardewJeiPlugin.JeiScreenBounds(0, 0, 1920, 1080), bounds);
    }

    @Test
    void supportedTabsKeepTheMenuBoundsUsedByJei() {
        StardewJeiPlugin.JeiScreenBounds bounds = StardewJeiPlugin.menuJeiBounds(
                true, 1920, 1080, 528, 180, 864, 720);

        assertEquals(new StardewJeiPlugin.JeiScreenBounds(528, 180, 864, 720), bounds);
    }
}
