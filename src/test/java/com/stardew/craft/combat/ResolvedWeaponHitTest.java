package com.stardew.craft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedWeaponHitTest {
    @Test
    void onlyTheFrameThatObservedAnAliveTargetCanClaimTheKill() {
        assertTrue(ResolvedWeaponHit.isKillTransition(
                true,
                false,
                false,
                0.0F
        ));
        assertFalse(ResolvedWeaponHit.isKillTransition(
                false,
                false,
                false,
                0.0F
        ));
    }

    @Test
    void survivingAndCollapsedTargetsAreNotKillTransitions() {
        assertFalse(ResolvedWeaponHit.isKillTransition(
                true,
                false,
                true,
                1.0F
        ));
        assertFalse(ResolvedWeaponHit.isKillTransition(
                true,
                true,
                false,
                0.0F
        ));
    }

    @Test
    void positiveHealthDamageRequiresALiveTargetAtApplicationStart() {
        assertTrue(ResolvedWeaponHit.isPositiveHealthDamage(true, 4.0F));
        assertFalse(ResolvedWeaponHit.isPositiveHealthDamage(true, 0.0F));
        assertFalse(ResolvedWeaponHit.isPositiveHealthDamage(false, 4.0F));
    }
}
