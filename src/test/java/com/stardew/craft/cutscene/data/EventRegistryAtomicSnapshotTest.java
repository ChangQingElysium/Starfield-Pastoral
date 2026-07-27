package com.stardew.craft.cutscene.data;

import com.stardew.craft.cutscene.network.SyncEventRegistryPayload;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EventRegistryAtomicSnapshotTest {
    @Test
    void definitionsIndexesAndNetworkPayloadPublishTogether() {
        Map<String, String> previous =
                new LinkedHashMap<>(
                        EventRegistry.getRawJsonMap());
        try {
            EventRegistry.loadFromJsonStrings(Map.of(
                    "event_snapshot_test:first",
                    eventJson("event_snapshot_test:first",
                            "interact_npc", "archivist")));

            EventRegistry.Catalog accepted =
                    EventRegistry.catalog();
            assertCoherent(accepted);
            assertEquals(1,
                    EventRegistry
                            .getByNpc("archivist").size());

            EventRegistry.loadFromJsonStrings(Map.of(
                    "event_snapshot_test:invalid",
                    """
                            {
                              "id": "event_snapshot_test:invalid",
                              "trigger": {"type": "interact_npc"},
                              "commands": [{"cmd": "end"}]
                            }
                            """));

            assertSame(accepted, EventRegistry.catalog(),
                    "invalid cutscene sync replaced the accepted catalog");
            assertCoherent(accepted);

            EventRegistry.loadFromJsonStrings(Map.of(
                    "event_snapshot_test:second",
                    eventJson("event_snapshot_test:second",
                            "interact_npc", "blacksmith")));
            assertCoherent(EventRegistry.catalog());
        } finally {
            EventRegistry.loadFromJsonStrings(previous);
        }
    }

    private static String eventJson(
            String id,
            String triggerType,
            String npc
    ) {
        return """
                {
                  "id": "%s",
                  "trigger": {
                    "type": "%s",
                    "npc": "%s"
                  },
                  "commands": [{"cmd": "end"}]
                }
                """.formatted(id, triggerType, npc);
    }

    private static void assertCoherent(
            EventRegistry.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                EventRegistry.snapshot());
        assertEquals(
                catalog.state().byId().keySet(),
                catalog.state().rawJsonById().keySet());
        SyncEventRegistryPayload payload =
                EventRegistry.currentSyncPayload();
        assertEquals(catalog.definitions().version(),
                payload.version());
        assertEquals(catalog.definitions().contentHash(),
                payload.contentHash());
        assertEquals(catalog.state().rawJsonById(),
                payload.eventJsonMap());
    }
}
