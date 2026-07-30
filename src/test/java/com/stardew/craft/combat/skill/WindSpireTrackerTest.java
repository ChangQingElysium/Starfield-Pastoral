package com.stardew.craft.combat.skill;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindSpireTrackerTest {
    @Test
    void galeBonusRemainsTenPercentThroughItsAuthoredWindow() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000031");

        WindSpireTracker.start(playerId, 100L, 60);

        assertEquals(0.10F, WindSpireTracker.getCritChanceBonus(playerId, 100L));
        assertEquals(0.10F, WindSpireTracker.getCritChanceBonus(playerId, 160L));
        assertFalse(WindSpireTracker.expireIfPast(playerId, 160L));
        assertTrue(WindSpireTracker.expireIfPast(playerId, 161L));
        assertEquals(0.0F, WindSpireTracker.getCritChanceBonus(playerId, 161L));
    }

    @Test
    void cleanupRemovesGaleState() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000032");

        WindSpireTracker.start(playerId, 200L, 60);
        WindSpireTracker.removePlayer(playerId);

        assertEquals(0.0F, WindSpireTracker.getCritChanceBonus(playerId, 200L));
    }

    @Test
    void galeBonusAppliesToNormalAttacksButNeverSkillAttacks() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000033");
        SkillContext normalAttack = SkillContext.normalAttack();
        SkillContext skillAttack = SkillContext.builder()
                .skillId("wind_spire_thrust")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(1.5F)
                .build();

        WindSpireTracker.start(playerId, 300L, 60);

        assertEquals(
                0.10F,
                WindSpireTracker.getCritChanceBonus(playerId, normalAttack, 300L)
        );
        assertEquals(
                0.0F,
                WindSpireTracker.getCritChanceBonus(playerId, skillAttack, 300L)
        );
        WindSpireTracker.removePlayer(playerId);
    }
}
