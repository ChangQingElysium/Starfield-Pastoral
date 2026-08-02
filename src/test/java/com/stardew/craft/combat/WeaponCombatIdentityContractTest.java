package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponCombatIdentityContractTest {
    @Test
    void builtInAndPublicWeaponsShareTheOrdinaryDamageBoundary()
            throws IOException {
        String identity = source("combat/WeaponCombatIdentity.java");
        String events = source("combat/WeaponCombatEvents.java");
        String calculator = source("combat/DamageCalculator.java");
        String skillDamage = source(
                "combat/skill/WeaponSkillDamage.java"
        );
        String nativeAttacks = source(
                "combat/equipment/CrossDimensionNativeAttackHandler.java"
        );
        String attributes = source(
                "combat/equipment/EquipmentPlayerAttributes.java"
        );

        assertTrue(identity.contains("instanceof IStardewWeapon"));
        assertTrue(identity.contains("EquipmentSlotResolver.isWeapon(stack)"));
        assertTrue(events.contains(
                "WeaponCombatIdentity.resolve(weapon).orElse(null)"
        ));
        assertTrue(calculator.contains(
                "WeaponCombatIdentity.resolve(weapon)"
        ));
        assertTrue(skillDamage.contains(
                "WeaponCombatIdentity.isWeapon(weaponSnapshot.weapon())"
        ));
        assertTrue(nativeAttacks.contains(
                "WeaponCombatIdentity.isWeapon(weapon)"
        ));
        assertTrue(attributes.contains(
                "weaponRawAttackSpeedCorrection("
        ));
        assertTrue(attributes.contains(
                "if (weaponIdentity != null)"
        ));
        assertFalse(attributes.contains(
                "!weaponIdentity.builtIn()"
        ));
        assertTrue(attributes.contains(
                "stack.forEachModifier("
        ));
        assertTrue(attributes.contains(
                "EquipmentSlot.MAINHAND"
        ));
        assertTrue(attributes.contains(
                "Attributes.ATTACK_SPEED"
        ));
    }

    private static String source(String relativeSource) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeSource);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
