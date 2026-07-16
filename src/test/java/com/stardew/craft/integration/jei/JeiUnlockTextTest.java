package com.stardew.craft.integration.jei;

import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.player.UnlockSourceData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiUnlockTextTest {
    @AfterEach
    void clearUnlockSources() {
        UnlockSourceData.applyFromJson("{}");
    }

    @Test
    void legacyInternalConditionsBecomeSemanticTranslationKeys() {
        assertEquals("stardewcraft.jei.unlock.skill", key(recipe("s Farming 3")));
        assertEquals("stardewcraft.jei.unlock.event", key(recipe("l 2")));
        assertEquals("stardewcraft.jei.unlock.festival_shop",
                key(recipe("Festival_SpiritsEve_Pierre")));
    }

    @Test
    void defaultConditionDoesNotAddAClutterLine() {
        assertTrue(JeiUnlockText.crafting(recipe("default")).isEmpty());
    }

    @Test
    void nullConditionUsesKnownSourceWithoutLeakingNullText() {
        UnlockSourceData.applyFromJson("""
                {"stardewcraft:skill/fishing/3":{"recipes":["test_recipe"]}}
                """);
        assertEquals("stardewcraft.jei.unlock.skill", key(recipe("null")));
    }

    @Test
    void nullConditionWithoutAConfiguredSourceIsNotRendered() {
        assertTrue(JeiUnlockText.crafting(recipe("null")).isEmpty());
    }

    private static StardewCraftingRecipeData.RecipeEntry recipe(String condition) {
        return new StardewCraftingRecipeData.RecipeEntry(
                "test_recipe",
                new StardewCraftingRecipeData.OutputEntry("minecraft:stick", 1),
                List.of(new StardewCraftingRecipeData.IngredientEntry(
                        "minecraft:oak_planks", null, null, null, 1)),
                condition,
                List.of());
    }

    private static String key(StardewCraftingRecipeData.RecipeEntry recipe) {
        Component text = JeiUnlockText.crafting(recipe).orElseThrow();
        return ((TranslatableContents) text.getContents()).getKey();
    }
}
