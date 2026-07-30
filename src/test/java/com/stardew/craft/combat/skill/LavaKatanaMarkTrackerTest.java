package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaKatanaMarkTrackerTest {
    @Test
    void markAndBurnUseTheirAuthoredExclusiveTimeline() {
        assertTrue(LavaKatanaMarkTracker.isWithinMarkWindow(119L, 120L));
        assertFalse(LavaKatanaMarkTracker.isWithinMarkWindow(120L, 120L));
        assertEquals(
                11,
                LavaKatanaMarkTracker.scheduledBurnTicks(
                        LavaKatanaMarkTracker.MARK_DURATION_TICKS
                )
        );
        assertEquals(10L, LavaKatanaMarkTracker.BURN_INTERVAL_TICKS);
    }

    @Test
    void normalHeatCapsAtFiveButReverbHeatDoesNot() {
        SkillContext base = LavaKatanaMarkTracker.createBurnContext(0, false);
        SkillContext capped =
                LavaKatanaMarkTracker.createBurnContext(99, false);
        SkillContext reverb =
                LavaKatanaMarkTracker.createBurnContext(6, true);

        assertEquals("lava_katana_burn", base.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, base.getTier());
        assertEquals(0.15F, base.getDamageMultiplier());
        assertEquals(0.35F, capped.getDamageMultiplier());
        assertEquals(0.63F, reverb.getDamageMultiplier());
        assertEquals(5, LavaKatanaMarkTracker.HEAT_CAP);
        assertEquals(0.04F, LavaKatanaMarkTracker.HEAT_BONUS_RATIO);
        assertEquals(
                0.08F,
                LavaKatanaMarkTracker.HEAT_BONUS_REVERB_RATIO
        );
        assertEquals(
                5,
                LavaKatanaMarkTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    @Test
    void ownershipRequiresPlayerSessionAndSharedDimension() {
        UUID owner = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );
        UUID other = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );
        UUID session = UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
        );

        assertTrue(LavaKatanaMarkTracker.matchesOwner(owner, owner));
        assertFalse(LavaKatanaMarkTracker.matchesOwner(owner, other));
        assertTrue(LavaKatanaMarkTracker.matchesSession(session, session));
        assertFalse(LavaKatanaMarkTracker.matchesSession(session, null));
        assertTrue(LavaKatanaMarkTracker.matchesDimension(
                Level.OVERWORLD.location().toString(),
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(LavaKatanaMarkTracker.matchesDimension(
                Level.OVERWORLD.location().toString(),
                Level.NETHER,
                Level.NETHER
        ));
        assertFalse(LavaKatanaMarkTracker.matchesDimension(
                Level.OVERWORLD.location().toString(),
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}
