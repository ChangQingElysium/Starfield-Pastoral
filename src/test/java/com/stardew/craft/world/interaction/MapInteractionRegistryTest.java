package com.stardew.craft.world.interaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapInteractionRegistryTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));

    @BeforeAll
    static void registerBuiltins() {
        com.stardew.craft.api.v1.internal.BuiltinApiTypes.bootstrap();
        BuiltinMapInteractionActions.bootstrap();
    }

    @Test
    void decodesAllBuiltinDefinitions() throws IOException {
        Path directory = PROJECT.resolve(
                "src/main/resources/data/stardewcraft/map_interactions");
        List<Path> files;
        try (var stream = Files.walk(directory)) {
            files = stream.filter(path ->
                            path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertTrue(files.size() >= 4,
                "Expected the initial source-backed interaction batch");
        for (Path file : files) {
            var json = JsonParser.parseString(Files.readString(file));
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft",
                    directory.relativize(file).toString()
                            .replace('\\', '/')
                            .replaceFirst("\\.json$", ""));
            MapInteractionDefinition definition =
                    MapInteractionRegistry.decode(id, json);
            assertEquals(id, definition.id());
            assertTrue(!definition.boxes().isEmpty());
            assertTrue(!definition.branches().isEmpty());
            assertTrue(!definition.source().map().isBlank());
            assertTrue(!definition.source().tileAction().isBlank());
        }
    }

    @Test
    void rejectsFractionalAndOutOfBoundsCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> decodeLiteralAt("[1.5, 2, 3]"));
        assertThrows(
                IllegalArgumentException.class,
                () -> decodeLiteralAt("[30000001, 2, 3]"));
        assertThrows(
                IllegalArgumentException.class,
                () -> decodeLiteralAt("[-2147483648, 2, 0]"));
        JsonObject oversizedBox = JsonParser.parseString("""
                {
                  "format": 1,
                  "trigger": {
                    "boxes": [
                      {
                        "min": [-3000, 2, 0],
                        "max": [3000, 2, 0]
                      }
                    ],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      "messages": [{"literal": "hello"}]
                    }
                  ]
                }
                """).getAsJsonObject();
        assertThrows(
                IllegalArgumentException.class,
                () -> MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "test", "oversized_box"),
                        oversizedBox));
    }

    @Test
    void unknownBuiltinActionPayloadIsARegularDecodeError() {
        JsonObject root = JsonParser.parseString("""
                {
                  "format": 1,
                  "trigger": {
                    "positions": [[1, 2, 3]],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      "action": {
                        "type": "stardewcraft:mr_qi_anchor",
                        "data": {"anchor": "not_a_real_anchor"}
                      }
                    }
                  ]
                }
                """).getAsJsonObject();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "test", "bad_anchor"),
                        root));
        assertTrue(exception.getMessage().contains(
                "Unknown Mr Qi map anchor"));
    }

    @Test
    void npcMessageActionIsTypedAndRejectsAmbiguousText() {
        JsonObject valid = JsonParser.parseString("""
                {
                  "format": 1,
                  "hint": "read",
                  "trigger": {
                    "positions": [[1, 2, 3]],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      "action": {
                        "type": "stardewcraft:npc_message",
                        "data": {
                          "npc": "Abigail",
                          "nearby": {
                            "translate": "example.abigail.snoop",
                            "fallback": "That's private!"
                          },
                          "fallback": {
                            "literal": "A sword is hidden under the clothes."
                          },
                          "announce_snooping": true
                        }
                      }
                    }
                  ]
                }
                """).getAsJsonObject();
        MapInteractionDefinition definition =
                MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "test", "npc_message"),
                        valid);
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "npc_message"),
                definition.branches().getFirst().action().type());
        assertTrue(definition.showsReadHint(
                definition.branches().getFirst()));

        JsonObject invalid = valid.deepCopy();
        invalid.getAsJsonArray("branches")
                .get(0).getAsJsonObject()
                .getAsJsonObject("action")
                .getAsJsonObject("data")
                .getAsJsonObject("nearby")
                .addProperty("literal", "conflicting literal");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "test", "ambiguous_npc_message"),
                        invalid));
        assertTrue(exception.getMessage().contains(
                "exactly one of translate or literal"));
    }

    @Test
    void reloadAppliesValidDefinitionsAndRetainsInvalidPreviousIds() {
        ResourceLocation validId =
                ResourceLocation.fromNamespaceAndPath("test", "valid");
        ResourceLocation badId =
                ResourceLocation.fromNamespaceAndPath("test", "bad");
        JsonObject valid = literalDefinition("[1, 2, 3]");
        JsonObject invalid = JsonParser.parseString(
                "{\"format\":1}").getAsJsonObject();
        try {
            MapInteractionRegistry.publish(Map.of());
            var first = MapInteractionRegistry.applyObjects(
                    Map.of(validId, valid, badId, invalid));
            assertTrue(first.partial());
            assertEquals(List.of(validId),
                    MapInteractionRegistry.all().stream()
                            .map(MapInteractionDefinition::id)
                            .toList());

            var second = MapInteractionRegistry.applyObjects(
                    Map.of(validId, invalid));
            assertTrue(second.partial());
            assertEquals(List.of(validId),
                    MapInteractionRegistry.all().stream()
                            .map(MapInteractionDefinition::id)
                            .toList());

            var empty = MapInteractionRegistry.applyObjects(Map.of());
            assertFalse(empty.partial());
            assertTrue(MapInteractionRegistry.all().isEmpty());
        } finally {
            MapInteractionRegistry.publish(Map.of());
        }
    }

    @Test
    void rejectsUnknownFieldsInsteadOfIgnoringTypos() {
        JsonObject root = JsonParser.parseString("""
                {
                  "format": 1,
                  "source": {
                    "map": "Town",
                    "tile_action": "Message \\"Town.1\\""
                  },
                  "trigger": {
                    "positions": [[1, 2, 3]],
                    "hand": "main_hand",
                    "postions": [[4, 5, 6]]
                  },
                  "branches": [
                    {
                      "messages": [{"literal": "hello"}]
                    }
                  ]
                }
                """).getAsJsonObject();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "test", "typo"),
                        root));
        assertTrue(exception.getMessage().contains("postions"));
    }

    @Test
    void readHintCanBeAutomaticForcedOrDisabled() {
        MapInteractionDefinition automatic =
                decodeWithHint(null, true);
        assertTrue(automatic.showsReadHint(
                automatic.branches().getFirst()));

        MapInteractionDefinition disabled =
                decodeWithHint("none", true);
        assertFalse(disabled.showsReadHint(
                disabled.branches().getFirst()));

        MapInteractionDefinition forced =
                decodeWithHint("read", false);
        assertTrue(forced.showsReadHint(
                forced.branches().getFirst()));

        assertThrows(
                IllegalArgumentException.class,
                () -> decodeWithHint("sparkle", true));
    }

    @Test
    void addonDefinitionMayOmitVanillaSourceMetadata() {
        JsonObject root = JsonParser.parseString("""
                {
                  "format": 1,
                  "trigger": {
                    "positions": [[1, 2, 3]],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      "messages": [{"literal": "addon text"}]
                    }
                  ]
                }
                """).getAsJsonObject();
        MapInteractionDefinition definition =
                MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "addon", "original_interaction"),
                        root);
        assertTrue(definition.source().map().isEmpty());
        assertTrue(definition.source().tileAction().isEmpty());
    }

    @Test
    void branchEffectsReuseTheSharedStardewActionCodec() {
        JsonObject root = JsonParser.parseString("""
                {
                  "format": 1,
                  "trigger": {
                    "positions": [[1, 2, 3]],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      "effects": [
                        {
                          "type": "stardewcraft:add_item",
                          "data": {
                            "item": "minecraft:apple",
                            "count": 1
                          }
                        }
                      ],
                      "messages": [{"literal": "done"}]
                    }
                  ]
                }
                """).getAsJsonObject();
        MapInteractionDefinition definition =
                MapInteractionRegistry.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                "addon", "shared_action"),
                        root);
        assertEquals(1,
                definition.branches().getFirst().effects().size());
    }

    @Test
    void publishUsesStablePriorityThenIdOrder() {
        MapInteractionDefinition low = definition(
                "test:z_low", 0);
        MapInteractionDefinition first = definition(
                "test:a_high", 10);
        MapInteractionDefinition second = definition(
                "test:b_high", 10);
        LinkedHashMap<ResourceLocation, MapInteractionDefinition> values =
                new LinkedHashMap<>();
        values.put(low.id(), low);
        values.put(second.id(), second);
        values.put(first.id(), first);

        MapInteractionRegistry.publish(values);

        assertEquals(
                List.of(first.id(), second.id(), low.id()),
                MapInteractionRegistry.all().stream()
                        .map(MapInteractionDefinition::id)
                        .toList());
        assertEquals(
                List.of(first.id(), second.id(), low.id()),
                MapInteractionRegistry.at(
                                ResourceLocation.fromNamespaceAndPath(
                                        "test", "dimension"),
                                new net.minecraft.core.BlockPos(1, 2, 3))
                        .stream()
                        .map(MapInteractionDefinition::id)
                        .toList());
    }

    private static MapInteractionDefinition definition(
            String rawId,
            int priority
    ) {
        ResourceLocation id = ResourceLocation.parse(rawId);
        var pos = new net.minecraft.core.BlockPos(1, 2, 3);
        return new MapInteractionDefinition(
                id,
                priority,
                null,
                null,
                List.of(new MapInteractionDefinition.Box(pos, pos)),
                java.util.Set.of(),
                java.util.Set.of(),
                List.of(new MapInteractionDefinition.Branch(
                        "default",
                        List.of(),
                        List.of(),
                        List.of(new MapInteractionDefinition.Message(
                                null, null, "hello")),
                        null)),
                MapInteractionDefinition.Hint.AUTO,
                new MapInteractionDefinition.Source(
                        "1.6", "Town", "Message \"Town.1\"", ""));
    }

    private static MapInteractionDefinition decodeWithHint(
            String hint,
            boolean messages
    ) {
        String hintField = hint == null
                ? ""
                : "\"hint\": \"" + hint + "\",";
        String branchContent = messages
                ? "\"messages\": [{\"literal\": \"hello\"}]"
                : """
                  "action": {
                    "type": "stardewcraft:mr_qi_anchor",
                    "data": {"anchor": "railroad_box"}
                  }
                  """;
        JsonObject root = JsonParser.parseString("""
                {
                  "format": 1,
                  %s
                  "trigger": {
                    "positions": [[1, 2, 3]],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      %s
                    }
                  ]
                }
                """.formatted(hintField, branchContent))
                .getAsJsonObject();
        return MapInteractionRegistry.decode(
                ResourceLocation.fromNamespaceAndPath(
                        "test",
                        "hint_" + (hint == null ? "auto" : hint)),
                root);
    }

    private static MapInteractionDefinition decodeLiteralAt(
            String position
    ) {
        return MapInteractionRegistry.decode(
                ResourceLocation.fromNamespaceAndPath("test", "coordinate"),
                literalDefinition(position));
    }

    private static JsonObject literalDefinition(String position) {
        return JsonParser.parseString("""
                {
                  "format": 1,
                  "trigger": {
                    "positions": [%s],
                    "hand": "main_hand"
                  },
                  "branches": [
                    {
                      "messages": [{"literal": "hello"}]
                    }
                  ]
                }
                """.formatted(position)).getAsJsonObject();
    }
}
