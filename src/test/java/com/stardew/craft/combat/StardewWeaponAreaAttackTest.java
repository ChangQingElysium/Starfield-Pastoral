package com.stardew.craft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewWeaponAreaAttackTest {
    @Test
    void daggerUsesANarrowFacingAreaRatherThanBecomingSingleTarget() {
        assertTrue(StardewWeaponAreaAttack.contains(
                WeaponType.DAGGER, 1.2D, 0.4D, 0.0D
        ));
        assertFalse(StardewWeaponAreaAttack.contains(
                WeaponType.DAGGER, 1.2D, 0.8D, 0.0D
        ));
    }

    @Test
    void swordAndClubProjectProgressivelyWiderFacingAreas() {
        assertTrue(StardewWeaponAreaAttack.contains(
                WeaponType.SWORD, 1.8D, 1.2D, 0.0D
        ));
        assertFalse(StardewWeaponAreaAttack.contains(
                WeaponType.SWORD, 1.8D, 1.5D, 0.0D
        ));
        assertTrue(StardewWeaponAreaAttack.contains(
                WeaponType.CLUB, 1.8D, 1.5D, 0.0D
        ));
    }

    @Test
    void rangedTypeNeverOwnsMeleeArea() {
        assertFalse(StardewWeaponAreaAttack.contains(
                WeaponType.SLINGSHOT, 0.0D, 0.0D, 0.0D
        ));
    }
}
