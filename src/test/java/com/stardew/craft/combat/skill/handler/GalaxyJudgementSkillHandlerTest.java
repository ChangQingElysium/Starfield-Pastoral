package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.StarfallTracker;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalaxyJudgementSkillHandlerTest {
    @Test
    void authoredMainSlashAndStarfallContractRemainStable() {
        WeaponData galaxySword = WeaponRegistry.get("galaxy_sword");
        assertNotNull(galaxySword);
        WeaponSkillData skill = galaxySword.getSkill2();
        assertNotNull(skill);

        SkillContext normal =
                GalaxyJudgementSkillHandler.createMainSlashContext(
                        skill,
                        false
                );
        SkillContext maximum =
                GalaxyJudgementSkillHandler.createMainSlashContext(
                        skill,
                        true
                );

        assertEquals("galaxy_judgement", skill.getId());
        assertEquals(220, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MAJOR, normal.getTier());
        assertEquals(2.20F, normal.getDamageMultiplier());
        assertFalse(normal.isGuaranteedCrit());
        assertTrue(maximum.isGuaranteedCrit());
        assertEquals(
                4.0D,
                GalaxyJudgementSkillHandler.MAIN_SLASH_RADIUS
        );
        assertEquals(
                12,
                GalaxyJudgementSkillHandler.ANIMATION_TICKS
        );
        assertEquals(
                5,
                GalaxyJudgementSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertFalse(
                new GalaxyJudgementSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void consumingAllStacksMapsToTheOriginalExtraHits() {
        assertEquals(
                0,
                GalaxyJudgementSkillHandler.extraHitsForStacks(0)
        );
        assertEquals(
                0,
                GalaxyJudgementSkillHandler.extraHitsForStacks(3)
        );
        assertEquals(
                1,
                GalaxyJudgementSkillHandler.extraHitsForStacks(4)
        );
        assertEquals(
                2,
                GalaxyJudgementSkillHandler.extraHitsForStacks(8)
        );
        assertEquals(
                3,
                GalaxyJudgementSkillHandler.extraHitsForStacks(
                        StartrailTracker.MAX_STACKS
                )
        );
        assertEquals(
                12,
                StarfallTracker.DEFAULT_STRIKES
                        * (1 + GalaxyJudgementSkillHandler
                                .extraHitsForStacks(12))
        );
    }
}
