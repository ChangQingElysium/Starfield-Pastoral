package com.stardew.craft.client.gui.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewGameMenuArrowLayoutTest {
    @Test
    void arrowIsCenteredWithinItsActualControlBounds() {
        assertCentered(44, 11, 3.2f);
        assertCentered(48, 12, 3.2f);
        assertCentered(64, 11, 3.2f);
        assertCentered(64, 12, 3.2f);
    }

    private static void assertCentered(int boundSize, int texturePixels, float drawScale) {
        int drawSize = Math.round(texturePixels * drawScale);
        int leadingSpace = StardewGameMenuScreen.centeredArrowOffset(boundSize, texturePixels, drawScale);
        int trailingSpace = boundSize - leadingSpace - drawSize;

        assertTrue(Math.abs(leadingSpace - trailingSpace) <= 1,
                () -> "arrow margins differ: " + leadingSpace + " vs " + trailingSpace);
    }
}
