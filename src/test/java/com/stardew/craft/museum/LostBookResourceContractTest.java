package com.stardew.craft.museum;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LostBookResourceContractTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");
    private static final int[][] INTERACTION_COLUMNS = {
            {117, 42}, {117, 41}, {117, 40}, {117, 39}, {117, 38}, {117, 37},
            {118, 30}, {119, 30}, {120, 30}, {121, 30},
            {118, 36}, {119, 36}, {120, 36}, {121, 36},
            {124, 36}, {125, 36}, {126, 36}, {127, 36}, {128, 36}, {129, 36},
            {130, 37}
    };

    @Test
    void builtInLibraryHasAllTwentyOneVanillaNotesInUnlockOrder() throws Exception {
        JsonArray books = jsonArray("data/stardewcraft/museum/lost_books.json");
        assertEquals(21, books.size());

        Set<Integer> unlocks = new HashSet<>();
        for (int i = 0; i < books.size(); i++) {
            JsonObject book = books.get(i).getAsJsonObject();
            assertEquals("book_" + i, book.get("id").getAsString());
            assertEquals(i, book.get("unlock_at").getAsInt());
            assertEquals("stardewcraft.lost_book.text." + i, book.get("text").getAsString());
            JsonArray interactions = book.getAsJsonArray("interactions");
            assertEquals(2, interactions.size());
            for (int layer = 0; layer < 2; layer++) {
                JsonObject interaction = interactions.get(layer).getAsJsonObject();
                assertEquals(INTERACTION_COLUMNS[i][0], interaction.get("x").getAsInt());
                assertEquals(38 + layer, interaction.get("y").getAsInt());
                assertEquals(INTERACTION_COLUMNS[i][1], interaction.get("z").getAsInt());
            }
            unlocks.add(book.get("unlock_at").getAsInt());
        }
        assertEquals(21, unlocks.size());
    }

    @Test
    void itemAndUnreadIndicatorUseStandaloneVanillaSizedAssets() throws Exception {
        try (InputStream stream = resource("assets/stardewcraft/textures/item/lost_book.png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
        try (InputStream stream = resource(
                "assets/stardewcraft/textures/misc/lost_book_indicator.png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(15, image.getWidth());
            assertEquals(15, image.getHeight());
        }

        JsonObject model = jsonObject("assets/stardewcraft/models/item/lost_book.json");
        assertEquals("item/generated", model.get("parent").getAsString());
        assertEquals("stardewcraft:item/lost_book",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void everySupportedLanguageContainsTheItemMessagesAndAllBookTexts() throws Exception {
        for (String language : LANGUAGES) {
            JsonObject lang = jsonObject("assets/stardewcraft/lang/" + language + ".json");
            assertNonBlank(lang, "item.stardewcraft.lost_book", language);
            assertNonBlank(lang, "item.stardewcraft.lost_book.desc", language);
            assertNonBlank(lang, "stardewcraft.lost_book.found", language);
            assertNonBlank(lang, "stardewcraft.lost_book.chat", language);
            assertNonBlank(lang, "stardewcraft.lost_book.missing", language);
            for (int i = 0; i < 21; i++) {
                assertNonBlank(lang, "stardewcraft.lost_book.text." + i, language);
            }
        }
    }

    @Test
    void englishPickupAndMissingMessagesMatchVanillaSource() throws Exception {
        JsonObject english = jsonObject("assets/stardewcraft/lang/en_us.json");
        assertEquals("You found a lost book. The library has been expanded.",
                english.get("stardewcraft.lost_book.found").getAsString());
        assertEquals("There's a book missing here...",
                english.get("stardewcraft.lost_book.missing").getAsString());
        assertEquals("Writings from a wide variety of time periods.",
                english.get("item.stardewcraft.lost_book.desc").getAsString());
    }

    private static void assertNonBlank(JsonObject json, String key, String language) {
        assertTrue(json.has(key), language + " missing " + key);
        assertTrue(!json.get(key).getAsString().isBlank(), language + " blank " + key);
    }

    private static JsonArray jsonArray(String path) throws Exception {
        try (InputStream stream = resource(path);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        }
    }

    private static JsonObject jsonObject(String path) throws Exception {
        try (InputStream stream = resource(path);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = LostBookResourceContractTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
