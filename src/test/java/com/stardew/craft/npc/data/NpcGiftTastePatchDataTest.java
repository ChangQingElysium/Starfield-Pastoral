package com.stardew.craft.npc.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.npc.StardewNpcGiftTastePatchDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcGiftTastePatchDataTest {
    @Test
    void patchRemovesThenAddsWithoutReplacingNpcDocument() {
        JsonObject abigail = new JsonObject();
        abigail.addProperty("npc_id", "abigail");
        abigail.addProperty("loved_msg", "Thanks!");
        abigail.add("loved", array("minecraft:apple"));
        Map<String, JsonObject> tastes =
                new LinkedHashMap<>(Map.of("abigail", abigail));
        StardewNpcGiftTastePatchDefinition patch =
                new StardewNpcGiftTastePatchDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                "stardewcraft", "abigail"),
                        100,
                        true,
                        Map.of("loved", List.of(id("diamond"))),
                        Map.of("loved", List.of(id("apple"))));
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

        NpcDataManager.ReloadListener.applyTastePatches(
                tastes,
                List.of(Map.entry(id("patch"), patch)),
                diagnostics);

        assertTrue(diagnostics.isEmpty());
        JsonObject result = tastes.get("abigail");
        assertEquals("Thanks!", result.get("loved_msg").getAsString());
        assertEquals(
                List.of("minecraft:diamond"),
                strings(result.getAsJsonArray("loved")));
    }

    @Test
    void requiredMissingTargetProducesAtomicCandidateError() {
        StardewNpcGiftTastePatchDefinition patch =
                new StardewNpcGiftTastePatchDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                "missing_addon", "npc"),
                        0,
                        true,
                        Map.of("liked", List.of(id("apple"))),
                        Map.of());
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

        NpcDataManager.ReloadListener.applyTastePatches(
                new LinkedHashMap<>(),
                List.of(Map.entry(id("patch"), patch)),
                diagnostics);

        assertEquals(1, diagnostics.size());
        assertEquals(
                DefinitionDiagnostic.Severity.ERROR,
                diagnostics.getFirst().severity());
    }

    @Test
    void codecRejectsUnknownCategoryAndAmbiguousItemOperation() {
        var unknown = StardewNpcGiftTastePatchDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "npc": "stardewcraft:abigail",
                          "add": {
                            "favorite": ["minecraft:apple"]
                          }
                        }
                        """));
        var overlap = StardewNpcGiftTastePatchDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "npc": "stardewcraft:abigail",
                          "add": {
                            "loved": ["minecraft:apple"]
                          },
                          "remove": {
                            "loved": ["minecraft:apple"]
                          }
                        }
                        """));

        assertTrue(unknown.error().isPresent());
        assertTrue(overlap.error().isPresent());
        assertFalse(unknown.result().isPresent());
        assertFalse(overlap.result().isPresent());
    }

    @Test
    void duplicateLogicalDocumentKeepsFirstCandidateAndReportsError() {
        Map<String, JsonObject> documents = new LinkedHashMap<>();
        Map<String, ResourceLocation> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        JsonObject first = new JsonObject();
        first.addProperty("value", "first");
        JsonObject second = new JsonObject();
        second.addProperty("value", "second");

        NpcDataManager.ReloadListener.putUniqueDocument(
                "dialogue",
                "stardewcraft_test:keeper",
                first,
                ResourceLocation.fromNamespaceAndPath(
                        "first_addon", "dialogue/keeper"),
                documents,
                sources,
                diagnostics);
        NpcDataManager.ReloadListener.putUniqueDocument(
                "dialogue",
                "stardewcraft_test:keeper",
                second,
                ResourceLocation.fromNamespaceAndPath(
                        "second_addon", "dialogue/keeper"),
                documents,
                sources,
                diagnostics);

        assertEquals(
                "first",
                documents.get("stardewcraft_test:keeper")
                        .get("value").getAsString());
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message()
                .contains("Duplicate NPC dialogue logical ID"));
    }

    @Test
    void definitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewNpcGiftTastePatchDefinition> previous =
                NpcGiftTastePatchData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("patch_low");
            ResourceLocation highId = id("patch_high");
            StardewNpcGiftTastePatchDefinition low =
                    patch(1, "abigail");
            StardewNpcGiftTastePatchDefinition high =
                    patch(100, "leah");
            Map<ResourceLocation, StardewNpcGiftTastePatchDefinition>
                    definitions = Map.of(
                    lowId, low, highId, high);

            NpcGiftTastePatchData.apply(
                    definitions, sources(definitions), List.of());
            NpcGiftTastePatchData.Catalog accepted =
                    NpcGiftTastePatchData.catalog();
            assertSame(accepted.definitions(),
                    NpcGiftTastePatchData.snapshot());
            assertEquals(List.of(lowId, highId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            NpcGiftTastePatchData.apply(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted,
                    NpcGiftTastePatchData.catalog());
        } finally {
            NpcGiftTastePatchData.apply(
                    previous, sources(previous), List.of());
        }
    }

    private static StardewNpcGiftTastePatchDefinition patch(
            int priority,
            String npc
    ) {
        return new StardewNpcGiftTastePatchDefinition(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", npc),
                priority,
                false,
                Map.of("liked", List.of(id("apple"))),
                Map.of());
    }

    private static <T> Map<ResourceLocation, String> sources(
            Map<ResourceLocation, T> definitions
    ) {
        return definitions.keySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        id -> id, id -> "{}"));
    }

    private static JsonArray array(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream()
                .map(value -> value.getAsString())
                .toList();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
