package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClaymoreFoldbackSkillHandlerTest {
    @Test
    void preservesTheAuthoredInitialStrikeAndCooldownContract() {
        WeaponData claymore = WeaponRegistry.get("claymore");
        assertNotNull(claymore);
        WeaponSkillData skill = claymore.getSkill1();
        assertNotNull(skill);

        SkillContext context =
                ClaymoreFoldbackSkillHandler.createInitialContext(skill);

        assertEquals("claymore_foldback", skill.getId());
        assertEquals(70, skill.getDamagePercent());
        assertEquals(8, skill.getCooldown());
        assertEquals("claymore_foldback", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(0.7F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(4.0D, ClaymoreFoldbackSkillHandler.INITIAL_TARGET_RANGE);
        assertEquals(12, ClaymoreFoldbackSkillHandler.RETURN_DELAY_TICKS);
        assertEquals(
                5,
                ClaymoreFoldbackSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                12,
                ClaymoreFoldbackSkillHandler.INITIAL_ANIMATION_TICKS
        );
        assertFalse(
                new ClaymoreFoldbackSkillHandler().completesImmediately()
        );
    }
}
