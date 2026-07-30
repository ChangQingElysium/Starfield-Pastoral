package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSwordBloodMoonTrackerTest {
    @Test
    void activeWindowIsInclusiveButCannotCrossDimensionsOrOutliveCaster() {
        assertTrue(DarkSwordBloodMoonTracker.shouldRemainActive(
                180L,
                180L,
                true,
                true
        ));
        assertFalse(DarkSwordBloodMoonTracker.shouldRemainActive(
                180L,
                181L,
                true,
                true
        ));
        assertFalse(DarkSwordBloodMoonTracker.shouldRemainActive(
                180L,
                160L,
                true,
                false
        ));
        assertFalse(DarkSwordBloodMoonTracker.shouldRemainActive(
                180L,
                160L,
                false,
                true
        ));
        assertTrue(DarkSwordBloodMoonTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(DarkSwordBloodMoonTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void burnAndLifestealUseOneAuthoritativeHealthUnitInEveryDimension() {
        assertEquals(1.0F, DarkSwordBloodMoonTracker.burnAmount(20.0F));
        assertEquals(1.0F, DarkSwordBloodMoonTracker.burnAmount(100.0F));
        assertEquals(2.5F, DarkSwordBloodMoonTracker.burnAmount(250.0F));
        assertEquals(8.0F, DarkSwordBloodMoonTracker.netBurn(12.0F, 4.0F));
        assertEquals(0.0F, DarkSwordBloodMoonTracker.netBurn(4.0F, 12.0F));
        assertEquals(0.30F, DarkSwordBloodMoonTracker.LIFESTEAL_RATIO);
        assertEquals(
                1.35F,
                DarkSwordBloodMoonTracker.DAMAGE_BONUS_MULTIPLIER
        );
        assertEquals(
                1.0F,
                DarkSwordBloodMoonTracker.MINIMUM_REMAINING_HEALTH
        );
    }

    @Test
    void finisherPreservesNetBurnConversionAndMajorDamageContext() {
        assertEquals(
                0.2F,
                DarkSwordBloodMoonTracker.burstDamageMultiplier(
                        8.0F,
                        40.0F
                )
        );
        assertEquals(
                0.1F,
                DarkSwordBloodMoonTracker.burstDamageMultiplier(
                        1.0F,
                        40.0F
                )
        );
        assertEquals(
                0.0F,
                DarkSwordBloodMoonTracker.burstDamageMultiplier(
                        8.0F,
                        0.0F
                )
        );

        SkillContext context =
                DarkSwordBloodMoonTracker.createBurstContext(0.2F);
        assertEquals(
                "dark_sword_blood_moon_burst",
                context.getSkillId()
        );
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(0.2F, context.getDamageMultiplier());
        assertEquals(3.5F, DarkSwordBloodMoonTracker.BURST_RADIUS);
        assertEquals(
                5,
                DarkSwordBloodMoonTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }
}
