package com.stardew.craft.combat.skill;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SilverSaberSkillHelperTest {
    @Test
    void helperPreservesTheAuthoredStrikeContextAndTiming() {
        WeaponData silverSaber = WeaponRegistry.get("silver_saber");
        assertNotNull(silverSaber);
        WeaponSkillData skill = silverSaber.getSkill1();
        assertNotNull(skill);

        SkillContext context =
                SilverSaberSkillHelper.createSkillContext(skill);
        assertEquals("silver_foldback", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.2F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(5, SilverSaberSkillHelper.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, SilverSaberSkillHelper.SKILL_ANIM_TICKS);
        assertEquals(20, SilverSaberFoldbackState.DEFAULT_DURATION_TICKS);
    }
}
