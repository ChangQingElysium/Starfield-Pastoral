package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilverFoldbackSkillHandlerTest {
    @Test
    void preservesTheAuthoredTargetReturnAndEmptyDashContract() {
        WeaponData silverSaber = WeaponRegistry.get("silver_saber");
        assertNotNull(silverSaber);
        WeaponSkillData skill = silverSaber.getSkill1();
        assertNotNull(skill);

        assertEquals("silver_foldback", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(5.0, SilverFoldbackSkillHandler.TARGET_RANGE);
        assertEquals(5.0, SilverFoldbackSkillHandler.EMPTY_DASH_DISTANCE);
        assertEquals(
                5,
                SilverFoldbackSkillHandler.EMPTY_DASH_DURATION_TICKS
        );

        assertEquals(
                SilverFoldbackSkillHandler.CastMode.RETURN,
                SilverFoldbackSkillHandler.modeFor(true, true)
        );
        assertEquals(
                SilverFoldbackSkillHandler.CastMode.RETURN,
                SilverFoldbackSkillHandler.modeFor(true, false)
        );
        assertEquals(
                SilverFoldbackSkillHandler.CastMode.TARGET,
                SilverFoldbackSkillHandler.modeFor(false, true)
        );
        assertEquals(
                SilverFoldbackSkillHandler.CastMode.EMPTY,
                SilverFoldbackSkillHandler.modeFor(false, false)
        );
        assertTrue(new SilverFoldbackSkillHandler().completesImmediately());
    }
}
