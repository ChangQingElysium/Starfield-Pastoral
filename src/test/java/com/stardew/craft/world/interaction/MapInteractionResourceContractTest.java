package com.stardew.craft.world.interaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapInteractionResourceContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));

    @BeforeAll
    static void registerBuiltins() {
        com.stardew.craft.api.v1.internal.BuiltinApiTypes.bootstrap();
        BuiltinMapInteractionActions.bootstrap();
    }

    @Test
    void trailerPilotUsesOnlyAuthorApprovedCoordinates()
            throws IOException {
        assertBoxes("trailer_1", List.of(box(
                60, 36, -3, 60, 36, -3)));
        assertBoxes("trailer_2", List.of(box(
                65, 35, -3, 65, 37, -3)));
        assertBoxes("trailer_3", List.of(box(
                66, 35, -3, 66, 37, -3)));
        assertBoxes("trailer_4", List.of(box(
                76, 35, 4, 76, 35, 4)));
        assertBoxes("trailer_5", List.of(box(
                59, 35, 3, 59, 35, 3)));

        JsonObject locations = readJson(
                "src/main/resources/data/stardewcraft/locations/"
                        + "fixed_interiors.json");
        JsonObject trailer = locations.getAsJsonArray("regions")
                .asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(region -> "trailer".equals(
                        region.get("id").getAsString()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(59, 34, -3),
                trailer.getAsJsonArray("min").asList().stream()
                        .map(element -> element.getAsInt())
                        .toList());
        assertEquals(
                List.of(80, 40, 10),
                trailer.getAsJsonArray("max").asList().stream()
                        .map(element -> element.getAsInt())
                        .toList());
    }

    @Test
    void approvedSectionsFourTwoThroughFourSevenMatchAuthorManifest()
            throws IOException {
        JsonObject manifest = readJson(
                "scripts/data/"
                        + "map_interaction_approvals_4_2_to_4_7.json");
        assertEquals(2, manifest.get("format").getAsInt());
        JsonObject geometry =
                manifest.getAsJsonObject("geometry");
        assertEquals(
                "single_block",
                geometry.get("unpaired_points").getAsString());
        assertEquals(
                List.of("_1", "_2"),
                geometry.getAsJsonArray("paired_suffixes")
                        .asList().stream()
                        .map(value -> value.getAsString())
                        .toList());
        assertEquals(
                "inclusive_cuboid",
                geometry.get("paired_points").getAsString());
        int definitionCount = 0;
        int endpointCount = 0;
        int regionCount = 0;

        for (var rawBatch : manifest.getAsJsonArray("batches")) {
            JsonObject batch = rawBatch.getAsJsonObject();
            String map = batch.get("map").getAsString();
            for (var rawInteraction :
                    batch.getAsJsonArray("interactions")) {
                JsonObject interaction =
                        rawInteraction.getAsJsonObject();
                ResourceLocation location =
                        interaction.has("location")
                                && interaction.get("location").isJsonNull()
                                ? null
                                : ResourceLocation.parse(
                                        interaction.has("location")
                                                ? interaction.get("location")
                                                        .getAsString()
                                                : batch.get("location")
                                                        .getAsString());
                String id = interaction.get("id").getAsString();
                String originalKey =
                        interaction.get("original_key").getAsString();
                String type = interaction.get("type").getAsString();
                String textKey =
                        interaction.get("text_key").getAsString();
                List<MapInteractionDefinition.Box> expected =
                        expectedApprovalRegions(interaction);
                MapInteractionDefinition definition =
                        decodeDefinition(id);
                MapInteractionDefinition.Branch branch =
                        definition.branches().getFirst();

                assertEquals(expected, definition.boxes(), id);
                assertEquals(location, definition.location(), id);
                assertEquals(
                        ResourceLocation.fromNamespaceAndPath(
                                "stardewcraft", "stardew_valley"),
                        definition.dimension(),
                        id);
                assertTrue(definition.blocks().isEmpty(), id);
                assertTrue(definition.blockTags().isEmpty(), id);
                assertEquals(map, definition.source().map(), id);
                assertEquals(
                        ("letter".equals(type) ? "Letter" : "Message")
                                + " \"" + originalKey + "\"",
                        definition.source().tileAction(),
                        id);
                assertTrue(definition.showsReadHint(branch), id);

                if ("letter".equals(type)) {
                    assertTrue(branch.messages().isEmpty(), id);
                    assertEquals(
                            ResourceLocation.fromNamespaceAndPath(
                                    "stardewcraft", "open_letter"),
                            branch.action().type(),
                            id);
                    assertEquals(
                            MapInteractionDefinition.Hint.READ,
                            definition.hint(),
                            id);
                    JsonObject rawDefinition = readJson(
                            "src/main/resources/data/stardewcraft/"
                                    + "map_interactions/" + id + ".json");
                    assertEquals(
                            textKey,
                            rawDefinition.getAsJsonArray("branches")
                                    .get(0).getAsJsonObject()
                                    .getAsJsonObject("action")
                                    .getAsJsonObject("data")
                                    .get("text").getAsString(),
                            id);
                } else {
                    assertEquals(null, branch.action(), id);
                    assertEquals(1, branch.messages().size(), id);
                    MapInteractionDefinition.Message message =
                            branch.messages().getFirst();
                    assertEquals(textKey, message.translationKey(), id);
                    assertFalse(message.fallback().isBlank(), id);
                }
                definitionCount++;
                endpointCount += interaction
                        .getAsJsonArray("points").size();
                regionCount += expected.size();
            }
        }

        assertEquals(32, definitionCount);
        assertEquals(40, endpointCount);
        assertEquals(32, regionCount);
    }

    @Test
    void pairedApprovalEndpointsProduceInclusiveCuboids()
            throws IOException {
        MapInteractionDefinition.Box furnace =
                decodeDefinition("blacksmith_5").boxes().getFirst();
        assertEquals(
                box(110, 46, 19, 113, 48, 20),
                furnace);
        assertTrue(furnace.contains(new BlockPos(112, 47, 20)));
        assertFalse(furnace.contains(new BlockPos(114, 47, 20)));

        for (String id : List.of(
                "hospital_7",
                "leah_house_3",
                "leah_house_4",
                "leah_house_5",
                "fish_shop_1",
                "blacksmith_2",
                "blacksmith_5",
                "elliott_house_5")) {
            JsonObject trigger = readJson(
                    "src/main/resources/data/stardewcraft/"
                            + "map_interactions/" + id + ".json")
                    .getAsJsonObject("trigger");
            assertFalse(trigger.has("positions"), id);
            assertEquals(
                    1,
                    trigger.getAsJsonArray("boxes").size(),
                    id);
        }
    }

    @Test
    void explicitlyOmittedInteractionsRemainAbsent() {
        assertDefinitionAbsent("hospital_1");
        assertDefinitionAbsent("hospital_2");
        assertDefinitionAbsent("leah_house_2");
        assertDefinitionAbsent("blacksmith_3");
        assertDefinitionAbsent("blacksmith_6");
        assertDefinitionAbsent("blacksmith_8");
    }

    @Test
    void phaseThreeDefinitionsMatchEveryAuthorApprovedRegion()
            throws IOException {
        JsonObject manifest = readJson(
                "scripts/data/map_interaction_approvals_phase3.json");
        assertEquals(2, manifest.get("format").getAsInt());
        assertEquals(
                "phase3_approval_image_order",
                manifest.get("source_numbering").getAsString());
        assertEquals(
                "inclusive_cuboid",
                manifest.getAsJsonObject("geometry")
                        .get("paired_points").getAsString());

        int definitionCount = 0;
        int endpointCount = 0;
        int regionCount = 0;
        int letterCount = 0;
        int npcMessageCount = 0;
        for (var rawBatch : manifest.getAsJsonArray("batches")) {
            JsonObject batch = rawBatch.getAsJsonObject();
            ResourceLocation location = ResourceLocation.parse(
                    batch.get("location").getAsString());
            String map = batch.get("map").getAsString();
            for (var rawInteraction :
                    batch.getAsJsonArray("interactions")) {
                JsonObject interaction =
                        rawInteraction.getAsJsonObject();
                String id = interaction.get("id").getAsString();
                String type = interaction.get("type").getAsString();
                MapInteractionDefinition definition =
                        decodeDefinition(id);
                MapInteractionDefinition.Branch branch =
                        definition.branches().getFirst();
                JsonObject rawDefinition = readJson(
                        "src/main/resources/data/stardewcraft/"
                                + "map_interactions/" + id + ".json");

                List<MapInteractionDefinition.Box> expected =
                        expectedApprovalRegions(interaction);
                assertEquals(expected, definition.boxes(), id);
                assertEquals(location, definition.location(), id);
                assertEquals(
                        ResourceLocation.fromNamespaceAndPath(
                                "stardewcraft", "stardew_valley"),
                        definition.dimension(),
                        id);
                assertEquals(map, definition.source().map(), id);
                assertTrue(definition.showsReadHint(branch), id);

                if ("letter".equals(type)) {
                    letterCount++;
                    assertEquals(
                            ResourceLocation.fromNamespaceAndPath(
                                    "stardewcraft", "open_letter"),
                            branch.action().type(),
                            id);
                    assertEquals(
                            MapInteractionDefinition.Hint.READ,
                            definition.hint(),
                            id);
                    assertEquals(
                            interaction.get("text_key").getAsString(),
                            rawDefinition.getAsJsonArray("branches")
                                    .get(0).getAsJsonObject()
                                    .getAsJsonObject("action")
                                    .getAsJsonObject("data")
                                    .get("text").getAsString(),
                            id);
                } else if ("npc_message".equals(type)) {
                    npcMessageCount++;
                    assertEquals(
                            ResourceLocation.fromNamespaceAndPath(
                                    "stardewcraft", "npc_message"),
                            branch.action().type(),
                            id);
                    assertEquals(
                            interaction.get("original_action")
                                    .getAsString(),
                            definition.source().tileAction(),
                            id);
                    assertEquals(
                            MapInteractionDefinition.Hint.READ,
                            definition.hint(),
                            id);
                    JsonObject actionData =
                            rawDefinition.getAsJsonArray("branches")
                                    .get(0).getAsJsonObject()
                                    .getAsJsonObject("action")
                                    .getAsJsonObject("data");
                    assertEquals(
                            interaction.get("npc").getAsString(),
                            actionData.get("npc").getAsString(),
                            id);
                    assertEquals(14.0D,
                            actionData.get("radius").getAsDouble(),
                            id);
                    assertEquals(4,
                            actionData.get("vertical_radius").getAsInt(),
                            id);
                    assertTrue(
                            actionData.get("announce_snooping")
                                    .getAsBoolean(),
                            id);
                    assertEquals(
                            interaction.getAsJsonObject("nearby")
                                    .get("text_key").getAsString(),
                            actionData.getAsJsonObject("nearby")
                                    .get("translate").getAsString(),
                            id);
                    assertEquals(
                            interaction.getAsJsonObject("fallback")
                                    .get("text_key").getAsString(),
                            actionData.getAsJsonObject("fallback")
                                    .get("translate").getAsString(),
                            id);
                } else {
                    assertEquals(null, branch.action(), id);
                    assertEquals(1, branch.messages().size(), id);
                    assertEquals(
                            interaction.get("text_key").getAsString(),
                            branch.messages().getFirst()
                                    .translationKey(),
                            id);
                    assertEquals(
                            "Message \""
                                    + interaction.get("original_key")
                                            .getAsString()
                                    + "\"",
                            definition.source().tileAction(),
                            id);
                }

                definitionCount++;
                endpointCount += interaction
                        .getAsJsonArray("points").size();
                regionCount += expected.size();
            }
        }
        assertEquals(72, definitionCount);
        assertEquals(101, endpointCount);
        assertEquals(72, regionCount);
        assertEquals(4, letterCount);
        assertEquals(4, npcMessageCount);
    }

    @Test
    void phaseThreeUnapprovedAndMergedDuplicatesRemainAbsent() {
        for (String id : List.of(
                "seed_shop_3",
                "seed_shop_9",
                "seed_shop_10",
                "sam_house_12",
                "josh_house_6",
                "josh_house_9",
                "josh_house_10",
                "haley_house_3",
                "haley_house_7",
                "animal_shop_2",
                "animal_shop_4",
                "science_house_7",
                "science_house_11")) {
            assertDefinitionAbsent(id);
        }
    }

    private static void assertBoxes(
            String path,
            List<MapInteractionDefinition.Box> expected
    ) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", path);
        MapInteractionDefinition definition =
                MapInteractionRegistry.decode(
                        id,
                        readJson(
                                "src/main/resources/data/stardewcraft/"
                                        + "map_interactions/" + path
                                        + ".json"));
        assertEquals(expected, definition.boxes(), path);
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "trailer"),
                definition.location(),
                path);
        assertEquals(
                "Message \"Trailer."
                        + path.substring(path.length() - 1) + "\"",
                definition.source().tileAction(),
                path);
        int message = Integer.parseInt(
                path.substring(path.length() - 1));
        MapInteractionDefinition.Message authoredMessage =
                definition.branches().getFirst()
                        .messages().getFirst();
        assertEquals(
                "stardewcraft.strings_from_maps.trailer." + message,
                authoredMessage.translationKey(),
                path);
        assertFalse(authoredMessage.fallback().isBlank(), path);
        assertTrue(definition.showsReadHint(
                definition.branches().getFirst()), path);
    }

    private static MapInteractionDefinition decodeDefinition(
            String path
    ) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", path);
        return MapInteractionRegistry.decode(
                id,
                readJson(
                        "src/main/resources/data/stardewcraft/"
                                + "map_interactions/" + path + ".json"));
    }

    private static List<MapInteractionDefinition.Box>
            expectedApprovalRegions(JsonObject interaction) {
        List<MapInteractionDefinition.Box> singles =
                new ArrayList<>();
        Map<String, BlockPos[]> paired =
                new LinkedHashMap<>();
        for (var rawPoint :
                interaction.getAsJsonArray("points")) {
            JsonObject point = rawPoint.getAsJsonObject();
            String name = point.get("name").getAsString();
            BlockPos pos = new BlockPos(
                    point.get("x").getAsInt(),
                    point.get("y").getAsInt(),
                    point.get("z").getAsInt());
            if (name.endsWith("_1") || name.endsWith("_2")) {
                String base = name.substring(
                        0, name.length() - 2);
                int endpoint = name.endsWith("_1") ? 0 : 1;
                paired.computeIfAbsent(
                        base, ignored -> new BlockPos[2])
                        [endpoint] = pos;
            } else {
                singles.add(new MapInteractionDefinition.Box(
                        pos, pos));
            }
        }
        for (Map.Entry<String, BlockPos[]> entry :
                paired.entrySet()) {
            BlockPos[] endpoints = entry.getValue();
            assertTrue(
                    endpoints[0] != null
                            && endpoints[1] != null,
                    interaction.get("id").getAsString()
                            + " incomplete pair "
                            + entry.getKey());
            singles.add(box(
                    Math.min(endpoints[0].getX(),
                            endpoints[1].getX()),
                    Math.min(endpoints[0].getY(),
                            endpoints[1].getY()),
                    Math.min(endpoints[0].getZ(),
                            endpoints[1].getZ()),
                    Math.max(endpoints[0].getX(),
                            endpoints[1].getX()),
                    Math.max(endpoints[0].getY(),
                            endpoints[1].getY()),
                    Math.max(endpoints[0].getZ(),
                            endpoints[1].getZ())));
        }
        return List.copyOf(singles);
    }

    private static void assertDefinitionAbsent(String path) {
        assertFalse(Files.exists(PROJECT.resolve(
                "src/main/resources/data/stardewcraft/"
                        + "map_interactions/" + path + ".json")),
                path);
    }

    private static MapInteractionDefinition.Box box(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        return new MapInteractionDefinition.Box(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ));
    }

    private static JsonObject readJson(String relative)
            throws IOException {
        return JsonParser.parseString(
                Files.readString(PROJECT.resolve(relative)))
                .getAsJsonObject();
    }
}
