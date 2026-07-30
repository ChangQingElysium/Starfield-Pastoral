package com.stardew.craft.client.gui.casino;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoViewportTest {
    @Test
    void originalCasinoCanvasFitsAcrossWindowSizesAndGuiScales() {
        List<Scenario> scenarios = List.of(
                new Scenario(1280, 720, 1.0F),
                new Scenario(640, 360, 2.0F),
                new Scenario(640, 360, 3.0F),
                new Scenario(960, 540, 2.0F),
                new Scenario(480, 270, 4.0F),
                new Scenario(853, 480, 1.0F),
                new Scenario(427, 240, 2.0F),
                new Scenario(1280, 480, 3.0F)
        );

        for (Scenario scenario : scenarios) {
            CasinoViewport viewport = new CasinoViewport(
                    scenario.guiWidth(), scenario.guiHeight(), scenario.guiScale());
            assertTrue(viewport.sourceWidth() >= CasinoViewport.MIN_SOURCE_WIDTH, scenario.toString());
            assertTrue(viewport.sourceHeight() >= CasinoViewport.MIN_SOURCE_HEIGHT, scenario.toString());

            int centerX = viewport.sourceWidth() / 2;
            int centerY = viewport.sourceHeight() / 2;
            assertTrue(viewport.containsSourceRect(
                    centerX - 440, centerY - 352, 1024, 704), scenario.toString());
            assertTrue(viewport.containsSourceRect(
                    centerX - 132, centerY - 192, 312, 192), scenario.toString());
        }
    }

    @Test
    void sourceRectsConvertToStableClickableGuiRects() {
        CasinoViewport viewport = new CasinoViewport(640, 360, 2.0F);
        CasinoViewport.Rect rect = viewport.rect(128, 64, 192, 52);

        assertEquals(viewport.ui(128), rect.x());
        assertEquals(viewport.ui(64), rect.y());
        assertEquals(viewport.ui(192), rect.width());
        assertEquals(viewport.ui(52), rect.height());
        assertTrue(rect.contains(rect.x(), rect.y()));
        assertTrue(rect.contains(rect.x() + rect.width() - 0.01D,
                rect.y() + rect.height() - 0.01D));
        assertFalse(rect.contains(rect.x() + rect.width(), rect.y()));
    }

    private record Scenario(int guiWidth, int guiHeight, float guiScale) {
    }
}
