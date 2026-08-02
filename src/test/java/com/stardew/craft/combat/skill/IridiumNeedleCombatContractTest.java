package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumNeedleCombatContractTest {
    @Test
    void successfulDamageCyclesThePassiveBeforeTheNextGuaranteedCrit() {
        assertFalse(IridiumNeedleCritTracker.guaranteesCritAtStacks(0));
        assertEquals(1, IridiumNeedleCritTracker.nextStacks(0));
        assertFalse(IridiumNeedleCritTracker.guaranteesCritAtStacks(1));
        assertEquals(2, IridiumNeedleCritTracker.nextStacks(1));
        assertTrue(IridiumNeedleCritTracker.guaranteesCritAtStacks(2));
        assertEquals(0, IridiumNeedleCritTracker.nextStacks(2));
    }
}
