package com.stardew.craft.api.v1.production;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewCraftingRecipeDefinitionTest {
    private static final String BASE_RECIPE = """
            {
              "output": "minecraft:chest",
              %s
              "ingredients": [
                {"item": "minecraft:oak_planks", "count": 8}
              ]
            }
            """;

    @Test
    void existingDataPacksStillDecodeWithoutDisplayMetadata() {
        StardewCraftingRecipeDefinition recipe = parse(BASE_RECIPE.formatted(""));
        assertEquals("minecraft:chest", recipe.output().toString());
    }

    @Test
    void displayMetadataDoesNotBreakThePublicRecipeCodec() {
        StardewCraftingRecipeDefinition recipe = parse(BASE_RECIPE.formatted("\"big_craftable\": true,"));
        assertEquals("minecraft:chest", recipe.output().toString());
    }

    private static StardewCraftingRecipeDefinition parse(String json) {
        return StardewCraftingRecipeDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result()
                .orElseThrow();
    }
}
