package com.stardew.craft.combat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentNegativeStatusProtectionTest {
    @Test
    void oneDecisionControlsTheWholeCompositeStatus() {
        EquipmentNegativeStatusProtection.Decision normal =
                EquipmentNegativeStatusProtection.decide(
                        40,
                        false,
                        false
        );
        assertFalse(normal.resisted());
        assertEquals(40, normal.durationTicks());
        assertFalse(normal.durationReduced());
        assertEquals(60, normal.adjustRelatedDurationTicks(60));

        EquipmentNegativeStatusProtection.Decision sturdy =
                EquipmentNegativeStatusProtection.decide(
                        40,
                        false,
                        true
        );
        assertFalse(sturdy.resisted());
        assertEquals(20, sturdy.durationTicks());
        assertTrue(sturdy.durationReduced());
        assertEquals(30, sturdy.adjustRelatedDurationTicks(60));

        EquipmentNegativeStatusProtection.Decision resisted =
                EquipmentNegativeStatusProtection.decide(
                        40,
                        true,
                        false
        );
        assertTrue(resisted.resisted());
        assertEquals(0, resisted.durationTicks());
        assertEquals(0, resisted.adjustRelatedDurationTicks(60));
    }
}
