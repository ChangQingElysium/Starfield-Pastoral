package com.stardew.craft.mail;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.mail.StardewMailDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MailRegistryAtomicSnapshotTest {
    private static final Gson GSON = new Gson();

    @Test
    void definitionsAndLegacyDisplayIdsPublishTogether() {
        MailRegistry.Catalog previous = MailRegistry.catalog();
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "mail_snapshot_test", "first");
            StardewMailDefinition definition = definition(
                    "mail_snapshot_test.first");
            MailRegistry.applyCandidate(candidate(
                    Map.of(id, definition),
                    Map.of(id, "LegacyFirst"),
                    List.of()));

            MailRegistry.Catalog accepted =
                    MailRegistry.catalog();
            assertCoherent(accepted);
            assertEquals("LegacyFirst",
                    MailRegistry.get(id.toString()).getId());

            MailRegistry.applyCandidate(candidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate"))));

            assertSame(accepted, MailRegistry.catalog(),
                    "invalid mail candidate replaced the accepted catalog");
            assertCoherent(accepted);
        } finally {
            MailRegistry.applyCandidate(candidate(
                    previous.definitions().definitions(),
                    previous.displayIds(),
                    List.of()));
        }
    }

    private static StardewMailDefinition definition(String text) {
        return StardewMailDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(
                                "{\"text\":\"" + text + "\"}"))
                .getOrThrow();
    }

    private static MailRegistry.Candidate candidate(
            Map<ResourceLocation, StardewMailDefinition> definitions,
            Map<ResourceLocation, String> displayIds,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<ResourceLocation, String> sources =
                new LinkedHashMap<>();
        definitions.forEach((id, definition) ->
                sources.put(id,
                        StardewMailDefinition.CODEC
                                .encodeStart(JsonOps.INSTANCE, definition)
                                .map(GSON::toJson)
                                .getOrThrow()));
        return new MailRegistry.Candidate(
                definitions, sources, displayIds, diagnostics);
    }

    private static void assertCoherent(MailRegistry.Catalog catalog) {
        assertSame(catalog.definitions(), MailRegistry.snapshot());
        catalog.definitions().definitions().forEach((id, definition) -> {
            assertSame(definition, MailRegistry.getDefinition(id));
            assertEquals(catalog.displayIds().get(id),
                    MailRegistry.get(id.toString()).getId());
        });
    }
}
