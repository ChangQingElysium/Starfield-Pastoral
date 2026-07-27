package com.stardew.craft.farm;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachmentKeys;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmLayoutDataRegistryTest {
    @Test
    void completeDataLayoutDecodesIntoCanonicalRegistration() {
        ResourceLocation id = id("orchard");
        var registration = FarmLayoutDataRegistry.decode(
                id, JsonParser.parseString(validJson()), null);

        assertEquals(id, registration.layout().id());
        assertEquals(3, registration.version());
        assertTrue(registration.layout().selectable());
        assertEquals("data_layout_test:farm/orchard.schem",
                registration.layout().schematic().toString());
        assertEquals("minecraft:plains",
                registration.layout().biomeId());
        assertEquals(3, registration.configurationFields().size());
        assertEquals(1, registration.attachments().size());
        assertTrue(registration.attachments().getFirst().tags()
                .contains(StardewFarmLayoutAttachmentKeys.FESTIVAL));
    }

    @Test
    void dataPublicationAddsLegacyPointsAndReplacesAtomically() {
        Map<ResourceLocation, com.stardew.craft.api.v1.farm
                .StardewFarmLayoutRegistration> previous =
                new LinkedHashMap<>(
                        StardewFarmLayoutRegistry.dataRegistrations());
        ResourceLocation id = id("published");
        var registration = FarmLayoutDataRegistry.decode(
                id, JsonParser.parseString(validJson()), null);
        try {
            var applied = FarmLayoutDataRegistry.reload(
                    Map.of(id, JsonParser.parseString(validJson())),
                    null);
            assertFalse(applied.rejected());
            var published = StardewFarmLayoutRegistry
                    .findRegistration(id).orElseThrow();
            assertTrue(published.findAttachment(
                    StardewFarmLayoutAttachmentKeys.SPAWN).isPresent());
            assertTrue(published.findAttachment(
                    id("festival_stage")).isPresent());

            assertThrows(IllegalStateException.class,
                    () -> StardewFarmLayoutRegistry.publishData(
                            Map.of(
                                    StardewFarmLayoutRegistry.builtinId(
                                            FarmType.STANDARD),
                                    registration)));
            assertTrue(StardewFarmLayoutRegistry
                    .findRegistration(id).isPresent());

            var rejected = FarmLayoutDataRegistry.reload(
                    Map.of(id, JsonParser.parseString(
                            validJson().replace(
                                    "\"format\": 1",
                                    "\"format\": 99"))),
                    null);
            assertTrue(rejected.rejected());
            assertEquals(1, rejected.activeCount());
            assertTrue(StardewFarmLayoutRegistry
                    .findRegistration(id).isPresent());
        } finally {
            StardewFarmLayoutRegistry.publishData(previous);
        }
    }

    @Test
    void malformedFormatAndInvalidDefaultsAreRejected() {
        String wrongFormat = validJson().replace(
                "\"format\": 1", "\"format\": 2");
        assertThrows(IllegalArgumentException.class,
                () -> FarmLayoutDataRegistry.decode(
                        id("wrong_format"),
                        JsonParser.parseString(wrongFormat),
                        null));

        String invalidDefault = validJson().replace(
                "\"default\": 2,\n"
                        + "      \"minimum\": 0,\n"
                        + "      \"maximum\": 4",
                "\"default\": 9,\n"
                        + "      \"minimum\": 0,\n"
                        + "      \"maximum\": 4");
        assertThrows(IllegalArgumentException.class,
                () -> FarmLayoutDataRegistry.decode(
                        id("invalid_default"),
                        JsonParser.parseString(invalidDefault),
                        null));

        String misspelledField = validJson().replace(
                "\"origin_y\": 12",
                "\"origin_y\": 12,\n  \"orgin_y\": 12");
        var unknown = assertThrows(IllegalArgumentException.class,
                () -> FarmLayoutDataRegistry.decode(
                        id("unknown_field"),
                        JsonParser.parseString(misspelledField),
                        null));
        assertTrue(unknown.getMessage().contains("orgin_y"));

        String wrongStructureType = validJson().replace(
                "farm/orchard.schem", "farm/orchard.txt");
        assertThrows(IllegalArgumentException.class,
                () -> FarmLayoutDataRegistry.decode(
                        id("wrong_structure_type"),
                        JsonParser.parseString(wrongStructureType),
                        null));
    }

    @Test
    void missingStructureIsResolvedThroughActiveDataPackResources() {
        AtomicReference<ResourceLocation> requested =
                new AtomicReference<>();
        ResourceManager resources = new ResourceManager() {
            @Override
            public Optional<Resource> getResource(
                    ResourceLocation location
            ) {
                requested.set(location);
                return Optional.empty();
            }

            @Override
            public Set<String> getNamespaces() {
                return Set.of();
            }

            @Override
            public List<Resource> getResourceStack(
                    ResourceLocation location
            ) {
                return List.of();
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(
                    String path,
                    Predicate<ResourceLocation> filter
            ) {
                return Map.of();
            }

            @Override
            public Map<ResourceLocation, List<Resource>>
            listResourceStacks(
                    String path,
                    Predicate<ResourceLocation> filter
            ) {
                return Map.of();
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.empty();
            }
        };

        var failure = assertThrows(IllegalArgumentException.class,
                () -> FarmLayoutDataRegistry.decode(
                        id("missing_structure"),
                        JsonParser.parseString(validJson()),
                        resources));
        assertEquals(
                "data_layout_test:structures/farm/orchard.schem",
                requested.get().toString());
        assertTrue(failure.getMessage().contains(
                "data/data_layout_test/structures/farm/orchard.schem"));
    }

    private static String validJson() {
        return """
                {
                  "format": 1,
                  "version": 3,
                  "selectable": true,
                  "display_name": {"translate": "farm.data_layout_test.orchard"},
                  "description": "A data-defined layout",
                  "icon": "data_layout_test:textures/gui/orchard.png",
                  "schematic": "farm/orchard.schem",
                  "origin_y": 12,
                  "size": [40, 16, 48],
                  "spawn": {"offset": [15, 4, 18], "yaw": 45},
                  "greenhouse": [12, 4, 9],
                  "totem": [18, 4, 16],
                  "entries": {
                    "south": {
                      "teleport": [20, 4, 30],
                      "yaw": 90,
                      "exit_min": [19, 4, 31],
                      "exit_max": [21, 6, 31]
                    },
                    "east": {
                      "teleport": [2, 4, 15],
                      "yaw": -90,
                      "exit_min": [1, 4, 14],
                      "exit_max": [1, 6, 16]
                    },
                    "west": {
                      "teleport": [10, 4, 38],
                      "yaw": 180,
                      "exit_min": [9, 4, 39],
                      "exit_max": [11, 6, 39]
                    }
                  },
                  "biome": "minecraft:plains",
                  "forage": {"min": [3, 4, 3], "max": [8, 6, 8]},
                  "configuration": [
                    {
                      "id": "rain",
                      "type": "boolean",
                      "label": "Rain",
                      "default": true
                    },
                    {
                      "id": "cabins",
                      "type": "integer",
                      "label": "Cabins",
                      "default": 2,
                      "minimum": 0,
                      "maximum": 4
                    },
                    {
                      "id": "theme",
                      "type": "choice",
                      "label": "Theme",
                      "default": "spring",
                      "choices": ["spring", "autumn"]
                    }
                  ],
                  "attachments": [
                    {
                      "id": "festival_stage",
                      "offset": [7, 5, 9],
                      "yaw": 180,
                      "tags": ["stardewcraft:festival"]
                    }
                  ]
                }
                """;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "data_layout_test", path);
    }
}
