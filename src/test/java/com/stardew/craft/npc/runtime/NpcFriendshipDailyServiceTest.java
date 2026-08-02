package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcFriendshipDailyServiceTest {
    @Test
    void nonDatableFriendshipDecaysOnlyBelowTenHeartsWhenNotTalkedTo() {
        assertEquals(-2, NpcFriendshipDailyService.calculateDailyFriendshipDelta(2_499, false, false));
        assertEquals(0, NpcFriendshipDailyService.calculateDailyFriendshipDelta(2_500, false, false));
        assertEquals(0, NpcFriendshipDailyService.calculateDailyFriendshipDelta(500, true, false));
    }

    @Test
    void singleDatableFriendshipStopsDecayingAtEightHearts() {
        assertEquals(-2, NpcFriendshipDailyService.calculateDailyFriendshipDelta(1_999, false, true));
        assertEquals(0, NpcFriendshipDailyService.calculateDailyFriendshipDelta(2_000, false, true));
        assertEquals(0, NpcFriendshipDailyService.calculateDailyFriendshipDelta(500, true, true));
    }

    @Test
    void settlementClampsAtZeroAndRunsAgainstThePreviousDayTalkFlag() {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        UUID player = UUID.randomUUID();
        NpcFriendshipDataManager.FriendshipState state = manager.getOrCreate(player, "linus");
        state.addPoints(1, 2_749);
        state.setLastTalkDayKey(40);

        assertTrue(manager.settleNewDay(41, 6, id -> true, id -> false, id -> 2_749));
        assertEquals(0, state.points());

        state.addPoints(100, 2_749);
        state.setLastTalkDayKey(42);
        assertFalse(manager.settleNewDay(42, 6, id -> true, id -> false, id -> 2_749));
        assertEquals(100, state.points());
    }

    @Test
    void sundayResetAwardsTenPointsAfterBothWeeklyGifts() {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        UUID player = UUID.randomUUID();
        NpcFriendshipDataManager.FriendshipState state = manager.getOrCreate(player, "linus");
        state.addPoints(100, 2_749);
        state.applyGiftCounters(5, 0);
        state.applyGiftCounters(6, 0);

        assertTrue(manager.settleNewDay(6, 1, id -> true, id -> false, id -> 2_749));
        // Vanilla applies the missed-conversation decay first, then the weekly gift bonus.
        assertEquals(108, state.points());
        assertEquals(0, state.giftsThisWeek());
        assertEquals(1, state.lastGiftWeekKey());
    }

    @Test
    void unknownAddonStateKeepsPointsButStillResetsItsWeekCounter() {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        UUID player = UUID.randomUUID();
        NpcFriendshipDataManager.FriendshipState state =
                manager.getOrCreate(player, "missing_addon:npc");
        state.addPoints(100, 2_749);
        state.applyGiftCounters(5, 0);

        assertTrue(manager.settleNewDay(6, 1, id -> false, id -> false, id -> 2_749));
        assertEquals(100, state.points());
        assertEquals(0, state.giftsThisWeek());
    }
}
