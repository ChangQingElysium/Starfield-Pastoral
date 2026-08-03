package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBirthdayGiftLimitTest {
    @Test
    void birthdayGiftBypassesAFullWeeklyGiftAllowance() {
        assertTrue(NpcInteractionService.weeklyGiftLimitBlocks(2, false));
        assertFalse(NpcInteractionService.weeklyGiftLimitBlocks(2, true));
    }
}
