package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalDaggerLayerTrackerTest {
    @Test
    void layersCapAtFourRefreshDurationAndDelayBurstByOneTick() {
        CrystalDaggerLayerTracker.State state = null;

        state = CrystalDaggerLayerTracker.advance(state, 100L);
        assertEquals(1, state.stacks());
        assertEquals(220L, state.endTick());
        assertEquals(0L, state.readyTick());

        state = CrystalDaggerLayerTracker.advance(state, 110L);
        state = CrystalDaggerLayerTracker.advance(state, 120L);
        state = CrystalDaggerLayerTracker.advance(state, 130L);
        assertEquals(4, state.stacks());
        assertEquals(250L, state.endTick());
        assertEquals(131L, state.readyTick());
        assertFalse(CrystalDaggerLayerTracker.shouldBurst(state, 130L));
        assertTrue(CrystalDaggerLayerTracker.shouldBurst(state, 131L));

        state = CrystalDaggerLayerTracker.advance(state, 140L);
        assertEquals(4, state.stacks());
        assertEquals(260L, state.endTick());
        assertEquals(131L, state.readyTick());
    }

    @Test
    void anExpiredLayerChainRestartsAtOne() {
        CrystalDaggerLayerTracker.State expired =
                new CrystalDaggerLayerTracker.State(4, 200L, 150L);

        CrystalDaggerLayerTracker.State restarted =
                CrystalDaggerLayerTracker.advance(expired, 201L);

        assertEquals(1, restarted.stacks());
        assertEquals(321L, restarted.endTick());
        assertEquals(0L, restarted.readyTick());
        assertFalse(CrystalDaggerLayerTracker.shouldBurst(expired, 201L));
    }
}
