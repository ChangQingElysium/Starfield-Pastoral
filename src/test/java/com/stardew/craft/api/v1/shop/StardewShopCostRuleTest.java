package com.stardew.craft.api.v1.shop;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewItemCost;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewShopCostRuleTest {
    @Test
    void codecBuildsAndScalesCompositeCosts() {
        var json = JsonParser.parseString("""
                {
                  "shop": "test:orchard",
                  "item": "minecraft:apple",
                  "priority": 50,
                  "currencies": [
                    {"id": "test:marks", "amount": 2}
                  ],
                  "items": [
                    {"id": "minecraft:emerald", "amount": 3}
                  ]
                }
                """);
        StardewShopCostRule rule =
                StardewShopCostRule.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .getOrThrow();
        var cost = rule.cost(5);

        assertEquals("test:orchard", rule.shop());
        assertEquals(2, cost.entries().size());
        assertEquals(
                new StardewCurrencyCost(
                        ResourceLocation.parse("test:marks"),
                        10),
                cost.entries().get(0));
        assertEquals(
                new StardewItemCost(
                        ResourceLocation.withDefaultNamespace(
                                "emerald"),
                        15),
                cost.entries().get(1));
    }
}
