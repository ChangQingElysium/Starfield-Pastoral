package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumNeedleCombatContractTest {
    @Test
    void tripleSlashKeepsItsThreeTickCadenceAndFinalGuaranteedCrit() {
        assertEquals(103L, IridiumNeedleThrustTracker.nextStrikeTick(100L));
        assertFalse(IridiumNeedleThrustTracker.isGuaranteedCritStrike(3));
        assertFalse(IridiumNeedleThrustTracker.isGuaranteedCritStrike(2));
        assertTrue(IridiumNeedleThrustTracker.isGuaranteedCritStrike(1));
    }

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
