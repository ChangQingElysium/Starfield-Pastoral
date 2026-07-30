package com.stardew.craft.item.weapon;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponRegistryContractTest {
    @Test
    void everyRegisteredWeaponHasAValidDefinitionAndCanonicalSkillIds() {
        assertFalse(WeaponRegistry.getAll().isEmpty());
        Set<String> ids = new HashSet<>();

        for (WeaponData weapon : WeaponRegistry.getAll()) {
            assertTrue(ids.add(weapon.getId()), () -> "duplicate weapon id " + weapon.getId());
            assertEquals(weapon, WeaponRegistry.get(weapon.getId()));
            assertNotNull(weapon.getWeaponType());
            assertTrue(weapon.getDamageMin() >= 0, weapon::getId);
            assertTrue(weapon.getDamageMax() >= weapon.getDamageMin(), weapon::getId);

            verifySkill(weapon.getSkill1());
            verifySkill(weapon.getSkill2());
        }

        assertEquals(ids, new HashSet<>(WeaponRegistry.getAllIds()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> WeaponRegistry.getAllIds().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> WeaponRegistry.getAll().clear()
        );
    }

    @Test
    void buildersRejectDefinitionsThatWouldCorruptRuntimeContracts() {
        assertThrows(
                IllegalStateException.class,
                () -> WeaponData.builder("broken").damage(5, 2).build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponSkillData.builder("Invalid Skill Id").build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponSkillData.builder("negative_cooldown").cooldown(-1).build()
        );
    }

    private static void verifySkill(WeaponSkillData skill) {
        if (skill == null) {
            return;
        }
        assertNotNull(skill.getResourceId());
        assertTrue(skill.matches(skill.getResourceId()), skill::getId);
        assertTrue(skill.getDamagePercent() >= 0, skill::getId);
        assertTrue(skill.getCooldown() >= 0, skill::getId);
    }
}
