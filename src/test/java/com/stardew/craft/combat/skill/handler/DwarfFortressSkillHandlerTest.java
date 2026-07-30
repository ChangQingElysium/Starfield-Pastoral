package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DwarfFortressTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfFortressSkillHandlerTest {
    @Test
    void preservesTheAuthoredFortressContract() {
        WeaponData dwarfSword = WeaponRegistry.get("dwarf_sword");
        assertNotNull(dwarfSword);
        WeaponSkillData skill = dwarfSword.getSkill2();
        assertNotNull(skill);

        assertEquals("dwarf_fortress", skill.getId());
        assertEquals(220, skill.getDamagePercent());
        assertEquals(2.2F, skill.getDamagePercent() / 100.0F);
        assertEquals(20, skill.getCooldown());
        assertEquals(10.0F, DwarfFortressSkillHandler.ENERGY_COST);
        assertEquals(80, DwarfFortressTracker.ACTIVE_DURATION_TICKS);
        assertEquals(4, DwarfFortressTracker.MAX_REACTIVE_SHOCKS);
        assertEquals(1, DwarfFortressTracker.SHELTER_AMPLIFIER);
        assertEquals(
                1.0D,
                DwarfFortressTracker.KNOCKBACK_RESISTANCE_BONUS
        );
        assertEquals(3.5F, DwarfFortressTracker.INITIAL_SHOCK_RADIUS);
        assertEquals(3.0F, DwarfFortressTracker.REACTIVE_SHOCK_RADIUS);
        assertEquals(
                1.0F,
                DwarfFortressTracker.REACTIVE_DAMAGE_MULTIPLIER
        );
        assertEquals(4.0F, DwarfFortressTracker.ECHO_RADIUS);
        assertEquals(
                1.2F,
                DwarfFortressTracker.ECHO_DAMAGE_MULTIPLIER
        );
        assertEquals(12, DwarfFortressTracker.RING_DURATION_TICKS);
        assertFalse(
                new DwarfFortressSkillHandler().completesImmediately()
        );
    }

    @Test
    void energyGateKeepsCreativeAndBlessingExemptions() {
        assertFalse(DwarfFortressSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(DwarfFortressSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(DwarfFortressSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(DwarfFortressSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }
}
