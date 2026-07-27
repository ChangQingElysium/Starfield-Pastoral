package com.stardew.craft.integration.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.item.ModItems;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtisanJeiResourceContractTest {
    @Test
    void everyStableMachineHasAReadableArtisanDefinition() throws Exception {
        for (MachineJeiRegistry.Machine machine : MachineJeiRegistry.all()) {
            if (!"stardewcraft".equals(machine.id().getNamespace())) {
                continue;
            }
            String path = "/data/stardewcraft/artisan/" + machine.id().getPath() + ".json";
            try (var stream = ArtisanJeiResourceContractTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, "missing artisan definition " + path);
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(machine.id().getPath(), root.get("machine").getAsString());
                assertTrue(root.has("recipes"));
                assertFalse(root.getAsJsonArray("recipes").isEmpty(), "empty artisan definition " + path);
            }
        }
    }

    @Test
    void specialMachinesRetainTheDataNeededByJeiProjection() throws Exception {
        JsonObject preserves = resource("preserves_jar");
        assertTrue(preserves.getAsJsonArray("recipes").asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(recipe -> recipe.has("tag") && "JELLY".equals(recipe.get("preserveType").getAsString())));

        JsonObject keg = resource("keg");
        assertTrue(keg.getAsJsonArray("recipes").asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(recipe -> "stardewcraft:keg_wine_inputs".equals(
                                recipe.has("tag") ? recipe.get("tag").getAsString() : "")
                        && "stardewcraft:wine".equals(recipe.get("output").getAsString())
                        && "WINE".equals(recipe.get("preserveType").getAsString())));
        assertTrue(keg.getAsJsonArray("recipes").asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(recipe -> "stardewcraft:keg_juice_inputs".equals(
                                recipe.has("tag") ? recipe.get("tag").getAsString() : "")
                        && "stardewcraft:juice".equals(recipe.get("output").getAsString())
                        && "JUICE".equals(recipe.get("preserveType").getAsString())));

        JsonObject smoker = resource("fish_smoker");
        assertTrue(smoker.getAsJsonArray("recipes").asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(recipe -> "fish_type".equals(recipe.get("inputMode").getAsString())
                        && "smoked".equals(recipe.get("outputMode").getAsString())));

        JsonObject seedMaker = resource("seed_maker");
        assertTrue(seedMaker.getAsJsonArray("recipes").asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(recipe -> "seedmaker".equals(recipe.get("outputMode").getAsString())
                        && recipe.has("seedmaker")));
    }

    @Test
    void kegTagsExposeEveryDynamicDrinkVariantWithFlavorMetadata() throws Exception {
        JsonObject wineTag = jsonResource("/data/stardewcraft/tags/item/keg_wine_inputs.json");
        JsonObject juiceTag = jsonResource("/data/stardewcraft/tags/item/keg_juice_inputs.json");
        JsonObject flavorData = jsonResource("/data/stardewcraft/preserves/keg_ingredients.json");

        assertEquals(27, wineTag.getAsJsonArray("values").size());
        assertEquals(32, juiceTag.getAsJsonArray("values").size());
        for (var tag : java.util.List.of(wineTag, juiceTag)) {
            for (var value : tag.getAsJsonArray("values")) {
                String path = value.getAsString().substring("stardewcraft:".length());
                assertTrue(flavorData.has(path), "missing flavor metadata for " + path);
                assertTrue(flavorData.getAsJsonObject(path).has("color"),
                        "missing flavor color for " + path);
            }
        }
    }

    @Test
    void retiredDrinkIdsUseDynamicModelsAndStayHiddenFromJei() throws Exception {
        JsonObject hiddenTag = jsonResource("/data/stardewcraft/tags/item/hidden.json");
        var hiddenIds = hiddenTag.getAsJsonArray("values").asList().stream()
                .map(element -> element.getAsString())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(31, ModItems.LEGACY_FLAVORED_DRINKS.size());
        for (String legacyId : ModItems.LEGACY_FLAVORED_DRINKS.keySet()) {
            assertTrue(hiddenIds.contains("stardewcraft:" + legacyId),
                    "legacy drink must stay hidden from JEI: " + legacyId);
            JsonObject model = jsonResource("/assets/stardewcraft/models/item/" + legacyId + ".json");
            JsonObject textures = model.getAsJsonObject("textures");
            String drinkType = legacyId.endsWith("_wine") ? "wine" : "juice";
            assertEquals("stardewcraft:item/artisan/drinks/" + drinkType + "_base",
                    textures.get("layer0").getAsString());
            assertEquals("stardewcraft:item/artisan/drinks/" + drinkType + "_overlay",
                    textures.get("layer1").getAsString());
        }
    }

    private static JsonObject resource(String machine) throws Exception {
        return jsonResource("/data/stardewcraft/artisan/" + machine + ".json");
    }

    private static JsonObject jsonResource(String path) throws Exception {
        try (var stream = ArtisanJeiResourceContractTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing artisan definition " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
