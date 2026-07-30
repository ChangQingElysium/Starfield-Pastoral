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

class BurglarShankSkillHandlerTest {
    @Test
    void hitContextPreservesTheAuthoredSingleSlashContract() {
        WeaponData burglarShank = WeaponRegistry.get("burglars_shank");
        assertNotNull(burglarShank);
        WeaponSkillData skill = burglarShank.getSkill1();
        assertNotNull(skill);

        SkillContext context = BurglarShankSkillHandler.createHitContext(skill);

        assertEquals("burglar_shank", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.5F, context.getDamageMultiplier());
        assertEquals(0.0F, context.getCritChanceBonus());
        assertFalse(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
        assertEquals(7, skill.getCooldown());
    }

    @Test
    void targetingAndLifecycleConstantsRemainUnchanged() {
        assertEquals(4.0, BurglarShankSkillHandler.TARGET_RANGE);
        assertEquals(5, BurglarShankSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, BurglarShankSkillHandler.ANIMATION_TICKS);
        assertTrue(new BurglarShankSkillHandler().completesImmediately());
    }
}
