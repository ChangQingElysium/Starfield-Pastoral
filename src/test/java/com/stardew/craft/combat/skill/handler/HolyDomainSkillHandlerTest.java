package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolyDomainSkillHandlerTest {
    @Test
    void preservesTheAuthoredSanctuaryContract() {
        WeaponData holyBlade = WeaponRegistry.get("holy_blade");
        assertNotNull(holyBlade);
        WeaponSkillData skill = holyBlade.getSkill2();
        assertNotNull(skill);

        assertEquals("holy_domain", skill.getId());
        assertEquals(75, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(10.0F, HolyDomainSkillHandler.ENERGY_COST);
        assertEquals(80, HolyDomainSkillHandler.DURATION_TICKS);
        assertEquals(4.0F, HolyDomainSkillHandler.MAX_RADIUS);
        assertEquals(20, HolyDomainSkillHandler.PULSE_INTERVAL_TICKS);
        assertEquals(
                0.75F,
                HolyDomainSkillHandler.PULSE_DAMAGE_MULTIPLIER
        );
        assertEquals(
                5,
                HolyDomainSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(4, HolyDomainSkillHandler.HEAL_AMOUNT);
        assertEquals(12, HolyDomainSkillHandler.RING_DURATION_TICKS);
        assertEquals(
                4,
                HolyDomainSkillHandler.DURATION_TICKS
                        / HolyDomainSkillHandler.PULSE_INTERVAL_TICKS
        );
        assertEquals(
                16,
                HolyDomainSkillHandler.HEAL_AMOUNT
                        * (HolyDomainSkillHandler.DURATION_TICKS
                        / HolyDomainSkillHandler.PULSE_INTERVAL_TICKS)
        );
        assertFalse(new HolyDomainSkillHandler().completesImmediately());
    }

    @Test
    void energyValidationIsSideEffectFreeAndHonorsFreeCastModes() {
        assertTrue(HolyDomainSkillHandler.canPayEnergy(10.0F, false, false));
        assertFalse(HolyDomainSkillHandler.canPayEnergy(9.99F, false, false));
        assertTrue(HolyDomainSkillHandler.canPayEnergy(0.0F, true, false));
        assertTrue(HolyDomainSkillHandler.canPayEnergy(0.0F, false, true));
    }
}
