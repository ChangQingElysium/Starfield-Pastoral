package com.stardew.craft.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import com.stardew.craft.api.v1.shop.StardewShopDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnimalShopSourceParityTest {
    @BeforeAll
    static void bootstrapApiTypes() {
        BuiltinApiTypes.bootstrap();
    }

    @Test
    void editedShopsConformToRuntimeCodec() {
        for (String path : new String[]{
                "/data/stardewcraft/shops/animal_shop.json",
                "/data/stardewcraft/shops/joja_mart.json"}) {
            StardewShopDefinition.CODEC.parse(
                    JsonOps.INSTANCE, resource(path))
                    .getOrThrow();
        }
    }

    @Test
    void marnieToolsAndAutoGrabberMatchShopsData() {
        JsonArray entries = resource(
                "/data/stardewcraft/shops/animal_shop.json")
                .getAsJsonArray("entries");

        assertEquals(1000, entry(entries, "stardewcraft:milk_pail")
                .get("price").getAsInt());
        assertEquals(1000, entry(entries, "stardewcraft:shears")
                .get("price").getAsInt());
        assertLacksOwnedTool(
                entry(entries, "stardewcraft:milk_pail"),
                "stardewcraft:milk_pail");
        assertLacksOwnedTool(
                entry(entries, "stardewcraft:shears"),
                "stardewcraft:shears");

        JsonObject autoGrabber =
                entry(entries, "stardewcraft:auto_grabber");
        assertEquals(25000, autoGrabber.get("price").getAsInt());
        JsonObject condition = autoGrabber
                .getAsJsonArray("available_when")
                .get(0).getAsJsonObject();
        assertEquals(
                "stardewcraft:skill",
                condition.get("type").getAsString());
        assertEquals(
                "farming",
                condition.getAsJsonObject("data")
                        .get("skill").getAsString());
        assertEquals(
                10,
                condition.getAsJsonObject("data")
                        .get("level").getAsInt());

        assertFalse(contains(
                entries, "stardewcraft:auto_petter"));
    }

    @Test
    void autoPetterUsesJojaEventGateFromShopsData() {
        JsonArray entries = resource(
                "/data/stardewcraft/shops/joja_mart.json")
                .getAsJsonArray("entries");
        JsonObject autoPetter =
                entry(entries, "stardewcraft:auto_petter");

        assertEquals(50000, autoPetter.get("price").getAsInt());
        JsonObject condition = autoPetter
                .getAsJsonArray("available_when")
                .get(0).getAsJsonObject();
        assertEquals(
                "stardewcraft:seen_event",
                condition.get("type").getAsString());
        assertEquals(
                "502261",
                condition.getAsJsonObject("data")
                        .get("id").getAsString());
        assertEquals(
                "host",
                condition.getAsJsonObject("data")
                        .get("scope").getAsString());
    }

    private static void assertLacksOwnedTool(
            JsonObject entry,
            String item
    ) {
        JsonObject condition = entry
                .getAsJsonArray("available_when")
                .get(0).getAsJsonObject();
        assertEquals(
                "stardewcraft:lacks_item",
                condition.get("type").getAsString());
        assertEquals(
                item,
                condition.getAsJsonObject("data")
                        .get("item").getAsString());
    }

    private static JsonObject resource(String path) {
        var stream =
                AnimalShopSourceParityTest.class
                        .getResourceAsStream(path);
        if (stream == null) {
            throw new AssertionError(
                    "Missing test resource " + path);
        }
        try (stream;
             var reader = new InputStreamReader(
                     stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static JsonObject entry(
            JsonArray entries,
            String item
    ) {
        for (var element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (item.equals(entry.get("item").getAsString())) {
                return entry;
            }
        }
        throw new AssertionError("Missing shop item " + item);
    }

    private static boolean contains(
            JsonArray entries,
            String item
    ) {
        for (var element : entries) {
            if (item.equals(element.getAsJsonObject()
                    .get("item").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
