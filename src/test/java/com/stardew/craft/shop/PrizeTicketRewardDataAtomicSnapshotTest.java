package com.stardew.craft.shop;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.loot.StardewPrizeTicketRewardDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PrizeTicketRewardDataAtomicSnapshotTest {
    @Test
    void definitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewPrizeTicketRewardDefinition> previous =
                PrizeTicketRewardData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("low");
            ResourceLocation highId = id("high");
            StardewPrizeTicketRewardDefinition low = reward(1);
            StardewPrizeTicketRewardDefinition high = reward(100);
            Map<ResourceLocation, StardewPrizeTicketRewardDefinition> definitions =
                    Map.of(lowId, low, highId, high);

            PrizeTicketRewardData.applyCandidate(
                    definitions, sources(definitions), List.of());
            PrizeTicketRewardData.Catalog accepted =
                    PrizeTicketRewardData.catalog();
            assertSame(accepted.definitions(),
                    PrizeTicketRewardData.snapshot());
            assertEquals(List.of(highId, lowId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            PrizeTicketRewardData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted,
                    PrizeTicketRewardData.catalog());
        } finally {
            PrizeTicketRewardData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    private static StardewPrizeTicketRewardDefinition reward(
            int priority
    ) {
        return StardewPrizeTicketRewardDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "level": 5,
                                  "priority": %d,
                                  "reward": {
                                    "type": "stardewcraft:item",
                                    "data": {"item": "minecraft:apple"}
                                  }
                                }
                                """.formatted(priority)))
                .getOrThrow();
    }

    private static <T> Map<ResourceLocation, String> sources(
            Map<ResourceLocation, T> definitions
    ) {
        return definitions.keySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        id -> id, id -> "{}"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "prize_snapshot_test", path);
    }
}
