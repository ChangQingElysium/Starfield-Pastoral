package com.stardew.craft.cutscene.server;

import com.google.gson.JsonParser;
import com.stardew.craft.cutscene.data.EventData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutsceneActionManifestTest {
    @Test
    void validatesRawCommandTokenPayloadAndReplay() {
        CutsceneActionManifest manifest = CutsceneActionManifest.from(event());
        var state = manifest.newState();

        assertFalse(manifest.authorize(state, 0, "add_item", "stardewcraft:rusty_sword:1"));
        assertFalse(manifest.authorize(state, 1, "add_item", "stardewcraft:rusty_sword:2"));
        assertTrue(manifest.authorize(state, 1, "add_item", "stardewcraft:rusty_sword:1"));
        assertFalse(manifest.authorize(state, 1, "add_item", "stardewcraft:rusty_sword:1"));
    }

    @Test
    void questionTokenAllowsOnlyTheSelectedBranch() {
        CutsceneActionManifest manifest = CutsceneActionManifest.from(event());
        var state = manifest.newState();

        assertTrue(manifest.authorize(state, 2, "set_cave_choice", "fruit_bats"));
        assertFalse(manifest.authorize(state, 2, "set_cave_choice", "mushrooms"));
    }

    @Test
    void placePlayerRequiresTheExactAuthoredEndpoint() {
        EventData event = EventData.fromJson(JsonParser.parseString("""
                {
                  "id": "place_player_test",
                  "trigger": {"type": "manual"},
                  "commands": [
                    {"cmd":"place_player","x":50,"y":85,"z":-211,"yaw":-90,"pitch":0}
                  ]
                }
                """).getAsJsonObject());
        CutsceneActionManifest manifest = CutsceneActionManifest.from(event);
        var state = manifest.newState();

        assertFalse(manifest.authorize(state, 0, "place_player", "50.0,85.0,-210.0,-90.0,0.0"));
        assertTrue(manifest.authorize(state, 0, "place_player", "50.0,85.0,-211.0,-90.0,0.0"));
    }

    private static EventData event() {
        return EventData.fromJson(JsonParser.parseString("""
                {
                  "id": "authorization_test",
                  "trigger": {"type": "manual"},
                  "commands": [
                    {"cmd": "comment", "text": "token zero is intentionally empty"},
                    {"cmd": "add_item", "item": "stardewcraft:rusty_sword", "count": 1},
                    {"cmd": "question", "text": "test", "choices": [
                      {"text": "bats", "commands": [
                        {"cmd": "set_cave_choice", "choice": "fruit_bats"}
                      ]},
                      {"text": "mushrooms", "commands": [
                        {"cmd": "set_cave_choice", "choice": "mushrooms"}
                      ]}
                    ]}
                  ]
                }
                """).getAsJsonObject());
    }
}
