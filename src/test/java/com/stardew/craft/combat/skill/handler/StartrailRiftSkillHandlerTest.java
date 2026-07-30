package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartrailRiftSkillHandlerTest {
    @Test
    void authoredDashDamageAndRewardContractRemainStable() {
        WeaponData galaxySword = WeaponRegistry.get("galaxy_sword");
        assertNotNull(galaxySword);
        WeaponSkillData skill = galaxySword.getSkill1();
        assertNotNull(skill);

        SkillContext normal =
                StartrailRiftSkillHandler.createHitContext(skill, false);
        SkillContext boosted =
                StartrailRiftSkillHandler.createHitContext(skill, true);

        assertEquals("startrail_rift", skill.getId());
        assertEquals(140, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MINOR, normal.getTier());
        assertEquals(1.4F, normal.getDamageMultiplier());
        assertEquals(0.0F, normal.getCritChanceBonus());
        assertEquals(0.20F, boosted.getCritChanceBonus());
        assertEquals(4.5D, StartrailRiftSkillHandler.DASH_DISTANCE);
        assertEquals(0.9D, StartrailRiftSkillHandler.PATH_HIT_RADIUS);
        assertEquals(5, StartrailRiftSkillHandler.DASH_DURATION_TICKS);
        assertEquals(2, StartrailRiftSkillHandler.HIT_STARTRAIL_RESTORE);
        assertEquals(6.0F, StartrailRiftSkillHandler.HIT_ENERGY_RESTORE);
        assertEquals(3.0F, StartrailRiftSkillHandler.HIT_HEALTH_RESTORE);
        assertEquals(140, StartrailRiftSkillHandler.SPEED_DURATION_TICKS);
        assertEquals(8, StartrailRiftSkillHandler.ANIMATION_TICKS);
        assertTrue(
                new StartrailRiftSkillHandler().completesImmediately()
        );
    }

    @Test
    void sixStacksAtomicallyEnableCritAndBoostedPresentation() {
        assertFalse(StartrailRiftSkillHandler.isBoostedForStacks(5));
        assertTrue(StartrailRiftSkillHandler.isBoostedForStacks(6));
        assertEquals(
                0.0F,
                StartrailRiftSkillHandler
                        .criticalChanceBonusForStacks(5)
        );
        assertEquals(
                0.20F,
                StartrailRiftSkillHandler
                        .criticalChanceBonusForStacks(6)
        );
        assertEquals(
                VfxColors.GALAXY_PURPLE,
                StartrailRiftSkillHandler.presentationColor(false)
        );
        assertFalse(
                VfxColors.GALAXY_PURPLE
                        == StartrailRiftSkillHandler
                                .presentationColor(true)
        );
    }

    @Test
    void riftSegmentationPreservesOriginalSixToTenClamp() {
        assertEquals(6, StartrailRiftSkillHandler.riftSegmentCount(0.1D));
        assertEquals(6, StartrailRiftSkillHandler.riftSegmentCount(4.5D));
        assertEquals(10, StartrailRiftSkillHandler.riftSegmentCount(20.0D));
    }
}
