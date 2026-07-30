package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.TemplarJudgementTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarJudgementSkillHandlerTest {
    @Test
    void authoredSkillDataAndRuntimeCostsRemainStable() {
        WeaponData templarBlade = WeaponRegistry.get("templars_blade");
        assertNotNull(templarBlade);
        WeaponSkillData skill = templarBlade.getSkill2();
        assertNotNull(skill);

        assertEquals("templar_judgement", skill.getId());
        assertEquals(160, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(6.0D, TemplarJudgementSkillHandler.TARGET_RADIUS);
        assertEquals(10.0F, TemplarJudgementSkillHandler.ENERGY_COST);
        assertEquals(100, TemplarJudgementTracker.DURATION_TICKS);
        assertFalse(new TemplarJudgementSkillHandler().completesImmediately());
    }

    @Test
    void energyPreflightPreservesAuthoredBlessingButNotCreativeBypass() {
        assertFalse(TemplarJudgementSkillHandler.canPayEnergy(9.99F, false));
        assertTrue(TemplarJudgementSkillHandler.canPayEnergy(10.0F, false));
        assertTrue(TemplarJudgementSkillHandler.canPayEnergy(0.0F, true));
    }
}
