package com.stardew.craft.cutscene.data;

import com.google.gson.JsonParser;
import com.stardew.craft.cutscene.command.AddQuestCommand;
import com.stardew.craft.cutscene.command.SetFlagCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDataSkipSemanticsTest {
    @Test
    void explicitSkippableCommandDelaysSkippingUntilThatCommandRuns() {
        EventData event = event(true, """
                {"cmd":"add_quest","quest_id":"26"},
                {"cmd":"skippable"},
                {"cmd":"end"}
                """);

        assertTrue(event.skippable());
        assertFalse(event.skippableAtStart());
    }

    @Test
    void metadataStillAllowsImmediateSkippingWhenThereIsNoGateCommand() {
        EventData event = event(true, """
                {"cmd":"message","text":"test"},
                {"cmd":"end"}
                """);

        assertTrue(event.skippableAtStart());
    }

    @Test
    void persistentQuestAndFlagCommandsSurviveSkipping() {
        assertTrue(new AddQuestCommand("26").isStateCommand());
        assertTrue(new SetFlagCommand("canReadJunimoText").isStateCommand());
    }

    private static EventData event(boolean skippable, String commands) {
        return EventData.fromJson(JsonParser.parseString("""
                {
                  "id":"skip_test",
                  "skippable":%s,
                  "trigger":{"type":"manual"},
                  "commands":[%s]
                }
                """.formatted(skippable, commands)).getAsJsonObject());
    }
}
