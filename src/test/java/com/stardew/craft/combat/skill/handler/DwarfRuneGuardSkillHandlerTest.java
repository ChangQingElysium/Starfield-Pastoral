package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DwarfRuneGuardSkillHandlerTest {
    @Test
    void preservesTheAuthoredGuardSlashContract() {
        WeaponData dwarfSword = WeaponRegistry.get("dwarf_sword");
        assertNotNull(dwarfSword);
        WeaponSkillData skill = dwarfSword.getSkill1();
        assertNotNull(skill);

        SkillContext hit =
                DwarfRuneGuardSkillHandler.createHitContext(skill);

        assertEquals("dwarf_rune_guard", skill.getId());
        assertEquals(110, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MINOR, hit.getTier());
        assertEquals(1.10F, hit.getDamageMultiplier());
        assertFalse(hit.isIgnoreDefense());
        assertFalse(hit.isGuaranteedCrit());
        assertEquals(4.5D, DwarfRuneGuardSkillHandler.TARGET_RANGE);
        assertEquals(
                50,
                DwarfRuneGuardSkillHandler.SHELTER_DURATION_TICKS
        );
        assertEquals(1, DwarfRuneGuardSkillHandler.SHELTER_AMPLIFIER);
        assertEquals(
                40,
                DwarfRuneGuardSkillHandler.SLOW_DURATION_TICKS
        );
        assertEquals(0, DwarfRuneGuardSkillHandler.SLOW_AMPLIFIER);
        assertEquals(
                5,
                DwarfRuneGuardSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, DwarfRuneGuardSkillHandler.ANIMATION_TICKS);
        assertFalse(new DwarfRuneGuardSkillHandler().completesImmediately());
    }

    @Test
    void targetSelectionKeepsTheHitAndMissEnergyRewards() {
        assertEquals(
                6.0F,
                DwarfRuneGuardSkillHandler.energyRestoreForTarget(true)
        );
        assertEquals(
                3.0F,
                DwarfRuneGuardSkillHandler.energyRestoreForTarget(false)
        );
    }
}
