package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TetanusStrikeSkillHandlerTest {
    @Test
    void hitContextPreservesTheAuthoredMinorSkillDamageContract() {
        WeaponData rustySword = WeaponRegistry.get("rusty_sword");
        assertNotNull(rustySword);
        WeaponSkillData skill = rustySword.getSkill1();
        assertNotNull(skill);

        SkillContext context = TetanusStrikeSkillHandler.createHitContext(skill);

        assertEquals("tetanus_strike", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.0F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(0.0F, context.getCritChanceBonus());
    }
}
