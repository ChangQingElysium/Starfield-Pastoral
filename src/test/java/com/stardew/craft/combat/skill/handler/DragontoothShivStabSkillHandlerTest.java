package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragontoothShivStabSkillHandlerTest {
    @Test
    void preservesAuthoredStabAndFreezeContract() {
        WeaponData shiv = WeaponRegistry.get("dragontooth_shiv");
        assertNotNull(shiv);
        WeaponSkillData skill = shiv.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext =
                DragontoothShivStabSkillHandler.createHitContext(skill);

        assertEquals("dragontooth_shiv_stab", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(1.20F, hitContext.getDamageMultiplier());
        assertEquals(4.0, DragontoothShivStabSkillHandler.TARGET_RANGE);
        assertEquals(
                40,
                DragontoothShivStabSkillHandler.FREEZE_DURATION_TICKS
        );
        assertEquals(
                5,
                DragontoothShivStabSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, DragontoothShivStabSkillHandler.ANIMATION_TICKS);
        assertTrue(
                new DragontoothShivStabSkillHandler()
                        .completesImmediately()
        );
    }
}
