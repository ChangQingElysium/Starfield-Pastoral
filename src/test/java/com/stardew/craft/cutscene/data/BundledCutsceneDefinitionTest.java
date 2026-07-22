package com.stardew.craft.cutscene.data;

import com.google.gson.JsonParser;
import com.stardew.craft.cutscene.command.EventCommandFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledCutsceneDefinitionTest {
    @Test
    void everyBundledEnterAreaEventUsesAKnownLocationAndValidBounds() throws Exception {
        for (Path path : bundledEvents()) {
            EventData event = read(path);
            if (!"enter_area".equals(event.trigger().type())) {
                continue;
            }
            assertNotNull(CutsceneTriggerLocations.dimensionFor(event.trigger().location()),
                    () -> path + " uses unknown location " + event.trigger().location());
            if (event.trigger().areaMin() == null && event.trigger().areaMax() == null
                    && CutsceneTriggerLocations.isWholeDimension(event.trigger().location())) {
                continue;
            }
            assertNotNull(event.trigger().areaMin(), () -> path + " is missing area_min");
            assertNotNull(event.trigger().areaMax(), () -> path + " is missing area_max");
            assertEquals(3, event.trigger().areaMin().length, () -> path + " has invalid area_min");
            assertEquals(3, event.trigger().areaMax().length, () -> path + " has invalid area_max");
        }
    }

    @Test
    void bundledSkippableGatesAreNotEnabledAtEventStart() throws Exception {
        for (Path path : bundledEvents()) {
            EventData event = read(path);
            boolean hasGate = event.rawCommands().stream().anyMatch(command ->
                    command.has("cmd") && "skippable".equals(command.get("cmd").getAsString()));
            if (hasGate) {
                assertFalse(event.skippableAtStart(),
                        () -> path + " bypasses its explicit skippable command");
            }
        }
    }

    @Test
    void wizardCutscenesEnableSkippingBeforeTheirFirstDialogue() throws Exception {
        for (String fileName : java.util.List.of(
                "wizard_intro.json", "wizard_e112.json", "wizard_dark_talisman_opening.json")) {
            Path path = eventsRoot().resolve(fileName);
            EventData event = read(path);
            int skippableIndex = commandIndex(event, "skippable");
            int firstDialogueIndex = commandIndex(event, "speak");

            assertTrue(skippableIndex >= 0, () -> path + " is missing its skippable gate");
            assertTrue(firstDialogueIndex >= 0, () -> path + " has no dialogue command");
            assertTrue(skippableIndex < firstDialogueIndex,
                    () -> path + " does not enable skipping before its first dialogue");
        }
    }

    @Test
    void everyBundledCommandIsRecognizedByTheRuntimeFactory() throws Exception {
        for (Path path : bundledEvents()) {
            EventData event = read(path);
            for (var command : event.rawCommands()) {
                String type = command.get("cmd").getAsString();
                if ("comment".equals(type)) {
                    continue;
                }
                assertNotNull(EventCommandFactory.create(command),
                        () -> path + " uses unknown command " + type);
            }
        }
    }

    @Test
    void lewisTourStartsOnTheFifthPlayedDay() throws Exception {
        Path path = eventsRoot().resolve("lewis_cc_tour.json");
        EventData event = read(path);
        EventPrecondition daysPlayed = event.preconditions().stream()
                .filter(condition -> "days_played".equals(condition.type()))
                .findFirst()
                .orElseThrow();

        assertEquals(5, daysPlayed.getInt("min", -1));
    }

    private static java.util.List<Path> bundledEvents() throws Exception {
        try (var paths = Files.list(eventsRoot())) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static EventData read(Path path) throws Exception {
        return EventData.fromJson(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
    }

    private static int commandIndex(EventData event, String commandType) {
        for (int index = 0; index < event.rawCommands().size(); index++) {
            if (commandType.equals(event.rawCommands().get(index).get("cmd").getAsString())) {
                return index;
            }
        }
        return -1;
    }

    private static Path eventsRoot() {
        return Path.of(System.getProperty("stardewcraft.projectDir"))
                .resolve("src/main/resources/data/stardewcraft/cutscene_events");
    }
}
