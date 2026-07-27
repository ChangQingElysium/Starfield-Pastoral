package com.stardew.craft.animal.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAnimalDefinitionsTest {
    @BeforeAll
    static void bootstrapApiTypes() {
        BuiltinApiTypes.bootstrap();
    }

    @Test
    void builtInIdsAreCompleteAndUnique() {
        assertEquals(Set.of(
                "white_chicken",
                "brown_chicken",
                "blue_chicken",
                "void_chicken",
                "golden_chicken",
                "duck",
                "rabbit",
                "dinosaur",
                "ostrich",
                "cow",
                "brown_cow",
                "goat",
                "sheep",
                "pig"
        ), FarmAnimalDefinitions.ids());
        assertEquals(FarmAnimalDefinitions.ids().size(), FarmAnimalDefinitions.all().size());
    }

    @Test
    void maturityProductionGrassAndDrainMatchSourceLedger() {
        Map<String, int[]> expected = Map.ofEntries(
                Map.entry("white_chicken", values(3, 1, 2, 7)),
                Map.entry("brown_chicken", values(3, 1, 2, 7)),
                Map.entry("blue_chicken", values(3, 1, 2, 7)),
                Map.entry("void_chicken", values(3, 1, 2, 5)),
                Map.entry("golden_chicken", values(3, 1, 2, 10)),
                Map.entry("duck", values(5, 2, 2, 5)),
                Map.entry("rabbit", values(6, 4, 2, 5)),
                Map.entry("dinosaur", values(0, 7, 2, 3)),
                Map.entry("ostrich", values(7, 7, 4, 5)),
                Map.entry("cow", values(5, 1, 4, 6)),
                Map.entry("brown_cow", values(5, 1, 4, 6)),
                Map.entry("goat", values(5, 2, 4, 5)),
                Map.entry("sheep", values(4, 3, 4, 5)),
                Map.entry("pig", values(10, 1, 4, 4))
        );

        expected.forEach((id, values) -> {
            FarmAnimalDefinition definition = FarmAnimalDefinitions.require(id);
            assertEquals(values[0], definition.daysToMature(), id);
            assertEquals(values[1], definition.daysToProduce(), id);
            assertEquals(values[2], definition.grassEatAmount(), id);
            assertEquals(values[3], definition.happinessDrain(), id);
        });
    }

    @Test
    void ostrichHousingIsTheExplicitProjectOverrideOnly() {
        FarmAnimalDefinition ostrich = FarmAnimalDefinitions.require("ostrich");
        assertEquals("barn", ostrich.sourceHouse());
        assertEquals("coop", ostrich.family());
        assertTrue(ostrich.hasProjectOverride());

        assertEquals(1L, FarmAnimalDefinitions.all().stream()
                .filter(FarmAnimalDefinition::hasProjectOverride)
                .count());
    }

    @Test
    void sourceProfessionAssignmentsDoNotFollowTheHousingOverride() {
        FarmAnimalDefinition ostrich = FarmAnimalDefinitions.require("ostrich");
        assertEquals(
                ProfessionType.SHEPHERD.getId(),
                ostrich.professionForHappinessBoost());
        assertEquals(
                ProfessionType.SHEPHERD.getId(),
                ostrich.professionForQualityBoost());
        assertEquals(-1, ostrich.professionForFasterProduce());

        FarmAnimalDefinition sheep = FarmAnimalDefinitions.require("sheep");
        assertEquals(900, sheep.friendshipForFasterProduce());
        assertEquals(ProfessionType.SHEPHERD.getId(), sheep.professionForFasterProduce());
        assertEquals(-1, FarmAnimalDefinitions.require("cow").professionForFasterProduce());
        assertEquals(-1, FarmAnimalDefinitions.require("duck").professionForFasterProduce());
    }

    @Test
    void shopAndSellValuesMatchFarmAnimalsData() {
        assertEconomy("white_chicken", 400, 800, 1);
        assertEconomy("duck", 600, 1200, 2);
        assertEconomy("rabbit", 4000, 8000, 3);
        assertEconomy("cow", 750, 1500, 1);
        assertEquals(
                1500,
                FarmAnimalDefinitions.require(
                        "brown_cow").sellPrice());
        assertEconomy("goat", 2000, 4000, 2);
        assertEconomy("sheep", 4000, 8000, 3);
        assertEconomy("pig", 8000, 16000, 3);

        assertEquals(1000, FarmAnimalDefinitions.require("dinosaur").sellPrice());
        assertEquals(16000, FarmAnimalDefinitions.require("ostrich").sellPrice());
        assertFalse(FarmAnimalDefinitions.require("ostrich").soldByAnimalShop());
    }

    @Test
    void productionCapabilitiesMatchSource() {
        assertTrue(FarmAnimalDefinitions.require("duck").canSwim());
        assertFalse(FarmAnimalDefinitions.require("pig").canEatGoldenCrackers());
        assertTrue(FarmAnimalDefinitions.require("cow").canGetPregnant());
        assertFalse(FarmAnimalDefinitions.require("ostrich").canGetPregnant());
        assertNull(FarmAnimalDefinitions.require("pig").deluxeProduceItemId());
        assertEquals(
                FarmAnimalDefinition.HarvestType.DIG_UP,
                FarmAnimalDefinitions.require("pig").harvestType());
    }

    @Test
    void bundledDefinitionsRetainTraceableSourceFields() {
        FarmAnimalDefinition cow = FarmAnimalDefinitions.require("cow");
        assertEquals("White Cow", cow.sourceKey());
        assertEquals("Barn", cow.requiredBuilding());
        assertEquals("184", cow.produce().getFirst().sourceItemId());
        assertEquals("stardewcraft:milk", cow.produceItemId().toString());
        assertEquals("stardewcraft:milk_pail", cow.harvestTool().toString());
        assertEquals(
                "stardewcraft:textures/gui/animal_purchase/cow.png",
                cow.shopTextureId().toString());
        assertEquals(32, cow.shopTextureWidth());
        assertEquals(16, cow.shopTextureHeight());

        FarmAnimalDefinition whiteChicken =
                FarmAnimalDefinitions.require("white_chicken");
        assertEquals(2, whiteChicken.alternatePurchaseTypes().size());
        assertEquals(
                "RANDOM 0.25, PLAYER_HAS_SEEN_EVENT Current 3900074",
                whiteChicken.alternatePurchaseTypes().getFirst().sourceCondition());
        assertEquals(0.25,
                whiteChicken.alternatePurchaseTypes().getFirst().chance());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "seen_event"),
                whiteChicken.alternatePurchaseTypes().getFirst().condition().type());

        assertEquals(
                java.util.List.of("cow", "brown_cow"),
                cow.alternatePurchaseTypes()
                        .getFirst()
                        .animalTypeIds());
    }

    @Test
    void bundledGameplayFieldsRoundTripThroughDecoder() {
        JsonObject bundled = bundledResources().values()
                .iterator()
                .next()
                .getAsJsonObject();
        Map<String, JsonObject> expectedBySourceKey = new HashMap<>();
        for (JsonElement element : bundled.getAsJsonArray("animals")) {
            JsonObject animal = element.getAsJsonObject();
            expectedBySourceKey.put(
                    animal.get("source_key").getAsString(),
                    animal);
        }

        assertEquals(
                FarmAnimalDefinitions.all().stream()
                        .map(FarmAnimalDefinition::sourceKey)
                        .collect(java.util.stream.Collectors.toSet()),
                expectedBySourceKey.keySet());
        for (FarmAnimalDefinition definition :
                FarmAnimalDefinitions.all()) {
            String sourceKey = definition.sourceKey();
            JsonObject expected = expectedBySourceKey.get(sourceKey);
            assertEquals(expected.get("source_house").getAsString()
                            .toLowerCase(java.util.Locale.ROOT),
                    definition.sourceHouse(), sourceKey + " source_house");
            assertEquals(expected.get("gender").getAsString(),
                    definition.gender(), sourceKey + " gender");
            assertEquals(expected.get("purchase_price").getAsInt(),
                    definition.purchasePrice(), sourceKey + " purchase_price");
            assertEquals(expected.get("sell_price").getAsInt(),
                    definition.sellPrice(), sourceKey + " sell_price");
            assertEquals(nullableString(expected, "required_building"),
                    definition.requiredBuilding(),
                    sourceKey + " required_building");
            assertEquals(expected.get("incubation_time").getAsInt(),
                    definition.incubationTime(), sourceKey + " incubation_time");
            assertEquals(expected.get("incubator_parent_sheet_offset").getAsInt(),
                    definition.incubatorParentSheetOffset(),
                    sourceKey + " incubator_parent_sheet_offset");
            assertEquals(expected.get("days_to_mature").getAsInt(),
                    definition.daysToMature(), sourceKey + " days_to_mature");
            assertEquals(expected.get("can_get_pregnant").getAsBoolean(),
                    definition.canGetPregnant(), sourceKey + " can_get_pregnant");
            assertEquals(expected.get("days_to_produce").getAsInt(),
                    definition.daysToProduce(), sourceKey + " days_to_produce");
            assertEquals(normalizeHarvestType(
                            expected.get("harvest_type").getAsString()),
                    definition.harvestType(), sourceKey + " harvest_type");
            assertEquals(expected.get("produce_on_mature").getAsBoolean(),
                    definition.produceOnMature(), sourceKey + " produce_on_mature");
            assertEquals(expected.get("friendship_for_faster_produce").getAsInt(),
                    definition.friendshipForFasterProduce(),
                    sourceKey + " friendship_for_faster_produce");
            assertEquals(expected.get("deluxe_produce_minimum_friendship").getAsInt(),
                    definition.deluxeProduceMinimumFriendship(),
                    sourceKey + " deluxe_produce_minimum_friendship");
            assertEquals(expected.get("deluxe_produce_care_divisor").getAsDouble(),
                    definition.deluxeProduceCareDivisor(),
                    sourceKey + " deluxe_produce_care_divisor");
            assertEquals(expected.get("deluxe_produce_luck_multiplier").getAsDouble(),
                    definition.deluxeProduceLuckMultiplier(),
                    sourceKey + " deluxe_produce_luck_multiplier");
            assertEquals(expected.get("can_eat_golden_crackers").getAsBoolean(),
                    definition.canEatGoldenCrackers(),
                    sourceKey + " can_eat_golden_crackers");
            assertEquals(expected.get("profession_for_happiness_boost").getAsInt(),
                    definition.professionForHappinessBoost(),
                    sourceKey + " profession_for_happiness_boost");
            assertEquals(expected.get("profession_for_quality_boost").getAsInt(),
                    definition.professionForQualityBoost(),
                    sourceKey + " profession_for_quality_boost");
            assertEquals(expected.get("profession_for_faster_produce").getAsInt(),
                    definition.professionForFasterProduce(),
                    sourceKey + " profession_for_faster_produce");
            assertEquals(expected.get("can_swim").getAsBoolean(),
                    definition.canSwim(), sourceKey + " can_swim");
            assertEquals(expected.get("babies_follow_adults").getAsBoolean(),
                    definition.babiesFollowAdults(),
                    sourceKey + " babies_follow_adults");
            assertEquals(expected.get("grass_eat_amount").getAsInt(),
                    definition.grassEatAmount(), sourceKey + " grass_eat_amount");
            assertEquals(expected.get("happiness_drain").getAsInt(),
                    definition.happinessDrain(), sourceKey + " happiness_drain");
            assertProduceParity(
                    sourceKey,
                    expected.getAsJsonArray("produce"),
                    definition.produce());
            assertProduceParity(
                    sourceKey + " deluxe",
                    expected.getAsJsonArray("deluxe_produce"),
                    definition.deluxeProduce());
            assertProduceStatParity(
                    sourceKey,
                    nullableArray(
                            expected,
                            "produce_stats"),
                    definition.produceStats());
        }
    }

    @Test
    void addonFileCanAddACompleteAnimalWithoutJavaRegistryData() {
        Map<ResourceLocation, JsonElement> resources = bundledResources();
        resources.put(
                ResourceLocation.fromNamespaceAndPath("example", "goose"),
                JsonParser.parseString(customDefinition(
                        "stardewcraft:duck", "example:goose", false, 900))
        );

        FarmAnimalDefinitions.Snapshot decoded =
                FarmAnimalDefinitions.decodeForTests(resources);
        FarmAnimalDefinition goose = decoded.byAnimalType().get("example:goose");

        assertEquals("coop", goose.family());
        assertEquals(900, goose.purchasePrice());
        assertEquals("stardewcraft:duck", goose.entityTypeId().toString());
        assertTrue(decoded.animalShopOrder().contains("example:goose"));
        assertEquals(
                "entity.example.goose",
                FarmAnimalDefinitions.displayNameKeyFor(
                        "example:goose"));
    }

    @Test
    void standaloneSnapshotDoesNotRequireAnyBuiltInAnimalId() {
        ResourceLocation resourceId =
                ResourceLocation.fromNamespaceAndPath(
                        "example", "goose");
        FarmAnimalDefinitions.Snapshot decoded =
                FarmAnimalDefinitions.decodeForTests(Map.of(
                        resourceId,
                        JsonParser.parseString(customDefinition(
                                "stardewcraft:duck",
                                "example:goose",
                                false,
                                900))
                ));

        assertEquals(
                Set.of("example:goose"),
                decoded.byAnimalType().keySet());
        assertEquals(
                resourceId,
                decoded.sources().get("example:goose"));
    }

    @Test
    void crossFileOverrideMustBeExplicitAndIsDeterministic() {
        Map<ResourceLocation, JsonElement> resources = bundledResources();
        resources.put(
                ResourceLocation.fromNamespaceAndPath("example", "cheaper_cow"),
                JsonParser.parseString(customDefinition("stardewcraft:cow", "cow", true, 123))
        );

        FarmAnimalDefinitions.Snapshot decoded =
                FarmAnimalDefinitions.decodeForTests(resources);

        assertEquals(123, decoded.byAnimalType().get("cow").purchasePrice());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("example", "cheaper_cow"),
                decoded.sources().get("cow"));
    }

    @Test
    void accidentalCrossFileDuplicateRejectsTheSnapshot() {
        Map<ResourceLocation, JsonElement> resources = bundledResources();
        resources.put(
                ResourceLocation.fromNamespaceAndPath("example", "cow_collision"),
                JsonParser.parseString(customDefinition(
                        "stardewcraft:cow", "cow", false, 123))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> FarmAnimalDefinitions.decodeForTests(resources));
    }

    @Test
    void malformedAddonResourceRejectsTheWholeCandidateSnapshot() {
        Map<ResourceLocation, JsonElement> resources =
                bundledResources();
        resources.put(
                ResourceLocation.fromNamespaceAndPath(
                        "example", "broken_goose"),
                JsonParser.parseString("""
                        {
                          "animal_type_id": "example:goose",
                          "source_key": "Broken Goose"
                        }
                        """));

        assertThrows(
                IllegalArgumentException.class,
                () -> FarmAnimalDefinitions
                        .decodeForTests(resources));
    }

    @Test
    void runtimeReferenceValidationRejectsAirAsProduce() {
        Map<ResourceLocation, JsonElement> resources =
                bundledResources();
        resources.put(
                ResourceLocation.fromNamespaceAndPath(
                        "example", "air_goose"),
                JsonParser.parseString(
                        customDefinition(
                                "stardewcraft:duck",
                                "example:air_goose",
                                false,
                                900)
                                .replace(
                                        "\"minecraft:egg\"",
                                        "\"minecraft:air\"")));

        FarmAnimalDefinitions.Snapshot decoded =
                FarmAnimalDefinitions.decodeForTests(resources);
        assertThrows(
                IllegalArgumentException.class,
                () -> FarmAnimalDefinitions
                        .validateRuntimeReferences(decoded));
    }

    @Test
    void runtimeValidationRejectsDefaultNameOutsideServerContract() {
        Map<ResourceLocation, JsonElement> resources =
                bundledResources();
        resources.put(
                ResourceLocation.fromNamespaceAndPath(
                        "example", "long_name_goose"),
                JsonParser.parseString(
                        customDefinition(
                                "stardewcraft:duck",
                                "example:long_name_goose",
                                false,
                                900)
                                .replace(
                                        "\"default_name\": \"Goose\"",
                                        "\"default_name\": \""
                                                + "x".repeat(
                                                AnimalNameRules.MAX_LENGTH
                                                        + 1)
                                                + "\"")));

        FarmAnimalDefinitions.Snapshot decoded =
                FarmAnimalDefinitions.decodeForTests(resources);
        assertThrows(
                IllegalArgumentException.class,
                () -> FarmAnimalDefinitions
                        .validateRuntimeReferences(decoded));
    }

    @Test
    void buildingCapabilitiesAreCentralizedByTier() {
        assertFalse(AnimalBuildingType.BARN_TIER_1.allowsAnimalPregnancy());
        assertTrue(AnimalBuildingType.BARN_TIER_2.allowsAnimalPregnancy());
        assertTrue(AnimalBuildingType.BARN_TIER_3.allowsAnimalPregnancy());
        assertFalse(AnimalBuildingType.COOP_TIER_3.allowsAnimalPregnancy());

        assertFalse(AnimalBuildingType.COOP_TIER_2.hasAutomaticFeed());
        assertTrue(AnimalBuildingType.COOP_TIER_3.hasAutomaticFeed());
        assertFalse(AnimalBuildingType.BARN_TIER_2.hasAutomaticFeed());
        assertTrue(AnimalBuildingType.BARN_TIER_3.hasAutomaticFeed());
    }

    private static int[] values(int maturity, int production, int grass, int drain) {
        return new int[]{maturity, production, grass, drain};
    }

    private static void assertProduceParity(
            String sourceKey,
            com.google.gson.JsonArray expected,
            java.util.List<FarmAnimalDefinition.ProduceEntry> actual
    ) {
        assertEquals(expected.size(), actual.size(),
                sourceKey + " produce count");
        for (int index = 0; index < expected.size(); index++) {
            JsonObject sourceEntry =
                    expected.get(index).getAsJsonObject();
            FarmAnimalDefinition.ProduceEntry definition =
                    actual.get(index);
            assertEquals(sourceEntry.get("id").getAsString(),
                    definition.id(), sourceKey + " produce ID " + index);
            assertEquals(nullableString(sourceEntry, "source_condition"),
                    definition.sourceCondition(),
                    sourceKey + " produce condition " + index);
            assertEquals(sourceEntry.get("minimum_friendship").getAsInt(),
                    definition.minimumFriendship(),
                    sourceKey + " produce friendship " + index);
            assertEquals(nullableString(sourceEntry, "source_item_id"),
                    definition.sourceItemId(),
                    sourceKey + " produce item " + index);
        }
    }

    private static void assertProduceStatParity(
            String sourceKey,
            com.google.gson.JsonArray expected,
            java.util.List<FarmAnimalDefinition.ProduceStat>
                    actual
    ) {
        assertEquals(
                expected.size(),
                actual.size(),
                sourceKey + " produce stat count");
        for (int index = 0;
                index < expected.size();
                index++) {
            JsonObject sourceEntry =
                    expected.get(index).getAsJsonObject();
            FarmAnimalDefinition.ProduceStat definition =
                    actual.get(index);
            assertEquals(
                    sourceEntry.get("id").getAsString(),
                    definition.id(),
                    sourceKey + " produce stat ID "
                            + index);
            assertEquals(
                    sourceEntry.get("stat_name")
                            .getAsString(),
                    definition.statName(),
                    sourceKey + " produce stat name "
                            + index);
            assertEquals(
                    nullableString(
                            sourceEntry,
                            "source_required_item_id"),
                    definition.sourceRequiredItemId(),
                    sourceKey
                            + " produce stat item filter "
                            + index);
            assertEquals(
                    nullableStringList(
                            sourceEntry,
                            "source_required_tags"),
                    definition.sourceRequiredTags(),
                    sourceKey
                            + " produce stat tag filters "
                            + index);
        }
    }

    private static java.util.List<String>
    nullableStringList(
            JsonObject value,
            String field
    ) {
        JsonElement element = value.get(field);
        if (element == null || element.isJsonNull()) {
            return java.util.List.of();
        }
        java.util.ArrayList<String> result =
                new java.util.ArrayList<>();
        for (JsonElement entry :
                element.getAsJsonArray()) {
            result.add(entry.getAsString());
        }
        return java.util.List.copyOf(result);
    }

    private static com.google.gson.JsonArray nullableArray(
            JsonObject value,
            String field
    ) {
        JsonElement element = value.get(field);
        return element == null || element.isJsonNull()
                ? new com.google.gson.JsonArray()
                : element.getAsJsonArray();
    }

    private static String nullableString(
            JsonObject value,
            String field
    ) {
        JsonElement element = value.get(field);
        return element == null || element.isJsonNull()
                ? null
                : element.getAsString();
    }

    private static FarmAnimalDefinition.HarvestType normalizeHarvestType(
            String sourceValue
    ) {
        return switch (sourceValue) {
            case "drop_overnight" ->
                    FarmAnimalDefinition.HarvestType.DROP_OVERNIGHT;
            case "harvest_with_tool" ->
                    FarmAnimalDefinition.HarvestType.HARVEST_WITH_TOOL;
            case "dig_up" ->
                    FarmAnimalDefinition.HarvestType.DIG_UP;
            default -> throw new AssertionError(
                    "Unknown source HarvestType " + sourceValue);
        };
    }

    private static Map<ResourceLocation, JsonElement> bundledResources() {
        var stream = FarmAnimalDefinitionsTest.class.getResourceAsStream(
                "/data/stardewcraft/stardewcraft/farm_animals/vanilla_1_6_15.json");
        if (stream == null) {
            throw new AssertionError("bundled farm-animal data is missing");
        }
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<ResourceLocation, JsonElement> resources = new HashMap<>();
            resources.put(
                    ResourceLocation.fromNamespaceAndPath(
                            "stardewcraft", "vanilla_1_6_15"),
                    JsonParser.parseReader(reader));
            return resources;
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String customDefinition(
            String entityType,
            String animalTypeId,
            boolean replace,
            int purchasePrice
    ) {
        return """
                {
                  "replace": %s,
                  "animal_type_id": "%s",
                  "source_key": "Test Goose",
                  "source_house": "Coop",
                  "family": "coop",
                  "purchase_price": %d,
                  "sell_price": 1800,
                  "days_to_mature": 4,
                  "days_to_produce": 2,
                  "harvest_type": "drop_overnight",
                  "produce": [
                    {"item": "minecraft:egg", "minimum_friendship": 0}
                  ],
                  "grass_eat_amount": 2,
                  "happiness_drain": 5,
                  "entity_type": "%s",
                  "required_building_tier": 1,
                  "shop_order": 500,
                  "default_name": "Goose"
                }
                """.formatted(replace, animalTypeId, purchasePrice, entityType);
    }

    private static void assertEconomy(
            String id,
            int purchasePrice,
            int sellPrice,
            int requiredTier
    ) {
        FarmAnimalDefinition definition = FarmAnimalDefinitions.require(id);
        assertEquals(purchasePrice, definition.purchasePrice(), id);
        assertEquals(sellPrice, definition.sellPrice(), id);
        assertEquals(requiredTier, definition.requiredBuildingTier(), id);
        assertEquals((int) Math.floor(sellPrice * 0.3), definition.sellPriceAtFriendship(0), id);
        assertEquals((int) Math.floor(sellPrice * 1.3), definition.sellPriceAtFriendship(1000), id);
    }
}
