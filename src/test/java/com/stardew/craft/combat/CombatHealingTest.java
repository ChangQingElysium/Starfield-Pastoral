package com.stardew.craft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatHealingTest {
    @Test
    void healingIsClampedWithoutChangingItsOneToOneUnit() {
        assertEquals(6.0F, CombatHealing.cappedIncrease(20.0F, 100.0F, 6.0F));
        assertEquals(3.0F, CombatHealing.cappedIncrease(97.0F, 100.0F, 6.0F));
        assertEquals(0.0F, CombatHealing.cappedIncrease(100.0F, 100.0F, 6.0F));
        assertEquals(0.0F, CombatHealing.cappedIncrease(50.0F, 100.0F, -1.0F));
    }

    @Test
    void healthCostsAreClampedAtTheAuthoredNonlethalFloor() {
        assertEquals(6.0F, CombatHealing.nonlethalReduction(100.0F, 6.0F, 1.0F));
        assertEquals(2.0F, CombatHealing.nonlethalReduction(3.0F, 6.0F, 1.0F));
        assertEquals(0.0F, CombatHealing.nonlethalReduction(1.0F, 6.0F, 1.0F));
        assertEquals(0.0F, CombatHealing.nonlethalReduction(50.0F, -1.0F, 1.0F));
    }
}
