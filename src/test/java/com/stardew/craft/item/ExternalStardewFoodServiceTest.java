package com.stardew.craft.item;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.item.StardewItemData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStardewFoodServiceTest {
    @Test
    void edibilityAloneMarksExternalMetadataAsFood() {
        StardewItemData food = data(10, 0, 0);
        StardewItemData inedible = data(-300, 0, 0);

        assertTrue(ExternalStardewFoodService.isFoodMetadata(food));
        assertFalse(ExternalStardewFoodService.isFoodMetadata(inedible));
    }

    @Test
    void explicitEnergyOrHealthAlsoMarksMetadataAsFood() {
        assertTrue(ExternalStardewFoodService.isFoodMetadata(data(-300, 25, 0)));
        assertTrue(ExternalStardewFoodService.isFoodMetadata(data(-300, 0, 11)));
    }

    @Test
    void meagerMealsHalveOnlyPositiveRestoration() {
        assertEquals(12, ExternalStardewFoodService.scalePositiveEffect(25, true));
        assertEquals(1, ExternalStardewFoodService.scalePositiveEffect(1, true));
        assertEquals(-10, ExternalStardewFoodService.scalePositiveEffect(-10, true));
        assertEquals(25, ExternalStardewFoodService.scalePositiveEffect(25, false));
    }

    @Test
    void foodContextMixinsAreEnabled() throws Exception {
        try (var stream = ExternalStardewFoodServiceTest.class.getClassLoader()
                .getResourceAsStream("stardewcraft.mixins.json")) {
            assertTrue(stream != null, "missing mixin configuration");
            var mixins = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("mixins");
            assertTrue(mixins.contains(JsonParser.parseString("\"ItemStackStardewFoodContextMixin\"")));
            assertTrue(mixins.contains(JsonParser.parseString("\"PlayerStardewFoodMixin\"")));
        }
    }

    private static StardewItemData data(int edibility, int energy, int health) {
        return new StardewItemData(
                ResourceLocation.fromNamespaceAndPath("example", "food"),
                10,
                edibility,
                energy,
                health,
                false);
    }
}
