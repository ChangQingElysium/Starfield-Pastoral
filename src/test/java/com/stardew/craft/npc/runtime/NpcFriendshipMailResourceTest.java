package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NpcFriendshipMailResourceTest {

    @Test
    void giftMailMatchesTheSeventeenVanillaNpcLetterSources() {
        JsonArray rules = resourceArray(
                "/data/stardewcraft/npc/friendship_gift_mail.json");
        assertEquals(17, rules.size());

        JsonObject linus = StreamSupport.stream(
                        rules.spliterator(), false)
                .map(element -> element.getAsJsonObject())
                .filter(rule -> "linus".equals(
                        rule.get("npc").getAsString()))
                .findFirst()
                .orElseThrow();
        Set<String> fish = StreamSupport.stream(
                        linus.getAsJsonArray("object_choices")
                                .spliterator(), false)
                .map(element -> element.getAsJsonObject()
                        .get("object").getAsString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("136", "143", "202", "227", "228"),
                fish);
    }

    @Test
    void recipeMailMatchesAllThirtySixVanillaFriendshipRecipes() {
        JsonArray rules = resourceArray(
                "/data/stardewcraft/npc/friendship_recipe_mail.json");
        assertEquals(36, rules.size());
        long linusRules = StreamSupport.stream(
                        rules.spliterator(), false)
                .map(element -> element.getAsJsonObject())
                .filter(rule -> "linus".equals(
                        rule.get("npc").getAsString()))
                .count();
        assertEquals(2, linusRules);
    }

    private static JsonArray resourceArray(String path) {
        var stream = NpcFriendshipMailResourceTest.class
                .getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonArray();
    }
}
