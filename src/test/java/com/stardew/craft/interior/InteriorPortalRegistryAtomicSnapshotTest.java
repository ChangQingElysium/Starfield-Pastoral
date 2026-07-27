package com.stardew.craft.interior;

import com.google.gson.Gson;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.world.StardewPortalDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InteriorPortalRegistryAtomicSnapshotTest {
    private static final Gson GSON = new Gson();

    @Test
    void definitionsAndRuntimeTargetsPublishTogether() {
        Map<ResourceLocation, StardewPortalDefinition> previous =
                InteriorPortalRegistry.snapshot().definitions();
        try {
            ResourceLocation id =
                    ResourceLocation.fromNamespaceAndPath(
                            "portal_snapshot_test", "first");
            StardewPortalDefinition definition =
                    new StardewPortalDefinition(
                            1.5, 64, 2.5,
                            90, 5,
                            StardewPortalDefinition.Mode.EXIT);
            InteriorPortalRegistry.applyCandidate(
                    Map.of(id, definition),
                    sources(Map.of(id, definition)),
                    List.of());

            InteriorPortalRegistry.Catalog accepted =
                    InteriorPortalRegistry.catalog();
            assertCoherent(accepted);

            InteriorPortalRegistry.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));

            assertSame(accepted,
                    InteriorPortalRegistry.catalog(),
                    "invalid portal candidate replaced the accepted catalog");
            assertCoherent(accepted);
        } finally {
            InteriorPortalRegistry.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    private static Map<ResourceLocation, String> sources(
            Map<ResourceLocation, StardewPortalDefinition> definitions
    ) {
        Map<ResourceLocation, String> sources =
                new LinkedHashMap<>();
        definitions.forEach((id, definition) ->
                sources.put(id,
                        StardewPortalDefinition.CODEC
                                .encodeStart(JsonOps.INSTANCE, definition)
                                .map(GSON::toJson)
                                .getOrThrow()));
        return sources;
    }

    private static void assertCoherent(
            InteriorPortalRegistry.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                InteriorPortalRegistry.snapshot());
        catalog.definitions().definitions().forEach(
                (id, definition) -> {
                    InteriorPortalRegistry.PortalTarget target =
                            InteriorPortalRegistry.resolve(id.toString())
                                    .orElseThrow();
                    assertEquals(definition.x(), target.x());
                    assertEquals(definition.y(), target.y());
                    assertEquals(definition.z(), target.z());
                    assertEquals(definition.mode().name(),
                            target.mode().name());
                });
    }
}
