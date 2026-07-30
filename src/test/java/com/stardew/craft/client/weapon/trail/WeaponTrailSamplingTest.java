package com.stardew.craft.client.weapon.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponTrailSamplingTest {
    @Test
    void threeTickTrailCapacityMatchesTimeQuantizedSampling() {
        int required = (int) Math.ceil(
                3.0 / WeaponTrailClient.sampleIntervalTicks()
        ) + 1;
        assertTrue(WeaponTrailClient.sampleCapacity() >= required);
        assertEquals(1, WeaponTrailClient.calculateResampleCount(0.20));
        assertEquals(2, WeaponTrailClient.calculateResampleCount(0.40));
        assertEquals(4, WeaponTrailClient.calculateResampleCount(1.00));
    }

    @Test
    void textureCoordinateIsStableForSampleAge() {
        assertEquals(
                1.0f,
                WeaponTrailClient.calculateTrailCoordinate(100.0, 3.0f, 100.0),
                0.0001f
        );
        assertEquals(
                0.5f,
                WeaponTrailClient.calculateTrailCoordinate(100.0, 3.0f, 101.5),
                0.0001f
        );
        assertEquals(
                0.0f,
                WeaponTrailClient.calculateTrailCoordinate(100.0, 3.0f, 103.0),
                0.0001f
        );
    }
}
