package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DesperatePlunderSkillHandlerTest {
    @Test
    void hitContextPreservesTheAuthoredMinorSkillDamageContract() {
        WeaponData pirateSword = WeaponRegistry.get("pirate_sword");
        assertNotNull(pirateSword);
        WeaponSkillData skill = pirateSword.getSkill1();
        assertNotNull(skill);

        SkillContext context = DesperatePlunderSkillHandler.createHitContext(skill);

        assertEquals("desperate_plunder", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.4F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(0.0F, context.getCritChanceBonus());
        assertEquals(5, skill.getCooldown());
    }

    @Test
    void authoredHealthCostAndKillHealingRemainUnchanged() {
        assertEquals(2.0F, DesperatePlunderSkillHandler.HEALTH_COST);
        assertEquals(0.5F, DesperatePlunderSkillHandler.MINIMUM_REMAINING_HEALTH);
        assertEquals(4.0F, DesperatePlunderSkillHandler.KILL_HEALING);
    }

    @Test
    void noKillFuryContractRemainsThreeSecondsAtTenPercentDamage() {
        assertEquals(60, DesperatePlunderSkillHandler.FURY_DURATION_TICKS);
        assertEquals(0, DesperatePlunderSkillHandler.FURY_AMPLIFIER);
    }
}
