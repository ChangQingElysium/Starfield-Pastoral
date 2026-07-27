package com.stardew.craft.shop;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.loot.StardewGeodeDropDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GeodeDropDataAtomicSnapshotTest {
    private static final Gson GSON = new Gson();

    @Test
    void definitionsAndInputRoutingPublishTogether() {
        Map<ResourceLocation, StardewGeodeDropDefinition> previous =
                GeodeDropData.snapshot().definitions();
        try {
            ResourceLocation firstId =
                    ResourceLocation.fromNamespaceAndPath(
                            "geode_snapshot_test", "first");
            ResourceLocation firstInput =
                    BuiltInRegistries.ITEM.getKey(Items.CLAY_BALL);
            StardewGeodeDropDefinition first =
                    definition(firstInput, Items.DIAMOND);

            GeodeDropData.applyCandidate(
                    Map.of(firstId, first),
                    sources(Map.of(firstId, first)),
                    List.of());

            GeodeDropData.Catalog accepted =
                    GeodeDropData.catalog();
            assertCoherent(accepted);
            assertEquals(firstId,
                    GeodeDropData.definitionFor(
                            new ItemStack(Items.CLAY_BALL)));

            ResourceLocation duplicateId =
                    ResourceLocation.fromNamespaceAndPath(
                            "geode_snapshot_test", "duplicate");
            StardewGeodeDropDefinition duplicate =
                    definition(firstInput, Items.EMERALD);
            Map<ResourceLocation, StardewGeodeDropDefinition> invalid =
                    new LinkedHashMap<>();
            invalid.put(firstId, first);
            invalid.put(duplicateId, duplicate);
            GeodeDropData.applyCandidate(
                    invalid, sources(invalid), List.of());

            assertSame(accepted, GeodeDropData.catalog(),
                    "duplicate geode input replaced the accepted catalog");
            assertCoherent(accepted);
        } finally {
            GeodeDropData.applyCandidate(
                    previous, sources(previous),
                    List.<DefinitionDiagnostic>of());
        }
    }

    private static StardewGeodeDropDefinition definition(
            ResourceLocation input,
            net.minecraft.world.item.Item output
    ) {
        String json = """
                {
                  "inputs": ["%s"],
                  "entries": [{
                    "query": {
                      "type": "stardewcraft:item",
                      "data": {"item": "%s"}
                    }
                  }]
                }
                """.formatted(
                input,
                BuiltInRegistries.ITEM.getKey(output));
        return StardewGeodeDropDefinition.CODEC
                .parse(JsonOps.INSTANCE,
                        JsonParser.parseString(json))
                .getOrThrow();
    }

    private static Map<ResourceLocation, String> sources(
            Map<ResourceLocation, StardewGeodeDropDefinition> definitions
    ) {
        Map<ResourceLocation, String> sources =
                new LinkedHashMap<>();
        definitions.forEach((id, definition) ->
                sources.put(id,
                        StardewGeodeDropDefinition.CODEC
                                .encodeStart(JsonOps.INSTANCE, definition)
                                .map(GSON::toJson)
                                .getOrThrow()));
        return sources;
    }

    private static void assertCoherent(
            GeodeDropData.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                GeodeDropData.snapshot());
        catalog.inputToDefinition().forEach((input, id) ->
                assertEquals(id,
                        GeodeDropData.definitionFor(
                                new ItemStack(
                                        BuiltInRegistries.ITEM
                                                .get(input)))));
    }
}
