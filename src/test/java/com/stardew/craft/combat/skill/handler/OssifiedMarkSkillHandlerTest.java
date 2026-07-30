package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssifiedMarkSkillHandlerTest {
    @Test
    void preservesTheAuthoredMarkAndFirstCriticalBonusContract() {
        WeaponData ossifiedBlade = WeaponRegistry.get("ossified_blade");
        assertNotNull(ossifiedBlade);
        WeaponSkillData skill = ossifiedBlade.getSkill1();
        assertNotNull(skill);

        assertEquals("ossified_mark", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(6.0, OssifiedMarkSkillHandler.TARGET_RANGE);
        assertEquals(60, OssifiedMarkSkillHandler.MARK_DURATION_TICKS);
        assertEquals(0.10F, OssifiedMarkTracker.CRIT_CHANCE_BONUS);
        assertEquals(1.0F, OssifiedMarkTracker.BONUS_DAMAGE_MULTIPLIER);
        assertEquals(
                80,
                OssifiedMarkTracker.UNTRIGGERED_COOLDOWN_TOTAL_TICKS
        );
        assertTrue(new OssifiedMarkSkillHandler().completesImmediately());
    }
}
