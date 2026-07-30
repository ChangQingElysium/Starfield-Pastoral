package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YetiToothSpineTrackerTest {
    @Test
    void fiveSpinesFillTheInclusiveOneHundredTwentyDegreeFan() {
        float baseYaw = 15.0F;

        assertEquals(-45.0F, YetiToothSpineTracker.angleForIndex(
                baseYaw,
                0
        ));
        assertEquals(15.0F, YetiToothSpineTracker.angleForIndex(
                baseYaw,
                2
        ));
        assertEquals(75.0F, YetiToothSpineTracker.angleForIndex(
                baseYaw,
                4
        ));
    }

    @Test
    void verticalAimFallsBackToThePlayersYaw() {
        Vec3 direction = YetiToothSpineTracker.horizontalLook(
                new Vec3(0.0, 1.0, 0.0),
                90.0F
        );

        assertEquals(-1.0, direction.x, 1.0E-9);
        assertEquals(0.0, direction.y, 1.0E-9);
        assertEquals(0.0, direction.z, 1.0E-9);
    }

    @Test
    void activeSpinesAreBoundToTheirCastDimension() {
        assertTrue(YetiToothSpineTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(YetiToothSpineTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}
