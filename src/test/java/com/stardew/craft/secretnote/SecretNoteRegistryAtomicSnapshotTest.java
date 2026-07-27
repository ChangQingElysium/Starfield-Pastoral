package com.stardew.craft.secretnote;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SecretNoteRegistryAtomicSnapshotTest {
    @Test
    void definitionsNumberIndexesOrderAndSyncJsonPublishTogether() {
        String previous = SecretNoteRegistry.getCachedJson();
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                "secret_note_snapshot_test", "first");
        try {
            SecretNoteRegistry.applyFromJson("""
                    {
                      "secret_note_snapshot_test:first": {
                        "vanilla_number": 40,
                        "display_number": 1,
                        "sort_order": 20,
                        "text": "secret_note_snapshot_test.first"
                      }
                    }
                    """);

            SecretNoteRegistry.Catalog accepted =
                    SecretNoteRegistry.catalog();
            assertCoherent(accepted);
            assertEquals(firstId,
                    SecretNoteRegistry.byVanillaNumber(40));
            assertEquals(firstId,
                    SecretNoteRegistry.byDisplayNumber(1));

            SecretNoteRegistry.applyFromJson("""
                    {
                      "secret_note_snapshot_test:duplicate_a": {
                        "vanilla_number": 41,
                        "display_number": 1,
                        "sort_order": 1,
                        "text": "secret_note_snapshot_test.duplicate_a"
                      },
                      "secret_note_snapshot_test:duplicate_b": {
                        "vanilla_number": 42,
                        "display_number": 1,
                        "sort_order": 2,
                        "text": "secret_note_snapshot_test.duplicate_b"
                      }
                    }
                    """);

            assertSame(accepted, SecretNoteRegistry.catalog(),
                    "duplicate display numbers replaced the accepted catalog");
            assertEquals(firstId,
                    SecretNoteRegistry.byVanillaNumber(40));

            SecretNoteRegistry.applyFromJson("""
                    {
                      "secret_note_snapshot_test:second": {
                        "vanilla_number": 43,
                        "display_number": 1,
                        "sort_order": 5,
                        "image_index": 2
                      }
                    }
                    """);

            SecretNoteRegistry.Catalog replacement =
                    SecretNoteRegistry.catalog();
            assertCoherent(replacement);
            assertEquals(ResourceLocation.fromNamespaceAndPath(
                            "secret_note_snapshot_test", "second"),
                    SecretNoteRegistry.byDisplayNumber(1));
        } finally {
            SecretNoteRegistry.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            SecretNoteRegistry.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                SecretNoteRegistry.snapshot());
        Set<ResourceLocation> definitionIds =
                catalog.definitions().definitions().keySet();
        assertEquals(definitionIds,
                catalog.orderedNotes().stream()
                        .map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()));
        catalog.vanillaNumbers().values().forEach(id ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        definitionIds.contains(id)));
        catalog.displayNumbers().values().forEach(id ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        definitionIds.contains(id)));
        assertEquals(
                definitionIds,
                JsonParser.parseString(catalog.cachedJson())
                        .getAsJsonObject()
                        .keySet().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
