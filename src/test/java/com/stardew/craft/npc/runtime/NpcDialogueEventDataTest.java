package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDialogueEventDataTest {
    @Test
    void activeTopicsRemainIsolatedPerPlayer() {
        NpcDialogueEventData data = new NpcDialogueEventData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(data.activate(first, "cc_Boulder", 7));
        assertEquals("cc_Boulder", data.activeKeys(first, "linus").getFirst());
        assertTrue(data.activeKeys(second, "linus").isEmpty());
    }

    @Test
    void activeEventAndMemoryTimingMatchesFarmerDayUpdate() {
        NpcDialogueEventData.PlayerState state = new NpcDialogueEventData.PlayerState();
        assertTrue(state.activate("eventSeen_14", 4));

        state.onNewDay();
        assertEquals(3, state.active.get("eventSeen_14"));
        assertEquals(4, state.active.get("eventSeen_14_memory_oneday"));
        assertEquals(1, state.previous.get("eventSeen_14"));

        for (int day = 2; day <= 7; day++) {
            state.onNewDay();
        }
        assertFalse(state.active.containsKey("eventSeen_14"));
        assertEquals(4, state.active.get("eventSeen_14_memory_oneweek"));
        assertEquals(7, state.previous.get("eventSeen_14"));
    }

    @Test
    void eachNpcConsumesAnActiveTopicIndependently() {
        NpcDialogueEventData.PlayerState state = new NpcDialogueEventData.PlayerState();
        state.activate("eventSeen_14", 4);
        state.consumed.add("haley\u0000eventSeen_14");

        assertTrue(state.activeKeys("haley").isEmpty());
        assertEquals("eventSeen_14", state.activeKeys("emily").getFirst());
        assertFalse(state.activate("eventSeen_14", 4));

        state.answeredDialogueIds.add("27");
        assertTrue(state.answeredDialogueIds.contains("27"));

        NpcDialogueEventData.PlayerState restored =
                NpcDialogueEventData.PlayerState.load(state.save());
        assertTrue(restored.answeredDialogueIds.contains("27"));
        assertTrue(restored.consumed.contains("haley\u0000eventSeen_14"));
    }
}
