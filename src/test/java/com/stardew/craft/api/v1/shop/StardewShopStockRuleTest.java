package com.stardew.craft.api.v1.shop;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewShopStockRuleTest {
    @Test
    void codecReadsScopeResetAndDefaults() {
        var weekly = StardewShopStockRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "shop": "test:orchard",
                          "item": "minecraft:apple",
                          "priority": 50,
                          "scope": "world",
                          "reset": "week"
                        }
                        """)).getOrThrow();
        assertEquals(
                StardewShopStockRule.Scope.WORLD,
                weekly.scope());
        assertEquals(
                StardewShopStockRule.Reset.WEEK,
                weekly.reset());

        var defaults = StardewShopStockRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "shop": "test:orchard",
                          "item": "minecraft:apple"
                        }
                        """)).getOrThrow();
        assertEquals(
                StardewShopStockRule.Scope.PLAYER,
                defaults.scope());
        assertEquals(
                StardewShopStockRule.Reset.DAY,
                defaults.reset());
    }

    @Test
    void codecRejectsUnknownReset() {
        var result = StardewShopStockRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "shop": "test:orchard",
                          "item": "minecraft:apple",
                          "reset": "moon"
                        }
                        """));
        assertTrue(result.error().isPresent());
    }
}
