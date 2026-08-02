package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumNeedleFrenzySkillHandlerTest {
    @Test
    void preservesTheAuthoredActivationAndCriticalRewardContract() {
        WeaponData iridiumNeedle = WeaponRegistry.get("iridium_needle");
        assertNotNull(iridiumNeedle);
        WeaponSkillData skill = iridiumNeedle.getSkill2();
        assertNotNull(skill);

        assertEquals("iridium_needle_frenzy", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(18, skill.getCooldown());
        assertEquals(10.0F, IridiumNeedleFrenzySkillHandler.ENERGY_COST);
        assertEquals(120, IridiumNeedleFrenzySkillHandler.DURATION_TICKS);
        assertEquals(0, IridiumNeedleFrenzySkillHandler.SPEED_AMPLIFIER);
        assertEquals(
                0.30F,
                IridiumNeedleFrenzySkillHandler.CRIT_CHANCE_BONUS
        );
        assertEquals(
                5,
                IridiumNeedleFrenzySkillHandler.CRITICAL_HEAL_AMOUNT
        );
        assertEquals(
                10.0F,
                IridiumNeedleFrenzySkillHandler
                        .CRITICAL_ENERGY_RESTORE
        );
        assertEquals(
                40,
                IridiumNeedleFrenzySkillHandler
                        .CRITICAL_VULNERABLE_DURATION_TICKS
        );
        assertEquals(
                1,
                IridiumNeedleFrenzySkillHandler
                        .CRITICAL_VULNERABLE_AMPLIFIER
        );
        assertFalse(
                new IridiumNeedleFrenzySkillHandler().completesImmediately()
        );
    }

    @Test
    void failedEnergyValidationIsSideEffectFreeBeforeBegin() {
        assertFalse(IridiumNeedleFrenzySkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(IridiumNeedleFrenzySkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(IridiumNeedleFrenzySkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(IridiumNeedleFrenzySkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }

    @Test
    void logoutCleanupDoesNotSendAStopPacketToAnUnavailableCaster() {
        assertFalse(IridiumNeedleFrenzySkillHandler.shouldNotifyOnFinish(
                SkillInstance.EndReason.CASTER_UNAVAILABLE
        ));
        assertTrue(IridiumNeedleFrenzySkillHandler.shouldNotifyOnFinish(
                SkillInstance.EndReason.INVALIDATED
        ));
    }
}
