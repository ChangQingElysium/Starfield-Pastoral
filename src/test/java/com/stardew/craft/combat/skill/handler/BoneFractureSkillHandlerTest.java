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

class BoneFractureSkillHandlerTest {
    @Test
    void preservesTheAuthoredHitAndDebuffContract() {
        WeaponData boneSword = WeaponRegistry.get("bone_sword");
        assertNotNull(boneSword);
        WeaponSkillData skill = boneSword.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext = BoneFractureSkillHandler.createHitContext(skill);

        assertEquals("bone_fracture", hitContext.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(1.2F, hitContext.getDamageMultiplier());
        assertFalse(hitContext.isIgnoreDefense());
        assertFalse(hitContext.isGuaranteedCrit());
        assertEquals(0.0F, hitContext.getCritChanceBonus());
        assertEquals(7, skill.getCooldown());
        assertEquals(4.0, BoneFractureSkillHandler.TARGET_RANGE);
        assertEquals(80, BoneFractureSkillHandler.DEBUFF_DURATION_TICKS);
        assertEquals(0, BoneFractureSkillHandler.WEAKNESS_AMPLIFIER);
        assertEquals(0, BoneFractureSkillHandler.SLOWNESS_AMPLIFIER);
        assertEquals(5, BoneFractureSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, BoneFractureSkillHandler.ANIMATION_TICKS);
        assertTrue(new BoneFractureSkillHandler().completesImmediately());
    }
}
