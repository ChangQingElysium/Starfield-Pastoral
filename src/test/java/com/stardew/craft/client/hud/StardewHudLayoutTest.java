package com.stardew.craft.client.hud;

import com.stardew.craft.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewHudLayoutTest {
    @Test
    void topRightAnchorPreservesGuiMarginAcrossWindowSizes() {
        StardewHudLayout.Placement small = StardewHudLayout.calculate(
                427, 240, 100,
                Config.HudHorizontalAnchor.RIGHT, Config.HudVerticalAnchor.TOP,
                10, 10);
        StardewHudLayout.Placement fullscreen = StardewHudLayout.calculate(
                480, 270, 100,
                Config.HudHorizontalAnchor.RIGHT, Config.HudVerticalAnchor.TOP,
                10, 10);

        assertEquals(72, small.width());
        assertEquals(84, small.height());
        assertEquals(10, 427 - small.x() - small.width());
        assertEquals(10, 480 - fullscreen.x() - fullscreen.width());
        assertEquals(10, small.y());
        assertEquals(10, fullscreen.y());
    }

    @Test
    void scaleChangesWholeHudGroupBeforeAnchoring() {
        StardewHudLayout.Placement placement = StardewHudLayout.calculate(
                480, 270, 150,
                Config.HudHorizontalAnchor.RIGHT, Config.HudVerticalAnchor.BOTTOM,
                12, 8);

        assertEquals(108, placement.width());
        assertEquals(126, placement.height());
        assertEquals(12, 480 - placement.x() - placement.width());
        assertEquals(8, 270 - placement.y() - placement.height());
    }

    @Test
    void centerAnchorUsesSignedOffsetAndClampsToScreen() {
        StardewHudLayout.Placement centered = StardewHudLayout.calculate(
                400, 240, 100,
                Config.HudHorizontalAnchor.CENTER, Config.HudVerticalAnchor.CENTER,
                7, -5);
        StardewHudLayout.Placement clamped = StardewHudLayout.calculate(
                80, 60, 150,
                Config.HudHorizontalAnchor.RIGHT, Config.HudVerticalAnchor.BOTTOM,
                -9999, -9999);

        assertEquals(400 / 2 - centered.width() / 2 + 7, centered.x());
        assertEquals(240 / 2 - centered.height() / 2 - 5, centered.y());
        assertEquals(0, clamped.x());
        assertEquals(0, clamped.y());
    }

    @Test
    void eachElementUsesItsOwnPreviewBounds() {
        StardewHudLayout.Placement playerBars = StardewHudLayout.calculate(
                480, 270,
                Config.HudElement.PLAYER_BARS.baseWidth(), Config.HudElement.PLAYER_BARS.baseHeight(),
                100, Config.HudHorizontalAnchor.CENTER, Config.HudVerticalAnchor.BOTTOM,
                0, 31);
        StardewHudLayout.Placement messages = StardewHudLayout.calculate(
                480, 270,
                Config.HudElement.TEXT_MESSAGE.baseWidth(), Config.HudElement.TEXT_MESSAGE.baseHeight(),
                75, Config.HudHorizontalAnchor.LEFT, Config.HudVerticalAnchor.BOTTOM,
                10, 104);

        assertEquals(278, playerBars.width());
        assertEquals(18, playerBars.height());
        assertEquals(480 / 2 - playerBars.width() / 2, playerBars.x());
        assertEquals(239 - playerBars.height(), playerBars.y());
        assertEquals(135, messages.width());
        assertEquals(24, messages.height());
        assertEquals(10, messages.x());
        assertEquals(142, messages.y());
    }

    @Test
    void elementScaleIsClampedToEditorRange() {
        StardewHudLayout.Placement tooSmall = StardewHudLayout.calculate(
                1000, 1000, 100, 40, 1,
                Config.HudHorizontalAnchor.LEFT, Config.HudVerticalAnchor.TOP, 0, 0);
        StardewHudLayout.Placement tooLarge = StardewHudLayout.calculate(
                1000, 1000, 100, 40, 999,
                Config.HudHorizontalAnchor.LEFT, Config.HudVerticalAnchor.TOP, 0, 0);

        assertEquals(25, tooSmall.width());
        assertEquals(10, tooSmall.height());
        assertEquals(200, tooLarge.width());
        assertEquals(80, tooLarge.height());
    }
}
