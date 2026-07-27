package com.stardew.craft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StardewDeterministicRandomTest {
    @Test
    void goldenSequenceProtectsExistingSeedSemantics() {
        StardewDeterministicRandom random =
                StardewDeterministicRandom.create(
                        123_456_789L, 42L, 7L, 99L, 0L);

        int[] actual = new int[8];
        for (int index = 0; index < actual.length; index++) {
            actual[index] = random.nextInt(1_000_000);
        }

        assertArrayEquals(new int[]{
                163_171, 141_295, 809_448, 727_521,
                70_591, 290_397, 793_095, 64_795
        }, actual);
    }

    @Test
    void independentOwnerSeedDoesNotConsumeTheBaseStream() {
        StardewDeterministicRandom base =
                StardewDeterministicRandom.create(9L, 8L, 7L);
        StardewDeterministicRandom sameBase =
                StardewDeterministicRandom.create(9L, 8L, 7L);
        StardewDeterministicRandom addonStream =
                StardewDeterministicRandom.create(
                        9L, 8L, 7L, 0xADD0L, 1L);

        addonStream.nextInt(100);
        addonStream.nextInt(100);

        int expected = sameBase.nextInt(1_000_000);
        assertEquals(expected,
                base.nextInt(1_000_000));
        assertNotEquals(expected,
                addonStream.nextInt(1_000_000));
    }
}
