package com.stardew.craft.api.v1.shop;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewShopProductRuleTest {
    @Test
    void codecReadsRegisteredAction() {
        BuiltinApiTypes.bootstrap();
        var rule = StardewShopProductRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "shop": "test:orchard",
                          "item": "test:blessing",
                          "priority": 50,
                          "action": {
                            "type": "stardewcraft:add_money",
                            "data": {"amount": 25}
                          }
                        }
                        """)).getOrThrow();

        assertEquals("test:orchard", rule.shop());
        assertEquals(
                "stardewcraft:add_money",
                rule.action().type().toString());
    }

    @Test
    void acceptedPreparationRequiresExactGrant() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StardewShopProductPreparation(
                        StardewShopProductDecision.ACCEPT,
                        Optional.empty()));
        assertEquals(
                StardewShopProductDecision.REJECT,
                StardewShopProductPreparation.reject()
                        .decision());
    }
}
