package com.stardew.craft.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewHudLayoutTest {
    @Test
    void keepsSdvHudAtFourPhysicalPixelsPerSourcePixel() {
        for (int guiScale = 2; guiScale <= 4; guiScale++) {
            float renderScale = StardewHudLayout.renderScale(guiScale);
            assertEquals(288.0F,
                    StardewHudLayout.TIME_BG_WIDTH * renderScale * guiScale,
                    0.001F);
        }
    }

    @Test
    void keepsRightMarginAtFortyPhysicalPixels() {
        int physicalScreenWidth = 1920;
        for (int guiScale = 2; guiScale <= 4; guiScale++) {
            int guiScaledWidth = physicalScreenWidth / guiScale;
            float renderScale = StardewHudLayout.renderScale(guiScale);
            int anchorX = StardewHudLayout.anchorX(guiScaledWidth, renderScale);
            float rightEdge = (anchorX + StardewHudLayout.TIME_BG_WIDTH) * renderScale * guiScale;
            assertEquals(40.0F, physicalScreenWidth - rightEdge, 0.001F);
        }
    }
}
