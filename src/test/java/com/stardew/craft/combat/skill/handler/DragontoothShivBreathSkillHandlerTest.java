package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragontoothShivBreathSkillHandlerTest {
    @Test
    void preservesAuthoredBreathStanceContract() {
        WeaponData shiv = WeaponRegistry.get("dragontooth_shiv");
        assertNotNull(shiv);
        WeaponSkillData skill = shiv.getSkill2();
        assertNotNull(skill);

        assertEquals("dragontooth_shiv_breath", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(18, skill.getCooldown());
        assertEquals(10.0F, DragontoothShivBreathSkillHandler.ENERGY_COST);
        assertEquals(
                120,
                DragontoothShivBreathSkillHandler.ACTIVE_DURATION_TICKS
        );
        assertEquals(
                0,
                DragontoothShivBreathSkillHandler.SPEED_AMPLIFIER
        );
        assertEquals(
                1,
                DragontoothShivBreathSkillHandler.RESISTANCE_AMPLIFIER
        );
        assertEquals(8, DragontoothShivBreathSkillHandler.ANIMATION_TICKS);
        assertFalse(
                new DragontoothShivBreathSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void energyValidationSupportsCreativeAndBlessingExemptions() {
        assertFalse(
                DragontoothShivBreathSkillHandler.canPayEnergy(
                        9.0F,
                        false,
                        false
                )
        );
        assertTrue(
                DragontoothShivBreathSkillHandler.canPayEnergy(
                        0.0F,
                        true,
                        false
                )
        );
        assertTrue(
                DragontoothShivBreathSkillHandler.canPayEnergy(
                        0.0F,
                        false,
                        true
                )
        );
    }
}
