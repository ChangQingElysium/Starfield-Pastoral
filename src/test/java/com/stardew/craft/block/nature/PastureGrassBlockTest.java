package com.stardew.craft.block.nature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PastureGrassBlockTest {
    @Test
    void expandingSearchVisitsEachHorizontalCoordinateOnOneRingOnly() {
        assertTrue(PastureGrassBlock.isOnHorizontalRing(
                0, 0, 1, 0, 1));
        assertTrue(PastureGrassBlock.isOnHorizontalRing(
                0, 0, -2, 1, 2));
        assertFalse(PastureGrassBlock.isOnHorizontalRing(
                0, 0, 1, 0, 2));
        assertFalse(PastureGrassBlock.isOnHorizontalRing(
                0, 0, 0, 0, 1));
        assertFalse(PastureGrassBlock.isOnHorizontalRing(
                0, 0, 0, 0, 0));
    }
}
