package com.stardew.craft.combat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentStatsTest {
    @Test
    void stackableReactiveEffectsKeepTheirCountsWhenMerged() {
        EquipmentStats protection = EquipmentStats.builder()
                .protection(true)
                .thorns(true)
                .attackMultiplier(0.1f)
                .weaponSpeedMultiplier(0.1f)
                .build();
        EquipmentStats phoenix = EquipmentStats.builder().phoenix(true).build();

        EquipmentStats merged = EquipmentStats.merge(
                protection,
                protection,
                phoenix,
                phoenix
        );

        assertEquals(2, merged.getProtectionCount());
        assertEquals(2, merged.getPhoenixCount());
        assertEquals(2, merged.getThornsCount());
        assertEquals(0.2f, merged.getAttackMultiplier());
        assertEquals(0.2f, merged.getWeaponSpeedMultiplier());
        assertTrue(merged.hasPhoenix());
    }
}
