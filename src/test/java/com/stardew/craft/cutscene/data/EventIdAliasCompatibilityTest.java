package com.stardew.craft.cutscene.data;

import com.stardew.craft.cutscene.network.ClientEventSeenCache;
import com.stardew.craft.cutscene.server.EventSeenData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventIdAliasCompatibilityTest {
    @Test
    void legacyNumericIdsResolveWithoutAppearingInTheCanonicalRegistry() {
        Map<String, String> previous = new LinkedHashMap<>(EventRegistry.getRawJsonMap());
        try {
            EventRegistry.loadFromJsonStrings(Map.of("readable_event", """
                    {
                      "id": "readable_event",
                      "legacy_ids": ["9001"],
                      "trigger": {"type": "manual"},
                      "commands": [{"cmd": "end"}]
                    }
                    """));

            assertSame(EventRegistry.getById("readable_event"), EventRegistry.getById("9001"));
            assertEquals("readable_event", EventRegistry.canonicalId("9001"));
            assertEquals(Set.of("readable_event", "9001"), EventRegistry.equivalentIds("readable_event"));
            assertEquals(Set.of("readable_event"), EventRegistry.getRawJsonMap().keySet());
        } finally {
            EventRegistry.loadFromJsonStrings(previous);
        }
    }

    @Test
    void oldServerAndClientSeenDataStillBlocksTheRenamedEvent() {
        Map<String, String> previous = new LinkedHashMap<>(EventRegistry.getRawJsonMap());
        try {
            EventRegistry.loadFromJsonStrings(Map.of("readable_event", """
                    {
                      "id": "readable_event",
                      "legacy_ids": ["9001"],
                      "trigger": {"type": "manual"},
                      "commands": [{"cmd": "end"}]
                    }
                    """));

            UUID playerId = UUID.randomUUID();
            EventSeenData seenData = EventSeenData.load(oldSave(playerId, "9001"), null);
            assertTrue(seenData.hasSeen(playerId, "readable_event"));
            assertEquals(Set.of("readable_event"), seenData.getSeenEvents(playerId));

            ClientEventSeenCache.replace(Set.of("9001"));
            assertTrue(ClientEventSeenCache.hasSeen("readable_event"));

            seenData.markSeen(playerId, "9001");
            assertEquals(Set.of("readable_event"), seenData.getSeenEvents(playerId));
            assertTrue(seenData.clearSeen(playerId, "readable_event"));
            assertFalse(seenData.hasSeen(playerId, "9001"));
        } finally {
            ClientEventSeenCache.reset();
            EventRegistry.loadFromJsonStrings(previous);
        }
    }

    private static CompoundTag oldSave(UUID playerId, String legacyEventId) {
        CompoundTag root = new CompoundTag();
        root.putInt("PlayerCount", 1);
        CompoundTag player = new CompoundTag();
        player.putUUID("UUID", playerId);
        ListTag events = new ListTag();
        events.add(StringTag.valueOf(legacyEventId));
        player.put("Events", events);
        root.put("Player_0", player);
        return root;
    }
}
