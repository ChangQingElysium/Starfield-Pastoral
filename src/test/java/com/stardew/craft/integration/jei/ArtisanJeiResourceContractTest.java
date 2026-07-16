package com.stardew.craft.integration.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    private static JsonObject resource(String machine) throws Exception {
        String path = "/data/stardewcraft/artisan/" + machine + ".json";
        try (var stream = ArtisanJeiResourceContractTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing artisan definition " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
