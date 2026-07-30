package com.stardew.craft.client.weapon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponSkillPlaybackClockTest {
    @Test
    void playbackStartsAtPacketReceiptInsteadOfServerAbsoluteTick() {
        long packetReceiptTick = 2_402L;

        assertEquals(
                0.0f,
                WeaponSkillAnimationClient.calculatePlaybackProgress(
                        packetReceiptTick,
                        packetReceiptTick,
                        0.0f,
                        8
                ),
                0.0001f
        );
        assertEquals(
                3.0f / 8.0f,
                WeaponSkillAnimationClient.calculatePlaybackProgress(
                        packetReceiptTick + 3,
                        packetReceiptTick,
                        0.0f,
                        8
                ),
                0.0001f
        );
    }
}
