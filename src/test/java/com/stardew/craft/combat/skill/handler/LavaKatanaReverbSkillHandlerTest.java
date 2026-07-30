package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.LavaKatanaReverbTracker;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaKatanaReverbSkillHandlerTest {
    @Test
    void authoredActivationContractRemainsStable() {
        WeaponData lavaKatana = WeaponRegistry.get("lava_katana");
        assertNotNull(lavaKatana);
        WeaponSkillData skill = lavaKatana.getSkill2();
        assertNotNull(skill);

        assertEquals("lava_katana_reverb", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(12.0F, LavaKatanaReverbSkillHandler.ENERGY_COST);
        assertEquals(8.0D, LavaKatanaReverbTracker.TARGET_RANGE);
        assertEquals(5, LavaKatanaReverbTracker.MINIMUM_HEAT);
        assertEquals(
            80,
            LavaKatanaReverbTracker.ACTIVE_DURATION_TICKS
        );
        assertEquals(120, LavaKatanaMarkTracker.MARK_DURATION_TICKS);
        assertFalse(
            new LavaKatanaReverbSkillHandler().completesImmediately()
        );
    }

    @Test
    void targetAndEnergyPreflightAreSideEffectFreeContracts() {
        assertFalse(
            LavaKatanaReverbSkillHandler.hasCastTarget(0, false)
        );
        assertTrue(
            LavaKatanaReverbSkillHandler.hasCastTarget(1, false)
        );
        assertTrue(
            LavaKatanaReverbSkillHandler.hasCastTarget(0, true)
        );

        assertFalse(LavaKatanaReverbSkillHandler.canPayEnergy(
            11.99F,
            false,
            false
        ));
        assertTrue(LavaKatanaReverbSkillHandler.canPayEnergy(
            12.0F,
            false,
            false
        ));
        assertTrue(LavaKatanaReverbSkillHandler.canPayEnergy(
            0.0F,
            true,
            false
        ));
        assertTrue(LavaKatanaReverbSkillHandler.canPayEnergy(
            0.0F,
            false,
            true
        ));
    }

    @Test
    void logoutCleanupDoesNotNotifyAnUnavailableCaster() {
        assertFalse(
            LavaKatanaReverbSkillHandler.shouldNotifyOnFinish(
                SkillInstance.EndReason.CASTER_UNAVAILABLE
            )
        );
        assertTrue(
            LavaKatanaReverbSkillHandler.shouldNotifyOnFinish(
                SkillInstance.EndReason.COMPLETED
            )
        );
        assertTrue(
            LavaKatanaReverbSkillHandler.shouldNotifyOnFinish(
                SkillInstance.EndReason.INVALIDATED
            )
        );
    }
}
