package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TreeBlessingSkillHandlerTest {
    @Test
    void hitContextPreservesTheAuthoredMinorSkillDamageContract() {
        WeaponData woodenBlade = WeaponRegistry.get("wooden_blade");
        assertNotNull(woodenBlade);
        WeaponSkillData skill = woodenBlade.getSkill1();
        assertNotNull(skill);

        SkillContext context = TreeBlessingSkillHandler.createHitContext(skill);

        assertEquals("tree_blessing", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.1F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(0.0F, context.getCritChanceBonus());
    }
}
