package com.stardew.craft.client.weapon.presentation;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForestBlessingCadenceTest {
    @Test
    void eightyTickBlessingHasEightVisualHealingBeats() {
        long beats = IntStream.rangeClosed(1, 80)
                .filter(age -> ForestBlessingPresentation.isHealingBeat(age, 80))
                .count();

        assertEquals(8L, beats);
    }
}
