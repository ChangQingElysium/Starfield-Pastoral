package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindSpireThrustSkillHandlerTest {
    @Test
    void preservesTheAuthoredHitGaleAndNoTargetDashContract() {
        WeaponData windSpire = WeaponRegistry.get("wind_spire");
        assertNotNull(windSpire);
        WeaponSkillData skill = windSpire.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext =
                WindSpireThrustSkillHandler.createHitContext(skill);

        assertEquals("wind_spire_thrust", hitContext.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(1.5F, hitContext.getDamageMultiplier());
        assertFalse(hitContext.isIgnoreDefense());
        assertFalse(hitContext.isGuaranteedCrit());
        assertEquals(0.0F, hitContext.getCritChanceBonus());
        assertEquals(6, skill.getCooldown());
        assertEquals(6.0, WindSpireThrustSkillHandler.TARGET_RANGE);
        assertEquals(4.0, WindSpireThrustSkillHandler.NO_TARGET_DASH_DISTANCE);
        assertEquals(5, WindSpireThrustSkillHandler.DASH_DURATION_TICKS);
        assertEquals(60, WindSpireThrustSkillHandler.GALE_DURATION_TICKS);
        assertEquals(0, WindSpireThrustSkillHandler.SPEED_AMPLIFIER);
        assertEquals(
                5,
                WindSpireThrustSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, WindSpireThrustSkillHandler.ANIMATION_TICKS);
        assertTrue(new WindSpireThrustSkillHandler().completesImmediately());
    }
}
