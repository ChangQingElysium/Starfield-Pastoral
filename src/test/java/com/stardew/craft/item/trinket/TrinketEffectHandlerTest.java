package com.stardew.craft.item.trinket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrinketEffectHandlerTest {
    @Test
    void fairyCombatDamageCombinesIncomingAndOutgoingWithoutOverflow() {
        assertEquals(
                17,
                TrinketEffectHandler.accumulateFairyCombatDamage(10, 7)
        );
        assertEquals(
                Integer.MAX_VALUE,
                TrinketEffectHandler.accumulateFairyCombatDamage(
                        Integer.MAX_VALUE - 2,
                        7
                )
        );
    }

    @Test
    void fairyCombatDamageRejectsNegativeContributionsAndState() {
        assertEquals(
                10,
                TrinketEffectHandler.accumulateFairyCombatDamage(10, -3)
        );
        assertEquals(
                0,
                TrinketEffectHandler.accumulateFairyCombatDamage(-10, 0)
        );
    }
}
