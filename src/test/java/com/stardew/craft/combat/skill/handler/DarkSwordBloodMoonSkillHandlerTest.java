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

class DarkSwordBloodMoonSkillHandlerTest {
    @Test
    void preservesTheAuthoredEnergyDurationAndDeferredCooldownContract() {
        WeaponData darkSword = WeaponRegistry.get("dark_sword");
        assertNotNull(darkSword);
        WeaponSkillData skill = darkSword.getSkill2();
        assertNotNull(skill);

        assertEquals("dark_sword_blood_moon", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(10.0F, DarkSwordBloodMoonSkillHandler.ENERGY_COST);
        assertEquals(
                80,
                DarkSwordBloodMoonSkillHandler.ACTIVE_DURATION_TICKS
        );
        assertEquals(
                10,
                DarkSwordBloodMoonSkillHandler.BURN_INTERVAL_TICKS
        );
        assertEquals(
                1,
                DarkSwordBloodMoonSkillHandler
                        .PRESENTATION_NOTIFICATION_TICKS
        );
        assertEquals(
                0.30F,
                DarkSwordBloodMoonSkillHandler.LIFESTEAL_RATIO
        );
        assertEquals(
                1.35F,
                DarkSwordBloodMoonSkillHandler
                        .DAMAGE_BONUS_MULTIPLIER
        );
        assertEquals(
                0.01F,
                DarkSwordBloodMoonSkillHandler
                        .BURN_MAXIMUM_HEALTH_RATIO
        );
        assertEquals(
                1.0F,
                DarkSwordBloodMoonSkillHandler.MINIMUM_BURN_AMOUNT
        );
        assertEquals(
                1.0F,
                DarkSwordBloodMoonSkillHandler
                        .MINIMUM_REMAINING_HEALTH
        );
        assertEquals(
                3.5F,
                DarkSwordBloodMoonSkillHandler.BURST_RADIUS
        );
        assertEquals(
                0.1F,
                DarkSwordBloodMoonSkillHandler
                        .MINIMUM_BURST_DAMAGE_MULTIPLIER
        );
        assertEquals(
                5,
                DarkSwordBloodMoonSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertFalse(
                new DarkSwordBloodMoonSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void energyValidationHonorsCreativeAndFreeEnergyBlessing() {
        assertFalse(DarkSwordBloodMoonSkillHandler.canPayEnergy(
                9.0F,
                false,
                false
        ));
        assertTrue(DarkSwordBloodMoonSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(DarkSwordBloodMoonSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(DarkSwordBloodMoonSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }

    @Test
    void successfulCompletionCancellationAndLogoutAllCommitCooldown() {
        assertTrue(DarkSwordBloodMoonSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.COMPLETED,
                false
        ));
        assertTrue(DarkSwordBloodMoonSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.INVALIDATED,
                true
        ));
        assertTrue(DarkSwordBloodMoonSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.CASTER_UNAVAILABLE,
                false
        ));
        assertFalse(DarkSwordBloodMoonSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.INVALIDATED,
                false
        ));
    }
}
