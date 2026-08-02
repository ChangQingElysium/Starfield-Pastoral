package com.stardew.craft.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FishSmokerSourceParityTest {
    @Test
    void recipeIsShopOnlyAndWillySellsItForSourcePrice() throws Exception {
        JsonObject recipes = resource(
                "/data/stardewcraft/player/vanilla_crafting_recipes.json");
        JsonObject smokerRecipe = find(
                recipes.getAsJsonArray("recipes"), "id", "fish_smoker");
        assertNotNull(smokerRecipe);
        assertEquals("null", smokerRecipe.get("unlockCondition").getAsString());

        JsonObject fishShop = resource(
                "/data/stardewcraft/shops/fish_shop.json");
        JsonObject shopRecipe = find(
                fishShop.getAsJsonArray("entries"),
                "item",
                "recipe:stardewcraft:fish_smoker");
        assertNotNull(shopRecipe);
        assertEquals(10_000, shopRecipe.get("price").getAsInt());
        assertEquals(1, shopRecipe.get("stock").getAsInt());
    }

    private static JsonObject find(JsonArray entries, String field, String value) {
        for (var element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has(field) && value.equals(entry.get(field).getAsString())) {
                return entry;
            }
        }
        return null;
    }

    private JsonObject resource(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
