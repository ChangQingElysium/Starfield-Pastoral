package com.stardew.craft.player;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassOutServiceTest {
    @Test
    void combatRescueCannotStartBeforeEightSecondCollapseDeadline() {
        assertFalse(PassOutService.hasReachedCombatCollapseDeadline(100, 260));
        assertFalse(PassOutService.hasReachedCombatCollapseDeadline(259, 260));
        assertTrue(PassOutService.hasReachedCombatCollapseDeadline(260, 260));
    }


    @Test
    void ownBedWaivesOvernightMoneyLoss() {
        assertEquals(0, PassOutService.calculateOvernightMoneyLoss(50_000, true));
    }

    @Test
    void overnightMoneyLossUsesTenPercentWithCap() {
        assertEquals(50, PassOutService.calculateOvernightMoneyLoss(500, false));
        assertEquals(1_000, PassOutService.calculateOvernightMoneyLoss(50_000, false));
        assertEquals(0, PassOutService.calculateOvernightMoneyLoss(0, false));
    }

    @Test
    void completedCommunityCenterHasOriginalMarlonFreeRescueOverride() {
        Random marlonRoll = new Random() {
            @Override
            public double nextDouble() {
                return 0.1D;
            }
        };
        assertEquals(
                PassOutService.PassOutMailChoice.MARLON,
                PassOutService.selectDefaultPassOutMail(marlonRoll, true, true));
    }

    @Test
    void defaultCandidatePoolIncludesJojaOnlyBeforeCompletion() {
        Random chooseSecond = new Random() {
            @Override
            public double nextDouble() {
                return 0.9D;
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(1, bound - 1);
            }
        };
        assertEquals(
                PassOutService.PassOutMailChoice.JOJA,
                PassOutService.selectDefaultPassOutMail(
                        chooseSecond, false, true));
        assertEquals(
                PassOutService.PassOutMailChoice.LINUS,
                PassOutService.selectDefaultPassOutMail(
                        chooseSecond, true, true));
    }
}
