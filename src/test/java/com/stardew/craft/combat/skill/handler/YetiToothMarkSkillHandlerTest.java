package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.YetiToothMarkTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YetiToothMarkSkillHandlerTest {
    @Test
    void preservesTheAuthoredHitMarkAndFreezeContract() {
        WeaponData yetiTooth = WeaponRegistry.get("yeti_tooth");
        assertNotNull(yetiTooth);
        WeaponSkillData skill = yetiTooth.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext = YetiToothMarkSkillHandler.createHitContext(skill);

        assertEquals("yeti_tooth_mark", hitContext.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(1.10F, hitContext.getDamageMultiplier());
        assertFalse(hitContext.isIgnoreDefense());
        assertFalse(hitContext.isGuaranteedCrit());
        assertEquals(0.0F, hitContext.getCritChanceBonus());
        assertEquals(6, skill.getCooldown());
        assertEquals(4.0, YetiToothMarkSkillHandler.TARGET_RANGE);
        assertEquals(5, YetiToothMarkSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, YetiToothMarkSkillHandler.ANIMATION_TICKS);
        assertEquals(60, YetiToothMarkTracker.MARK_DURATION_TICKS);
        assertEquals(60, YetiToothMarkTracker.SLOW_DURATION_TICKS);
        assertEquals(0, YetiToothMarkTracker.SLOW_AMPLIFIER);
        assertEquals(40, YetiToothMarkTracker.FREEZE_DURATION_TICKS);
        assertTrue(new YetiToothMarkSkillHandler().completesImmediately());
    }
}
