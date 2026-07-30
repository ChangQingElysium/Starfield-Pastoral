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

class HolySmiteSkillHandlerTest {
    @Test
    void preservesTheAuthoredHitHealAndDodgeContract() {
        WeaponData holyBlade = WeaponRegistry.get("holy_blade");
        assertNotNull(holyBlade);
        WeaponSkillData skill = holyBlade.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext = HolySmiteSkillHandler.createHitContext(skill);

        assertEquals("holy_smite", hitContext.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(0.90F, hitContext.getDamageMultiplier());
        assertFalse(hitContext.isIgnoreDefense());
        assertFalse(hitContext.isGuaranteedCrit());
        assertEquals(0.0F, hitContext.getCritChanceBonus());
        assertEquals(6, skill.getCooldown());
        assertEquals(4.5, HolySmiteSkillHandler.TARGET_RANGE);
        assertEquals(6, HolySmiteSkillHandler.HEAL_AMOUNT);
        assertEquals(40, HolySmiteSkillHandler.DODGE_DURATION_TICKS);
        assertEquals(0.20F, HolySmiteSkillHandler.DODGE_CHANCE);
        assertEquals(5, HolySmiteSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, HolySmiteSkillHandler.ANIMATION_TICKS);
        assertTrue(new HolySmiteSkillHandler().completesImmediately());
    }
}
