package com.stardew.craft.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryQuest100To125ResourceTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void lostAxeUsesAStandaloneVanillaSizedItemAsset() throws Exception {
        try (InputStream stream = resource(
                "assets/stardewcraft/textures/item/lost_axe.png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }

        JsonObject model = json("assets/stardewcraft/models/item/lost_axe.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("stardewcraft:item/lost_axe",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void blackberryBasketUsesAStandaloneVanillaSizedItemAsset() throws Exception {
        assertEquals(new BlockPos(-96, 64, -71), BlackberryBasketQuestService.BASKET_POS);

        try (InputStream stream = resource(
                "assets/stardewcraft/textures/item/blackberry_basket.png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }

        JsonObject model = json("assets/stardewcraft/models/item/blackberry_basket.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("stardewcraft:item/blackberry_basket",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void sourceDateLettersGrantTheImplementedStoryQuests() throws Exception {
        JsonArray spring = jsonArray("data/stardewcraft/mail/starter_mail.json");
        JsonArray summer = jsonArray("data/stardewcraft/mail/summer_mail.json");
        JsonArray fall = jsonArray("data/stardewcraft/mail/fall_mail.json");

        assertMailQuest(spring, "spring_11_1", "100");
        assertMailQuest(spring, "spring_19_1", "101");
        assertMailQuest(summer, "summer_14_1", "103");
        assertMailQuest(summer, "summer_20_1", "104");
        assertMailQuest(summer, "summer_25_1", "105");
        assertMailQuest(fall, "fall_3_1", "106");
        assertMailQuest(fall, "fall_8_1", "107");
        assertMailQuest(fall, "fall_19_1", "108");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_2_1", "109");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_6_1", "110");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_12_1", "111");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_17_1", "112");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_21_1", "113");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_26_1", "114");
        assertMailQuest(spring, "spring_6_2", "115");
        assertMailQuest(spring, "spring_15_2", "116");
        assertMailQuest(spring, "spring_21_2", "117");
        assertMailQuest(jsonArray("data/stardewcraft/mail/summer_mail.json"),
                "summer_6_2", "118");
        assertMailQuest(jsonArray("data/stardewcraft/mail/summer_mail.json"),
                "summer_15_2", "119");
        assertMailQuest(jsonArray("data/stardewcraft/mail/summer_mail.json"),
                "summer_21_2", "120");
        assertMailQuest(fall, "fall_6_2", "121");
        assertMailQuest(fall, "fall_19_2", "122");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_5_2", "123");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_13_2", "124");
        assertMailQuest(jsonArray("data/stardewcraft/mail/winter_mail.json"),
                "winter_19_2", "125");
    }

    @Test
    void marriageQuests126And127RemainUnregistered() throws Exception {
        JsonObject quests = json("data/stardewcraft/quests.json");
        assertTrue(!quests.has("126"));
        assertTrue(!quests.has("127"));
    }

    @Test
    void allSupportedLanguagesContainTheImportedVanillaText() throws Exception {
        for (String language : LANGUAGES) {
            JsonObject lang = json("assets/stardewcraft/lang/" + language + ".json");
            assertTrue(lang.has("item.stardewcraft.lost_axe"), language);
            assertTrue(lang.has("item.stardewcraft.lost_axe.desc"), language);
            assertTrue(lang.has("stardewcraft.quest.100.title"), language);
            assertTrue(lang.has("stardewcraft.quest.101.title"), language);
            assertTrue(lang.has("stardewcraft.quest.103.title"), language);
            assertTrue(lang.has("stardewcraft.quest.104.title"), language);
            assertTrue(lang.has("stardewcraft.quest.105.title"), language);
            assertTrue(lang.has("stardewcraft.quest.106.title"), language);
            assertTrue(lang.has("stardewcraft.quest.107.title"), language);
            assertTrue(lang.has("stardewcraft.quest.108.title"), language);
            assertTrue(lang.has("stardewcraft.quest.109.title"), language);
            assertTrue(lang.has("stardewcraft.quest.110.title"), language);
            assertTrue(lang.has("stardewcraft.quest.111.title"), language);
            assertTrue(lang.has("stardewcraft.quest.112.title"), language);
            assertTrue(lang.has("stardewcraft.quest.113.title"), language);
            assertTrue(lang.has("stardewcraft.quest.114.title"), language);
            assertTrue(lang.has("stardewcraft.quest.115.title"), language);
            assertTrue(lang.has("stardewcraft.quest.116.title"), language);
            assertTrue(lang.has("stardewcraft.quest.117.title"), language);
            assertTrue(lang.has("stardewcraft.quest.118.title"), language);
            assertTrue(lang.has("stardewcraft.quest.119.title"), language);
            assertTrue(lang.has("stardewcraft.quest.120.title"), language);
            assertTrue(lang.has("stardewcraft.quest.121.title"), language);
            assertTrue(lang.has("stardewcraft.quest.122.title"), language);
            assertTrue(lang.has("stardewcraft.quest.123.title"), language);
            assertTrue(lang.has("stardewcraft.quest.124.title"), language);
            assertTrue(lang.has("stardewcraft.quest.125.title"), language);
            assertTrue(lang.has("item.stardewcraft.blackberry_basket"), language);
            assertTrue(lang.has("item.stardewcraft.blackberry_basket.desc"), language);
            assertTrue(lang.has("stardewcraft.mail.spring_11_1"), language);
            assertTrue(lang.has("stardewcraft.mail.spring_19_1"), language);
            assertTrue(lang.has("stardewcraft.mail.summer_14_1"), language);
            assertTrue(lang.has("stardewcraft.mail.summer_20_1"), language);
            assertTrue(lang.has("stardewcraft.mail.summer_25_1"), language);
            assertTrue(lang.has("stardewcraft.mail.fall_3_1"), language);
            assertTrue(lang.has("stardewcraft.mail.fall_8_1"), language);
            assertTrue(lang.has("stardewcraft.mail.fall_19_1"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_2_1"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_6_1"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_12_1"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_17_1"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_21_1"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_26_1"), language);
            assertTrue(lang.has("stardewcraft.mail.spring_6_2"), language);
            assertTrue(lang.has("stardewcraft.mail.spring_15_2"), language);
            assertTrue(lang.has("stardewcraft.mail.spring_21_2"), language);
            assertTrue(lang.has("stardewcraft.mail.summer_6_2"), language);
            assertTrue(lang.has("stardewcraft.mail.summer_15_2"), language);
            assertTrue(lang.has("stardewcraft.mail.summer_21_2"), language);
            assertTrue(lang.has("stardewcraft.mail.fall_6_2"), language);
            assertTrue(lang.has("stardewcraft.mail.fall_19_2"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_5_2"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_13_2"), language);
            assertTrue(lang.has("stardewcraft.mail.winter_19_2"), language);
        }
    }

    private static void assertMailQuest(JsonArray entries, String id, String questId) {
        JsonObject entry = entries.asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(candidate -> id.equals(candidate.get("id").getAsString()))
                .findFirst()
                .orElseThrow();
        assertEquals(questId, entry.get("questId").getAsString());
    }

    private static JsonArray jsonArray(String path) throws Exception {
        return JsonParser.parseReader(new InputStreamReader(
                resource(path), StandardCharsets.UTF_8)).getAsJsonArray();
    }

    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseReader(new InputStreamReader(
                resource(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static InputStream resource(String path) {
        InputStream stream = StoryQuest100To125ResourceTest.class
                .getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
