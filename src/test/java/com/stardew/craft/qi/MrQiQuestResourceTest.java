package com.stardew.craft.qi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MrQiQuestResourceTest {
    private static final List<String> LOCALES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn"
    );

    private static final List<String> REQUIRED_KEYS = List.of(
            "block.stardewcraft.qi_tunnel_safe",
            "stardewcraft.quest.2.title",
            "stardewcraft.quest.2.description",
            "stardewcraft.quest.2.objective",
            "stardewcraft.quest.3.title",
            "stardewcraft.quest.3.description",
            "stardewcraft.quest.3.objective",
            "stardewcraft.quest.4.title",
            "stardewcraft.quest.4.description",
            "stardewcraft.quest.4.objective",
            "stardewcraft.quest.5.title",
            "stardewcraft.quest.5.description",
            "stardewcraft.quest.5.objective",
            "stardewcraft.qi.tunnel.initial",
            "stardewcraft.qi.tunnel.consume_battery",
            "stardewcraft.qi.tunnel.mr_qi_note",
            "stardewcraft.qi.railroad.initial",
            "stardewcraft.qi.railroad.consume_shell",
            "stardewcraft.qi.railroad.mr_qi_note",
            "stardewcraft.qi.mayor_fridge.initial",
            "stardewcraft.qi.mayor_fridge.consume_beets",
            "stardewcraft.qi.mayor_fridge.mr_qi_note",
            "stardewcraft.qi.sand_dragon.initial",
            "stardewcraft.qi.sand_dragon.consume_essence",
            "stardewcraft.qi.sand_dragon.mr_qi_note",
            "stardewcraft.mail.mrQiClubCard"
    );

    @Test
    void legacyQuestDefinitionsContainTheOriginalUncancellableChain() throws IOException {
        JsonObject quests = readJson(resource("data/stardewcraft/quests.json"));
        for (String questId : List.of("2", "3", "4", "5")) {
            assertTrue(quests.has(questId), "Missing quest " + questId);
            String[] fields = quests.get(questId).getAsString().split("/", -1);
            assertEquals("Basic", fields[0]);
            assertEquals("stardewcraft.quest." + questId + ".title", fields[1]);
            assertEquals("stardewcraft.quest." + questId + ".description", fields[2]);
            assertEquals("stardewcraft.quest." + questId + ".objective", fields[3]);
            assertEquals("false", fields[8]);
        }
    }

    @Test
    void everyShippedLocaleContainsQuestAndInteractionText() throws IOException {
        for (String locale : LOCALES) {
            JsonObject language = readJson(resource("assets/stardewcraft/lang/" + locale + ".json"));
            for (String key : REQUIRED_KEYS) {
                assertTrue(language.has(key), locale + " is missing " + key);
                assertTrue(!language.get(key).getAsString().isBlank(), locale + " has blank " + key);
            }
        }
    }

    @Test
    void tunnelSafeUsesItsAuthoredModelParticleAndHasNoItemForm() throws IOException {
        JsonObject model = readJson(resource("assets/stardewcraft/models/block/qi/qi_tunnel_safe.json"));
        assertEquals(
                "stardewcraft:block/qi/qi_tunnel_safe",
                model.getAsJsonObject("textures").get("particle").getAsString()
        );
        assertTrue(Files.isRegularFile(resource(
                "assets/stardewcraft/textures/block/qi/qi_tunnel_safe.png")));
        assertFalse(Files.exists(resource(
                "assets/stardewcraft/models/item/qi_tunnel_safe.json")));

        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        String modItems = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/item/ModItems.java"));
        assertFalse(modItems.contains("ITEMS.register(\"qi_tunnel_safe\""));
    }

    @Test
    void clubCardMailCarriesTheRewardAndCompletesTheAdaptedFinalStep() throws IOException {
        var root = JsonParser.parseString(Files.readString(
                resource("data/stardewcraft/mail/qi_mail.json"))).getAsJsonArray();
        assertEquals(1, root.size());

        JsonObject mail = root.get(0).getAsJsonObject();
        assertEquals(MrQiQuestInteractionService.CLUB_CARD_MAIL_ID, mail.get("id").getAsString());
        JsonObject attachment = mail.getAsJsonArray("attachedItems").get(0).getAsJsonObject();
        assertEquals("stardewcraft:club_card", attachment.get("id").getAsString());
        assertEquals(1, attachment.get("count").getAsInt());

        String actions = mail.getAsJsonArray("on_read").toString();
        assertTrue(actions.contains("\"id\":\"TH_LumberPile\""));
        assertTrue(actions.contains("\"id\":\"HasClubCard\""));
        assertTrue(actions.contains("\"type\":\"stardewcraft:remove_quest\""));
        assertTrue(actions.contains("\"quest\":\"5\""));
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Path resource(String relative) {
        return Path.of(System.getProperty("stardewcraft.projectDir", "."))
                .resolve("src/main/resources")
                .resolve(relative);
    }
}
