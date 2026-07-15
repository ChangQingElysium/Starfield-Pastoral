package com.stardew.craft.client.gui.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewRenderMappingTest {
    @Test
    void keepsNormalGuiScaleWhenCanvasFits() {
        StardewRenderMapping mapping = StardewRenderMapping.fitCanvas(
                960, 540, 2.0f, 1280, 720);

        assertEquals(640, mapping.ui(1280));
        assertEquals(360, mapping.ui(720));
        assertEquals(2.0f, mapping.s4());
    }

    @Test
    void uniformlyFitsCanvasInsideSmallWindow() {
        StardewRenderMapping mapping = StardewRenderMapping.fitCanvas(
                512, 288, 2.0f, 1280, 720);

        assertEquals(512, mapping.ui(1280));
        assertEquals(288, mapping.ui(720));
        assertEquals(1.6f, mapping.s4(), 0.0001f);
    }

    @Test
    void fitsByNarrowestAxisWithoutDistortingAspectRatio() {
        StardewRenderMapping mapping = StardewRenderMapping.fitCanvas(
                240, 400, 2.0f, 1280, 720);

        assertEquals(240, mapping.ui(1280));
        assertEquals(135, mapping.ui(720));
        assertTrue(mapping.ui(1280) <= 240);
        assertTrue(mapping.ui(720) <= 400);
    }

    @Test
    void neverUpscalesPastSelectedGuiScale() {
        StardewRenderMapping mapping = StardewRenderMapping.fitCanvas(
                1920, 1080, 2.0f, 1280, 720);

        assertEquals(640, mapping.ui(1280));
        assertEquals(360, mapping.ui(720));
        assertEquals(2.0f, mapping.s4());
    }

    @Test
    void canvasAndInventoryItemsStayInsideTheirCellsAcrossWindowAndGuiScales() {
        int[][] windows = {
                {240, 135}, {320, 180}, {480, 270}, {512, 288},
                {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}
        };

        for (int[] window : windows) {
            for (int guiScale = 1; guiScale <= 8; guiScale++) {
                StardewRenderMapping mapping = StardewRenderMapping.fitCanvas(
                        window[0], window[1], guiScale, 1280, 720);
                int panelWidth = mapping.ui(1280);
                int panelHeight = mapping.ui(720);
                int menuX = (window[0] - panelWidth) / 2;
                int menuY = (window[1] - panelHeight) / 2;

                assertTrue(menuX >= 0);
                assertTrue(menuY >= 0);
                assertTrue(menuX + panelWidth <= window[0]);
                assertTrue(menuY + panelHeight <= window[1]);
                assertEquals(mapping.ui(64), Math.round(16 * mapping.s4()));
                assertTrue(mapping.ui(64) <= mapping.ui(72));
            }
        }
    }
}
