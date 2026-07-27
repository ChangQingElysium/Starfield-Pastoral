package com.stardew.craft.api.v1;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.internal.BuiltinApiTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinLocationConditionCodecTest {
    @Test
    void namespacedHierarchyAndEnvironmentCriteriaDecode() {
        BuiltinApiTypes.bootstrap();
        var decoded = StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:location",
                          "data": {
                            "locations": ["example:orchard"],
                            "required_tags": ["stardewcraft:outdoor"],
                            "excluded_tags": ["example:closed"],
                            "properties": {
                              "stardewcraft:climate": "temperate"
                            }
                          }
                        }
                        """));
        assertTrue(decoded.result().isPresent());
    }

    @Test
    void emptyOrContradictoryCriteriaAreRejected() {
        BuiltinApiTypes.bootstrap();
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:location",
                          "data": {}
                        }
                        """)).error().isPresent());
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:location",
                          "data": {
                            "required_tags": ["example:market"],
                            "excluded_tags": ["example:market"]
                          }
                        }
                        """)).error().isPresent());
    }

    @Test
    void reusableTimeConditionAcceptsDayAndOvernightWindows() {
        BuiltinApiTypes.bootstrap();
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:time",
                          "data": {"start": 900, "end": 1700}
                        }
                        """)).result().isPresent());
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:time",
                          "data": {"start": 2200, "end": 200}
                        }
                        """)).result().isPresent());
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:time",
                          "data": {"start": 1265, "end": 1700}
                        }
                        """)).error().isPresent());
    }

    @Test
    void reusableInventoryAndHostEventConditionsDecodeStrictly() {
        BuiltinApiTypes.bootstrap();
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:lacks_item",
                          "data": {
                            "item": "stardewcraft:milk_pail",
                            "count": 1
                          }
                        }
                        """)).result().isPresent());
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:seen_event",
                          "data": {
                            "id": "502261",
                            "scope": "host"
                          }
                        }
                        """)).result().isPresent());
        assertTrue(StardewConditions.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "stardewcraft:seen_event",
                          "data": {
                            "id": "502261",
                            "scope": "somebody"
                          }
                        }
                        """)).error().isPresent());
    }
}
