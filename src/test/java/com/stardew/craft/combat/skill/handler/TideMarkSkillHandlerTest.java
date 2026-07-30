package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.TideMarkTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideMarkSkillHandlerTest {
    @Test
    void preservesTheAuthoredTargetMarkAndCooldownContract() {
        WeaponData neptunesGlaive = WeaponRegistry.get("neptunes_glaive");
        assertNotNull(neptunesGlaive);
        WeaponSkillData skill = neptunesGlaive.getSkill1();
        assertNotNull(skill);

        assertEquals("tide_mark", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(6.0, TideMarkSkillHandler.TARGET_RANGE);
        assertEquals(100, TideMarkSkillHandler.MARK_DURATION_TICKS);
        assertEquals(8, TideMarkSkillHandler.ANIMATION_TICKS);
        assertTrue(new TideMarkSkillHandler().completesImmediately());
    }

    @Test
    void markedHitsUseTheAuthoredIndependentThirtyPercentContext() {
        SkillContext bonus = TideMarkTracker.createBonusContext();

        assertEquals("tide_mark_bonus", bonus.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, bonus.getTier());
        assertEquals(0.30F, bonus.getDamageMultiplier());
        assertFalse(bonus.isIgnoreDefense());
        assertFalse(bonus.isGuaranteedCrit());
        assertEquals(5, TideMarkTracker.HIT_CONTEXT_LIFETIME_TICKS);
    }
}
