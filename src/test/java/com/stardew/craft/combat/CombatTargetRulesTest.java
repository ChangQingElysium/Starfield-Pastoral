package com.stardew.craft.combat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTargetRulesTest {
    @Test
    void vanillaEnemiesAlwaysCountAsCombatMonsters() {
        assertTrue(CombatTargetRules.isCombatMonster(true, List.of()));
        assertTrue(CombatTargetRules.isCombatMonster(
                true,
                List.of("unrelated_marker")
        ));
    }

    @Test
    void stardewMonsterTagsCountEvenForNonEnemyEntityTypes() {
        assertTrue(CombatTargetRules.isCombatMonster(
                false,
                List.of("sd_tier_2", "sd_mob_duggy")
        ));
        assertTrue(CombatTargetRules.isCombatMonster(
                false,
                List.of("sd_mob_custom_addon_monster")
        ));
    }

    @Test
    void passiveEntitiesAndUnrelatedStardewTagsDoNotCount() {
        assertFalse(CombatTargetRules.isCombatMonster(false, List.of()));
        assertFalse(CombatTargetRules.isCombatMonster(
                false,
                List.of("sd_tier_4", "sd_truffle_crab", "farm_animal")
        ));
        assertFalse(CombatTargetRules.isCombatMonster(
                false,
                List.of("sd_mob", "mob_sd_mob_slime", "SD_MOB_SLIME")
        ));
        assertFalse(CombatTargetRules.isCombatMonster(false, null));
    }
}
