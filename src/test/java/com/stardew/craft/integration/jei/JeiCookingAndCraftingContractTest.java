package com.stardew.craft.integration.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JeiCookingAndCraftingContractTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");
    private static final List<String> UNLOCK_KEYS = List.of(
            "stardewcraft.jei.unlock.available",
            "stardewcraft.jei.unlock.conditional",
            "stardewcraft.jei.unlock.event",
            "stardewcraft.jei.unlock.festival_shop",
            "stardewcraft.jei.unlock.museum",
            "stardewcraft.jei.unlock.other",
            "stardewcraft.jei.unlock.shop",
            "stardewcraft.jei.unlock.skill");

    @Test
    void bundledCookingTableContainsAllRecipesAndFitsTheFourSlotLayout() throws Exception {
        JsonObject recipes = resourceJson("/data/stardewcraft/cooking/vanilla_cooking_recipes.json");
        assertEquals(83, recipes.size());
        assertTrue(recipes.entrySet().stream().allMatch(entry -> {
            int ingredientCount = entry.getValue().getAsJsonArray().size();
            return ingredientCount >= 1 && ingredientCount <= 4;
        }));
    }

    @Test
    void everyLanguageDefinesTheNewSemanticUnlockText() throws Exception {
        for (String language : LANGUAGES) {
            JsonObject translations = resourceJson("/assets/stardewcraft/lang/" + language + ".json");
            for (String key : UNLOCK_KEYS) {
                assertTrue(translations.has(key), language + " is missing " + key);
            }
        }
    }

    @Test
    void everyLanguageContainsTheCompleteEnglishJeiKeySet() throws Exception {
        JsonObject english = resourceJson("/assets/stardewcraft/lang/en_us.json");
        Set<String> expected = english.keySet().stream()
                .filter(key -> key.startsWith("stardewcraft.jei."))
                .collect(Collectors.toSet());
        for (String language : LANGUAGES) {
            JsonObject translations = resourceJson("/assets/stardewcraft/lang/" + language + ".json");
            Set<String> actual = translations.keySet().stream()
                    .filter(key -> key.startsWith("stardewcraft.jei."))
                    .collect(Collectors.toSet());
            assertEquals(expected, actual, language + " JEI keys differ from en_us");
        }
    }

    @Test
    void fourIngredientCraftingLayoutRegistersInputsAndOutputWithoutFakeWorkstation() {
        StardewCraftingCategory category = new StardewCraftingCategory(guiHelper());
        List<JeiIngredientStacks.Input> inputs = List.of(
                input(Items.APPLE), input(Items.CARROT), input(Items.POTATO), input(Items.SUGAR));
        StardewCraftingCategory.DisplayRecipe recipe = new StardewCraftingCategory.DisplayRecipe(
                "test", inputs, new ItemStack(Items.BREAD), Optional.empty());
        Map<RecipeIngredientRole, Integer> slots = new EnumMap<>(RecipeIngredientRole.class);

        assertDoesNotThrow(() -> category.setRecipe(layoutBuilder(slots), recipe, null));
        assertEquals(4, slots.getOrDefault(RecipeIngredientRole.INPUT, 0));
        assertEquals(0, slots.getOrDefault(RecipeIngredientRole.CATALYST, 0));
        assertEquals(1, slots.getOrDefault(RecipeIngredientRole.OUTPUT, 0));
    }

    @Test
    void inputGridCentersPartialRowsInsteadOfLeavingSlotsInTheTopLeft() {
        assertEquals(new JeiInputGrid.Position(21, 21), JeiInputGrid.position(1, 0, 10, 10));
        assertEquals(new JeiInputGrid.Position(10, 21), JeiInputGrid.position(2, 0, 10, 10));
        assertEquals(new JeiInputGrid.Position(32, 21), JeiInputGrid.position(2, 1, 10, 10));
        assertEquals(new JeiInputGrid.Position(21, 32), JeiInputGrid.position(3, 2, 10, 10));
    }

    @Test
    void rebuiltRecipesWithCopiedStacksKeepTheSameRefreshSignature() {
        List<JeiIngredientStacks.Input> firstInputs = List.of(input(Items.APPLE));
        List<JeiIngredientStacks.Input> secondInputs = List.of(input(Items.APPLE));
        StardewCraftingCategory.DisplayRecipe first = new StardewCraftingCategory.DisplayRecipe(
                "test", firstInputs, new ItemStack(Items.BREAD), Optional.empty());
        StardewCraftingCategory.DisplayRecipe second = new StardewCraftingCategory.DisplayRecipe(
                "test", secondInputs, new ItemStack(Items.BREAD), Optional.empty());
        assertEquals(first.contentSignature(), second.contentSignature());
    }

    private static JeiIngredientStacks.Input input(net.minecraft.world.item.Item item) {
        return new JeiIngredientStacks.Input(List.of(new ItemStack(item)), 1);
    }

    private static JsonObject resourceJson(String path) throws Exception {
        try (InputStream stream = JeiCookingAndCraftingContractTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
            return JsonParser.parseString(text).getAsJsonObject();
        }
    }

    private static IGuiHelper guiHelper() {
        IDrawableStatic drawable = proxy(IDrawableStatic.class, (proxy, method, args) -> switch (method.getName()) {
            case "getWidth", "getHeight" -> 16;
            default -> null;
        });
        IDrawableBuilder builder = proxy(IDrawableBuilder.class, (proxy, method, args) -> switch (method.getName()) {
            case "setTextureSize" -> proxy;
            case "build" -> drawable;
            default -> defaultValue(method.getReturnType());
        });
        return proxy(IGuiHelper.class, (proxy, method, args) -> switch (method.getName()) {
            case "createDrawableIngredient" -> drawable;
            case "drawableBuilder" -> builder;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static IRecipeLayoutBuilder layoutBuilder(Map<RecipeIngredientRole, Integer> slots) {
        IRecipeSlotBuilder slot = proxy(IRecipeSlotBuilder.class, (proxy, method, args) ->
                method.getReturnType().isInstance(proxy)
                        ? proxy
                        : defaultValue(method.getReturnType()));
        return proxy(IRecipeLayoutBuilder.class, (proxy, method, args) -> {
            if (method.getName().equals("addSlot")) {
                slots.merge((RecipeIngredientRole) args[0], 1, Integer::sum);
                return slot;
            }
            if (method.getName().equals("createFocusLink")) {
                fail("Recipe slots must not create invalid JEI focus links");
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}
