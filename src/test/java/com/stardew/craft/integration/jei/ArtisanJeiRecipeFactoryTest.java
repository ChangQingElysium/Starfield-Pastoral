package com.stardew.craft.integration.jei;

import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtisanJeiRecipeFactoryTest {
    private static final String SNAPSHOT = """
            {
              "stardewcraft:keg": [{
                "inputId": "stardewcraft:grape", "inputTag": null, "inputMode": "DEFAULT",
                "outputId": "stardewcraft:grape_wine", "outputCount": 1, "minutes": 8820,
                "consumeCount": 1, "keepInputQuality": false, "outputQuality": -1,
                "preserveType": null, "seedMakerRule": null, "outputMode": "FIXED"
              }],
              "stardewcraft:fish_smoker": [{
                "inputId": null, "inputTag": null, "inputMode": "FISH_TYPE",
                "outputId": null, "outputCount": 1, "minutes": 50,
                "consumeCount": 1, "keepInputQuality": true, "outputQuality": -1,
                "preserveType": null, "seedMakerRule": null, "outputMode": "SMOKED"
              }],
              "stardewcraft:incubator": [{
                "inputId": "stardewcraft:egg_white", "inputTag": null, "inputMode": "DEFAULT",
                "outputId": null, "outputCount": 1, "minutes": 9000,
                "consumeCount": 1, "keepInputQuality": false, "outputQuality": -1,
                "preserveType": null, "seedMakerRule": null, "outputMode": "COPY_INPUT"
              }],
              "stardewcraft:seed_maker": [{
                "inputId": "stardewcraft:grape", "inputTag": null, "inputMode": "DEFAULT",
                "outputId": null, "outputCount": 1, "minutes": 20,
                "consumeCount": 1, "keepInputQuality": false, "outputQuality": -1,
                "preserveType": null,
                "seedMakerRule": {"ancientChance": 0.005, "mixedChance": 0.02,
                  "mixedMin": 1, "mixedMax": 4, "seedMin": 1, "seedMax": 3},
                "outputMode": "SEEDMAKER"
              }]
            }
            """;

    @BeforeEach
    void installFixtureSnapshot() {
        ArtisanRecipeDataManager.applyFromJson(SNAPSHOT);
    }

    @Test
    void fixedKegRecipeProducesARealInputAndOutputRelationship() {
        var recipes = ArtisanJeiRecipeFactory.build(machine("keg"));
        assertEquals(1, recipes.size());
        assertFalse(recipes.getFirst().inputs().getFirst().stacks().isEmpty());
        assertFalse(recipes.getFirst().outputs().getFirst().stacks().isEmpty());
    }

    @Test
    void fishSmokerRegistersCoalAsASecondRealInput() {
        var recipes = ArtisanJeiRecipeFactory.build(machine("fish_smoker"));
        assertFalse(recipes.isEmpty());
        assertTrue(recipes.stream().allMatch(recipe -> recipe.inputs().size() == 2));
        assertTrue(recipes.stream().allMatch(recipe -> recipe.inputs().get(1).stacks().stream()
                .allMatch(stack -> stack.is(Items.COAL))));
        assertTrue(recipes.stream().allMatch(ArtisanJeiRecipe::keepInputQuality));
    }

    @Test
    void incubatorDoesNotInventAnEggOutput() {
        var recipes = ArtisanJeiRecipeFactory.build(machine("incubator"));
        assertEquals(1, recipes.size());
        assertTrue(recipes.getFirst().outputs().isEmpty());
    }

    @Test
    void seedMakerExposesAllThreeRandomOutputBranches() {
        var recipes = ArtisanJeiRecipeFactory.build(machine("seed_maker"));
        assertEquals(1, recipes.size());
        assertEquals(3, recipes.getFirst().outputs().size());
        double totalChance = recipes.getFirst().outputs().stream()
                .mapToDouble(ArtisanJeiRecipe.Output::chance)
                .sum();
        assertEquals(1.0D, totalChance, 0.000_001D);
    }

    private static MachineJeiRegistry.Machine machine(String path) {
        return MachineJeiRegistry.find(ResourceLocation.fromNamespaceAndPath("stardewcraft", path))
                .orElseThrow();
    }
}
