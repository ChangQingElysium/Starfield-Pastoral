package com.stardew.craft.cutscene.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardDarkTalismanOpeningResourceTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");
    private static final List<String> DIALOGUE_KEYS = List.of(
            "awaiting", "married", "strange", "mistake", "curse",
            "seal", "ink", "talisman", "reward");

    @Test
    void usesTheConfirmedBoundsAndChoreography() throws Exception {
        EventData event = event();

        assertEquals("railroad", event.trigger().location());
        assertArrayEquals(new double[] {-37, 85, -211}, event.trigger().areaMin());
        assertArrayEquals(new double[] {63, 95, -170}, event.trigger().areaMax());
        assertTrue(event.preconditions().stream().anyMatch(condition ->
                "flag".equals(condition.type()) && "ccIsComplete".equals(condition.getString("id"))));

        assertCommand(event, "camera", 57.135, 88.883, -200.933);
        assertActorPoint(event, "wizard", 52, 85, -211);
        assertActorPoint(event, "fake_player", 46, 85, -207);
        assertMovePoint(event, "fake_player", 48, 85, -207);
        assertMovePoint(event, "fake_player", 48, 85, -211);
        assertMovePoint(event, "fake_player", 50, 85, -211);
        assertMovePoint(event, "wizard", 51, 85, -211);
        assertCommand(event, "place_player", 50, 85, -211);

        int addQuest = commandIndex(event, "add_quest");
        int skippable = commandIndex(event, "skippable");
        assertTrue(addQuest >= 0 && addQuest < skippable);
        assertEquals("28", event.rawCommands().get(addQuest).get("quest_id").getAsString());

        String source = Files.readString(eventPath());
        assertFalse(source.toLowerCase().contains("witch"));
        assertFalse(source.contains("cackling_witch"));
    }

    @Test
    void everyBundledLanguageContainsTheNineWizardLines() throws Exception {
        for (String language : LANGUAGES) {
            Path path = projectRoot().resolve("src/main/resources/assets/stardewcraft/lang/" + language + ".json");
            JsonObject translations = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (String suffix : DIALOGUE_KEYS) {
                String key = "event.wizard_dark_talisman." + suffix;
                assertTrue(translations.has(key), () -> path + " is missing " + key);
                assertFalse(translations.get(key).getAsString().isBlank(), () -> path + " has blank " + key);
            }
            for (String suffix : List.of("title", "description", "objective")) {
                String key = "stardewcraft.quest.28." + suffix;
                assertTrue(translations.has(key), () -> path + " is missing " + key);
                assertFalse(translations.get(key).getAsString().isBlank(), () -> path + " has blank " + key);
            }
        }

        JsonObject chinese = JsonParser.parseString(Files.readString(projectRoot().resolve(
                "src/main/resources/assets/stardewcraft/lang/zh_cn.json"))).getAsJsonObject();
        assertTrue(chinese.get("event.wizard_dark_talisman.ink").getAsString().contains("魔法墨水"));
        assertFalse(chinese.get("event.wizard_dark_talisman.ink").getAsString().contains("魔法药水"));
    }

    @Test
    void darkTalismanQuestReferencedByTheEventExists() throws Exception {
        JsonObject quests = JsonParser.parseString(Files.readString(projectRoot().resolve(
                "src/main/resources/data/stardewcraft/quests.json"))).getAsJsonObject();
        assertTrue(quests.has("28"));
        assertTrue(quests.get("28").getAsString().startsWith(
                "Basic/stardewcraft.quest.28.title/stardewcraft.quest.28.description/"));
    }

    @Test
    void usesTheVanillaWizardSongAsset() throws Exception {
        JsonObject vanillaRailroadEvents = JsonParser.parseString(Files.readString(projectRoot().resolve(
                "源文件/Content/Data/Events/Railroad.json"))).getAsJsonObject();
        assertTrue(vanillaRailroadEvents.get("529952/C").getAsString().startsWith("WizardSong/"));

        EventData event = event();
        assertTrue(event.rawCommands().stream().anyMatch(command ->
                "music".equals(command.get("cmd").getAsString())
                        && "music_wizard_tower".equals(command.get("track").getAsString())));

        JsonObject sounds = JsonParser.parseString(Files.readString(projectRoot().resolve(
                "src/main/resources/assets/stardewcraft/sounds.json"))).getAsJsonObject();
        String soundAsset = sounds.getAsJsonObject("music_wizard_tower")
                .getAsJsonArray("sounds")
                .get(0).getAsJsonObject()
                .get("name").getAsString();
        assertEquals("stardewcraft:music/wizard_song", soundAsset);

        Path audio = projectRoot().resolve(
                "src/main/resources/assets/stardewcraft/sounds/music/wizard_song.ogg");
        assertTrue(Files.isRegularFile(audio));
        assertTrue(Files.size(audio) > 0);
    }

    private static void assertActorPoint(EventData event, String actor, double x, double y, double z) {
        assertTrue(event.rawCommands().stream().anyMatch(command ->
                "spawn_actor".equals(command.get("cmd").getAsString())
                        && actor.equals(command.get("actor").getAsString())
                        && pointEquals(command, x, y, z)));
    }

    private static void assertMovePoint(EventData event, String actor, double x, double y, double z) {
        assertTrue(event.rawCommands().stream().anyMatch(command ->
                "move_actor".equals(command.get("cmd").getAsString())
                        && actor.equals(command.get("actor").getAsString())
                        && pointEquals(command, x, y, z)));
    }

    private static void assertCommand(EventData event, String type, double x, double y, double z) {
        assertTrue(event.rawCommands().stream().anyMatch(command ->
                type.equals(command.get("cmd").getAsString()) && pointEquals(command, x, y, z)));
    }

    private static boolean pointEquals(JsonObject command, double x, double y, double z) {
        return command.get("x").getAsDouble() == x
                && command.get("y").getAsDouble() == y
                && command.get("z").getAsDouble() == z;
    }

    private static int commandIndex(EventData event, String type) {
        for (int index = 0; index < event.rawCommands().size(); index++) {
            if (type.equals(event.rawCommands().get(index).get("cmd").getAsString())) {
                return index;
            }
        }
        return -1;
    }

    private static EventData event() throws Exception {
        return EventData.fromJson(JsonParser.parseString(Files.readString(eventPath())).getAsJsonObject());
    }

    private static Path eventPath() {
        return projectRoot().resolve(
                "src/main/resources/data/stardewcraft/cutscene_events/wizard_dark_talisman_opening.json");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("stardewcraft.projectDir"));
    }
}
