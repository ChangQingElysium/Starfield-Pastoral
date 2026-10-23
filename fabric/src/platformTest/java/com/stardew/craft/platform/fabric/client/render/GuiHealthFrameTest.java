package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GuiHealthFrameTest {
    @Test
    void damageStartsTwentyTickBlinkWithoutChangingTheCurrentFramesPreviouslyComputedFlash() {
        var damaged = GuiHealthFrame.advance(12, 20, 20, 100, 0, 10, 5, 500);
        assertEquals(12, damaged.health());
        assertEquals(20, damaged.displayHealth());
        assertEquals(500, damaged.lastHealthTime());
        assertEquals(30, damaged.healthBlinkTime());
        assertFalse(damaged.blinking());
        var next = GuiHealthFrame.advance(12, 12, 20, 500, 30, 13, 2, 550);
        assertTrue(next.blinking());
        assertEquals(30, next.healthBlinkTime());
    }

    @Test
    void healingStartsTenTicksAndTheDisplaySettlesOnlyAfterTheNativeOneSecondBoundary() {
        var healed = GuiHealthFrame.advance(18, 12, 12, 100, 0, 50, 1, 500);
        assertEquals(60, healed.healthBlinkTime());
        assertEquals(12, healed.displayHealth());
        assertEquals(12, GuiHealthFrame.advance(18, 18, 12, 500, 60, 51, 0, 1500).displayHealth());
        var settled = GuiHealthFrame.advance(18, 18, 12, 500, 60, 51, 0, 1501);
        assertEquals(18, settled.displayHealth());
        assertEquals(1501, settled.lastHealthTime());
    }

    @Test
    void changesOutsideInvulnerabilityDoNotCreateAnotherBlinkWindow() {
        var frame = GuiHealthFrame.advance(7, 12, 12, 500, 60, 51, 0, 800);
        assertEquals(60, frame.healthBlinkTime());
        assertEquals(500, frame.lastHealthTime());
        assertEquals(12, frame.displayHealth());
    }
}
