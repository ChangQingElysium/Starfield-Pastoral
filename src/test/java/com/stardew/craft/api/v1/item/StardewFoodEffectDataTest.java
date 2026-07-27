package com.stardew.craft.api.v1.item;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewFoodEffectDataTest {
    @Test
    void codecUsesSafeDisplayAndChanceDefaults() {
        StardewFoodEffectData data = decode("""
                {
                  "effects": {
                    "example:harvest_luck": {
                      "effect": "stardewcraft:spirit_blessing",
                      "duration_ticks": 600
                    }
                  }
                }
                """);

        StardewFoodEffect effect = data.effects().get(id("harvest_luck"));
        assertEquals(id("spirit_blessing", "stardewcraft"), effect.effect());
        assertEquals(600, effect.durationTicks());
        assertEquals(0, effect.amplifier());
        assertEquals(1.0D, effect.chance());
        assertFalse(effect.ambient());
        assertTrue(effect.showParticles());
        assertTrue(effect.showIcon());
    }

    @Test
    void codecRejectsUnsafeRanges() {
        assertRejected(effectJson(0, 0, 1.0D));
        assertRejected(effectJson(20, -1, 1.0D));
        assertRejected(effectJson(20, 256, 1.0D));
        assertRejected(effectJson(20, 0, -0.01D));
        assertRejected(effectJson(20, 0, 1.01D));
    }

    @Test
    void mergeCombinesEntriesAndLetsNewerEntryIdWin() {
        StardewFoodEffect oldA = effect("stardewcraft:spirit_blessing", 100);
        StardewFoodEffect oldB = effect("minecraft:speed", 200);
        StardewFoodEffect newA = effect("stardewcraft:farmer_blessing", 300);

        StardewFoodEffectData merged = new StardewFoodEffectData(Map.of(
                id("a"), oldA,
                id("b"), oldB
        )).merge(new StardewFoodEffectData(Map.of(id("a"), newA)));

        assertEquals(2, merged.effects().size());
        assertEquals(newA, merged.effects().get(id("a")));
        assertEquals(oldB, merged.effects().get(id("b")));
    }

    private static StardewFoodEffectData decode(String json) {
        return StardewFoodEffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result()
                .orElseThrow();
    }

    private static void assertRejected(String json) {
        assertTrue(StardewFoodEffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .error()
                .isPresent());
    }

    private static String effectJson(int duration, int amplifier, double chance) {
        return """
                {
                  "effects": {
                    "example:test": {
                      "effect": "minecraft:speed",
                      "duration_ticks": %d,
                      "amplifier": %d,
                      "chance": %s
                    }
                  }
                }
                """.formatted(duration, amplifier, chance);
    }

    private static StardewFoodEffect effect(String id, int duration) {
        return new StardewFoodEffect(
                ResourceLocation.parse(id),
                duration,
                0,
                1.0D,
                false,
                true,
                true);
    }

    private static ResourceLocation id(String path) {
        return id(path, "example");
    }

    private static ResourceLocation id(String path, String namespace) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
