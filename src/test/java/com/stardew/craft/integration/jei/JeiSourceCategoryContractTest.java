package com.stardew.craft.integration.jei;

import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.FlavoredArtisanDrinkItem;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.shop.GeodeLootService;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JeiSourceCategoryContractTest {
    @Test
    void fishingKeepsDistinctRulesForTheSameFish() {
        FishingDataManager.applyFromJson("""
                {
                  "Test": {
                    "location": "Test",
                    "fish": [
                      {"id":"morning","item":"minecraft:cod","timeRanges":[[600,1200]]},
                      {"id":"evening","item":"minecraft:cod","timeRanges":[[1800,2400]]}
                    ]
                  }
                }
                """);
        long codRules = StardewJeiPlugin.buildFishingRecipes().stream()
                .filter(rule -> "minecraft:cod".equals(rule.itemId()))
                .count();
        assertEquals(2, codRules);
        long codPages = StardewJeiPlugin.buildFishingEntries().stream()
                .filter(entry -> "minecraft:cod".equals(entry.itemId()))
                .count();
        assertEquals(1, codPages);
    }

    @Test
    void fishingDeduplicatesEqualArrayBackedRulesByContent() {
        FishingDataManager.applyFromJson("""
                {
                  "Test": {
                    "location": "Test",
                    "fish": [
                      {"id":"copy_a","item":"minecraft:cod","timeRanges":[[600,1200]]},
                      {"id":"copy_b","item":"minecraft:cod","timeRanges":[[600,1200]]}
                    ]
                  }
                }
                """);
        long codRules = StardewJeiPlugin.buildFishingRecipes().stream()
                .filter(rule -> "minecraft:cod".equals(rule.itemId()))
                .count();
        assertEquals(1, codRules);
    }

    @Test
    void fishingMergesPagesThatOnlyDifferByLocation() {
        FishingDataManager.applyFromJson("""
                {
                  "Test": {
                    "location": "Test",
                    "fish": [
                      {"id":"river","item":"minecraft:cod","biomeTags":["#minecraft:is_river"]},
                      {"id":"ocean","item":"minecraft:cod","biomeTags":["#minecraft:is_ocean"]}
                    ]
                  }
                }
                """);
        List<FishingInfoCategory.DisplayEntry> codEntries = StardewJeiPlugin.buildFishingEntries().stream()
                .filter(entry -> "minecraft:cod".equals(entry.itemId()))
                .toList();
        assertEquals(1, codEntries.size());
        Set<String> locations = codEntries.getFirst().rules().stream()
                .flatMap(rule -> rule.biomeTags().stream())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("#minecraft:is_river", "#minecraft:is_ocean"), locations);
    }

    @Test
    void qualityIsDistinctInIngredientListButSharedForRecipeLookups() {
        Map<Item, ISubtypeInterpreter<ItemStack>> registered = registeredSubtypes();

        ISubtypeInterpreter<ItemStack> crop = registered.get(ModItems.PARSNIP.get());
        assertNotNull(crop);
        ItemStack normal = new ItemStack(ModItems.PARSNIP.get());
        ItemStack iridium = normal.copy();
        QualityHelper.setQuality(iridium, QualityHelper.IRIDIUM);

        Set<Object> ingredientIdentities = StardewItemDisplayStacks
                .stacksForItem(ModItems.PARSNIP.get()).stream()
                .map(stack -> crop.getSubtypeData(stack, UidContext.Ingredient))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(4, ingredientIdentities.size());

        assertNotEquals(
                crop.getSubtypeData(normal, UidContext.Ingredient),
                crop.getSubtypeData(iridium, UidContext.Ingredient));
        assertNull(crop.getSubtypeData(normal, UidContext.Recipe));
        assertNull(crop.getSubtypeData(iridium, UidContext.Recipe));

        assertTrue(registered.containsKey(ModItems.JELLY.get()));
        assertTrue(registered.containsKey(ModItems.WINE.get()));
        assertTrue(registered.containsKey(ModItems.JUICE.get()));
    }

    @Test
    void flowerColourIsDistinctOnlyInTheIngredientList() {
        ISubtypeInterpreter<ItemStack> flower =
                registeredSubtypes().get(ModItems.BLUE_JAZZ.get());
        assertNotNull(flower);
        List<ItemStack> variants = StardewItemDisplayStacks
                .stacksForItem(ModItems.BLUE_JAZZ.get());
        ItemStack firstColour = variants.stream()
                .filter(stack -> QualityHelper.getQuality(stack) == QualityHelper.NORMAL)
                .filter(stack -> Integer.valueOf(0).equals(
                        StardewItemDisplayStacks.getFlowerColor(stack)))
                .findFirst().orElseThrow();
        ItemStack secondColour = variants.stream()
                .filter(stack -> QualityHelper.getQuality(stack) == QualityHelper.NORMAL)
                .filter(stack -> Integer.valueOf(1).equals(
                        StardewItemDisplayStacks.getFlowerColor(stack)))
                .findFirst().orElseThrow();

        assertNotEquals(
                flower.getSubtypeData(firstColour, UidContext.Ingredient),
                flower.getSubtypeData(secondColour, UidContext.Ingredient));
        assertNull(flower.getSubtypeData(firstColour, UidContext.Recipe));
        assertNull(flower.getSubtypeData(secondColour, UidContext.Recipe));
    }

    @Test
    void sourceVariantsKeepQualityOnlyInTheIngredientList() {
        Map<Item, ISubtypeInterpreter<ItemStack>> registered = registeredSubtypes();
        ItemStack normal = FlavoredArtisanDrinkItem.createFlavored(PreserveType.WINE,
                new ItemStack(ModItems.GRAPE.get()), new ItemStack(ModItems.WINE.get()));
        ItemStack gold = normal.copy();
        QualityHelper.setQuality(gold, QualityHelper.GOLD);
        ISubtypeInterpreter<ItemStack> wine = registered.get(ModItems.WINE.get());
        assertNotNull(wine);

        assertNotEquals(
                wine.getSubtypeData(normal, UidContext.Ingredient),
                wine.getSubtypeData(gold, UidContext.Ingredient));
        assertEquals(
                wine.getSubtypeData(normal, UidContext.Recipe),
                wine.getSubtypeData(gold, UidContext.Recipe));

        ItemStack normalPreserve = PreservesItem.createFlavored(PreserveType.DRIED_FRUIT,
                new ItemStack(ModItems.PARSNIP.get()), new ItemStack(ModItems.DRIED_FRUIT.get()));
        ItemStack goldPreserve = normalPreserve.copy();
        QualityHelper.setQuality(goldPreserve, QualityHelper.GOLD);
        ISubtypeInterpreter<ItemStack> preserve = registered.get(ModItems.DRIED_FRUIT.get());
        assertNotNull(preserve);

        assertNotEquals(
                preserve.getSubtypeData(normalPreserve, UidContext.Ingredient),
                preserve.getSubtypeData(goldPreserve, UidContext.Ingredient));
        assertEquals(
                preserve.getSubtypeData(normalPreserve, UidContext.Recipe),
                preserve.getSubtypeData(goldPreserve, UidContext.Recipe));
    }

    @Test
    void preserveSubtypeKeepsSourceButIgnoresQuality() {
        ItemStack normalIngredient = new ItemStack(ModItems.PARSNIP.get());
        ItemStack goldIngredient = normalIngredient.copy();
        QualityHelper.setQuality(goldIngredient, QualityHelper.GOLD);
        ItemStack normal = PreservesItem.createFlavored(PreserveType.DRIED_FRUIT,
                normalIngredient, new ItemStack(ModItems.DRIED_FRUIT.get()));
        ItemStack gold = PreservesItem.createFlavored(PreserveType.DRIED_FRUIT,
                goldIngredient, new ItemStack(ModItems.DRIED_FRUIT.get()));

        assertEquals(PreservesItem.getSubtypeKey(normal), PreservesItem.getSubtypeKey(gold));
    }

    @Test
    void flavoredDrinkSubtypeKeepsSourceButIgnoresQuality() {
        ItemStack normal = FlavoredArtisanDrinkItem.createFlavored(PreserveType.WINE,
                new ItemStack(ModItems.GRAPE.get()), new ItemStack(ModItems.WINE.get()));
        ItemStack gold = normal.copy();
        QualityHelper.setQuality(gold, QualityHelper.GOLD);
        ItemStack strawberry = FlavoredArtisanDrinkItem.createFlavored(PreserveType.WINE,
                new ItemStack(ModItems.STRAWBERRY.get()), new ItemStack(ModItems.WINE.get()));

        assertEquals(FlavoredArtisanDrinkItem.getSubtypeKey(normal),
                FlavoredArtisanDrinkItem.getSubtypeKey(gold));
        assertFalse(FlavoredArtisanDrinkItem.getSubtypeKey(normal)
                .equals(FlavoredArtisanDrinkItem.getSubtypeKey(strawberry)));
    }

    @Test
    void tradeItemIsARealInputAndShopProductIsAnOutput() {
        ShopInfoCategory category = new ShopInfoCategory(guiHelper());
        ShopInfoCategory.DisplayEntry recipe = new ShopInfoCategory.DisplayEntry(
                new ItemStack(Items.APPLE), "test", 0, 1,
                new ItemStack(Items.EMERALD), 5, 2, Set.of(0), 2,
                40, true, 4, 1, true, false);
        Map<RecipeIngredientRole, Integer> slots = new EnumMap<>(RecipeIngredientRole.class);
        assertDoesNotThrow(() -> category.setRecipe(layoutBuilder(slots), recipe, null));
        assertEquals(1, slots.getOrDefault(RecipeIngredientRole.INPUT, 0));
        assertEquals(1, slots.getOrDefault(RecipeIngredientRole.OUTPUT, 0));
    }

    @Test
    void geodeDisplayUsesRuntimePoolsAndIncludesFallbackRanges() {
        List<GeodeLootService.DisplayOutput> outputs = GeodeLootService.getDisplayOutputs(
                new ItemStack(ModItems.GEODE.get()));
        assertTrue(outputs.stream().anyMatch(output -> output.stack().is(ModItems.ALAMITE.get())));
        assertTrue(outputs.stream().anyMatch(output -> output.stack().is(ModItems.STONE.get())
                && output.minCount() == 1 && output.maxCount() == 20));
        assertTrue(outputs.stream().anyMatch(output -> output.stack().is(ModItems.COPPER_ORE.get())
                && output.maxCount() == 20));
    }

    private static Map<Item, ISubtypeInterpreter<ItemStack>> registeredSubtypes() {
        Map<Item, ISubtypeInterpreter<ItemStack>> registered = new IdentityHashMap<>();
        ISubtypeRegistration registration = proxy(ISubtypeRegistration.class, (proxy, method, args) -> {
            if (method.getName().equals("registerSubtypeInterpreter")) {
                @SuppressWarnings("unchecked")
                ISubtypeInterpreter<ItemStack> interpreter =
                        (ISubtypeInterpreter<ItemStack>) args[1];
                registered.put((Item) args[0], interpreter);
            }
            return defaultValue(method.getReturnType());
        });
        new StardewJeiPlugin().registerItemSubtypes(registration);
        return registered;
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
                fail("Source categories must not create invalid focus links");
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
