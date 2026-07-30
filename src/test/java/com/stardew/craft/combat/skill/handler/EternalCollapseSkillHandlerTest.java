package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EternalCollapseSkillHandlerTest {
    @Test
    void preservesTheAuthoredFieldAndCooldownContract() {
        WeaponData infinityBlade = WeaponRegistry.get("infinity_blade");
        assertNotNull(infinityBlade);
        WeaponSkillData skill = infinityBlade.getSkill2();
        assertNotNull(skill);

        assertEquals("eternal_collapse", skill.getId());
        assertEquals(80, skill.getDamagePercent());
        assertEquals(25, skill.getCooldown());
        assertEquals(70, EternalCollapseSkillHandler.ACTIVE_DURATION_TICKS);
        assertEquals(6, EternalCollapseSkillHandler.BASE_STRIKE_COUNT);
        assertEquals(4.0D, EternalCollapseSkillHandler.EFFECT_RADIUS);
        assertEquals(
                3.0F,
                EternalCollapseSkillHandler
                        .FINAL_STRIKE_DAMAGE_MULTIPLIER
        );
        assertEquals(12, EternalCollapseSkillHandler.ANIMATION_TICKS);
        assertFalse(
                new EternalCollapseSkillHandler().completesImmediately()
        );
    }

    @Test
    void singularityStacksMapToStrikeCritAndFinisherTiers() {
        assertTier(0, 6, 0.0F, false);
        assertTier(4, 6, 0.0F, false);
        assertTier(5, 7, 0.05F, false);
        assertTier(15, 9, 0.15F, false);
        assertTier(16, 9, 0.15F, true);
        assertTier(20, 10, 0.20F, true);
        assertTier(25, 10, 0.20F, true);
    }

    private static void assertTier(
            int stacks,
            int expectedStrikes,
            float expectedCritBonus,
            boolean expectedFinalStrike
    ) {
        assertEquals(
                expectedStrikes,
                EternalCollapseSkillHandler
                        .totalStrikeCountForStacks(stacks)
        );
        assertEquals(
                expectedCritBonus,
                EternalCollapseSkillHandler
                        .criticalChanceBonusForStacks(stacks),
                1.0E-6F
        );
        assertEquals(
                expectedFinalStrike,
                EternalCollapseSkillHandler
                        .hasFinalStrikeForStacks(stacks)
        );
        if (expectedFinalStrike) {
            assertTrue(stacks >= 16);
        }
    }
}
