package com.stardew.craft.museum;

import com.stardew.craft.api.v1.museum.StardewLostBookDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LostBookRegistryAtomicSnapshotTest {
    @Test
    void definitionsInteractionIndexOrderAndMaximumPublishTogether() {
        LostBookRegistry.Catalog previous = LostBookRegistry.catalog();
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath(
                "lost_book_snapshot_test", "library");
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                "lost_book_snapshot_test", "first");
        BlockPos firstPosition = new BlockPos(3, 4, 5);
        try {
            StardewLostBookDefinition first = definition(
                    30, dimension, firstPosition);
            apply(Map.of(firstId, first));

            LostBookRegistry.Catalog accepted =
                    LostBookRegistry.catalog();
            assertCoherent(accepted);
            assertEquals(firstId,
                    LostBookRegistry.at(dimension, firstPosition));
            assertEquals(30, LostBookRegistry.discoveryMaximum());

            ResourceLocation duplicateId =
                    ResourceLocation.fromNamespaceAndPath(
                            "lost_book_snapshot_test", "duplicate");
            apply(Map.of(
                    firstId, first,
                    duplicateId, definition(
                            31, dimension, firstPosition)));

            assertSame(accepted, LostBookRegistry.catalog(),
                    "a duplicate interaction point replaced the accepted catalog");
            assertEquals(firstId,
                    LostBookRegistry.at(dimension, firstPosition));
        } finally {
            apply(previous.definitions().definitions());
        }
    }

    private static void assertCoherent(
            LostBookRegistry.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                LostBookRegistry.snapshot());
        Set<ResourceLocation> definitionIds =
                catalog.definitions().definitions().keySet();
        assertEquals(definitionIds,
                catalog.orderedBooks().stream()
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()));
        catalog.interactions().values().forEach(id ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        definitionIds.contains(id)));
        int expectedMaximum = Math.max(
                21,
                catalog.books().values().stream()
                        .mapToInt(StardewLostBookDefinition::unlockAt)
                        .max()
                        .orElse(0));
        assertEquals(expectedMaximum,
                catalog.discoveryMaximum());
    }

    private static StardewLostBookDefinition definition(
            int unlockAt,
            ResourceLocation dimension,
            BlockPos position
    ) {
        return new StardewLostBookDefinition(
                unlockAt,
                "lost_book_snapshot_test.text",
                List.of(),
                List.of(new StardewLostBookDefinition.Interaction(
                        dimension,
                        position.getX(),
                        position.getY(),
                        position.getZ())));
    }

    private static void apply(
            Map<ResourceLocation, StardewLostBookDefinition> definitions
    ) {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        definitions.forEach((id, definition) ->
                sources.put(id, definition.toString()));
        LostBookRegistry.applyCandidate(
                definitions,
                sources,
                new ArrayList<>());
    }
}
