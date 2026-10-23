package com.stardew.craft.interior.door;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoorConnectionTest {
    private final DoorConnection door = new DoorConnection(new Vec3(10, 65, 3), new Vec3(12, 36, 7), 2, 1, -1);

    @Test void jumpHeightAndTangentialPositionSurviveBothDirections() {
        Vec3 feet = new Vec3(10.2, 64.8, 3.1);
        Vec3 inside = door.movementDestinationBase(feet, true);
        assertEquals(35.8, inside.y, 1e-9);
        assertEquals(feet.x, door.movementDestinationBase(inside, false).x, 1e-9);
        assertEquals(feet.y, door.movementDestinationBase(inside, false).y, 1e-9);
        assertEquals(feet.z, door.movementDestinationBase(inside, false).z, 1e-9);
    }

    @Test void lowerApproachStillLiftsToDestinationFloor() {
        assertEquals(35, door.movementDestinationBase(new Vec3(10, 63.5, 3.1), true).y);
    }

    @Test void apertureAndDirectionRejectSidewaysOrReversedCrossings() {
        assertTrue(door.crosses(new Vec3(10, 64, 2.9), new Vec3(10, 64, 3.1), .6, 1.8, true));
        assertFalse(door.crosses(new Vec3(10, 64, 3.1), new Vec3(10, 64, 2.9), .6, 1.8, true));
        assertFalse(door.crosses(new Vec3(10.9, 64, 2.9), new Vec3(10.9, 64, 3.1), .6, 1.8, true));
        assertFalse(door.crosses(new Vec3(10, 64, 2.9), new Vec3(Double.NaN, 64, 3.1), .6, 1.8, true));
    }

    @Test void invalidAperturesNeverReachClippingOrPrediction() {
        assertThrows(IllegalArgumentException.class, () -> new DoorConnection(Vec3.ZERO, Vec3.ZERO, Double.NaN, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new DoorConnection(Vec3.ZERO, Vec3.ZERO, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new DoorConnection(new Vec3(0, Double.POSITIVE_INFINITY, 0), Vec3.ZERO, 1, 1, -1));
    }
}
