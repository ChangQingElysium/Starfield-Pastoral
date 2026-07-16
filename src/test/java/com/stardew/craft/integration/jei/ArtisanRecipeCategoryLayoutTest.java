package com.stardew.craft.integration.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ArtisanRecipeCategoryLayoutTest {
    @Test
    void qualityVariantsDoNotCreateInvalidJeiFocusLinks() {
        MachineJeiRegistry.Machine machine = MachineJeiRegistry.all().stream()
                .filter(candidate -> candidate.id().getPath().equals("keg"))
                .findFirst()
                .orElseThrow();
        ArtisanRecipeCategory category = new ArtisanRecipeCategory(
                guiHelper(), machine, new ItemStack(Items.BARREL));
        ArtisanJeiRecipe recipe = new ArtisanJeiRecipe(
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "test/keg/grape"),
                machine,
                List.of(new ArtisanJeiRecipe.Input(List.of(
                        new ItemStack(Items.APPLE),
                        new ItemStack(Items.APPLE),
                        new ItemStack(Items.APPLE),
                        new ItemStack(Items.APPLE)
                ), 1, false)),
                List.of(new ArtisanJeiRecipe.Output(List.of(new ItemStack(Items.POTION)), 1, 1, 1.0D)),
                10,
                true,
                -1
        );

        Map<RecipeIngredientRole, Integer> slots = new EnumMap<>(RecipeIngredientRole.class);
        IRecipeLayoutBuilder builder = layoutBuilder(slots);

        assertDoesNotThrow(() -> category.setRecipe(builder, recipe, null));
        assertEquals(1, slots.getOrDefault(RecipeIngredientRole.INPUT, 0));
        assertEquals(1, slots.getOrDefault(RecipeIngredientRole.CATALYST, 0));
        assertEquals(1, slots.getOrDefault(RecipeIngredientRole.OUTPUT, 0));
    }

    private static IGuiHelper guiHelper() {
        IDrawable drawable = proxy(IDrawable.class, (proxy, method, args) -> switch (method.getName()) {
            case "getWidth", "getHeight" -> 16;
            default -> null;
        });
        return proxy(IGuiHelper.class, (proxy, method, args) -> {
            if (method.getName().equals("createDrawableIngredient")) {
                return drawable;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static IRecipeLayoutBuilder layoutBuilder(Map<RecipeIngredientRole, Integer> slots) {
        IRecipeSlotBuilder slot = proxy(IRecipeSlotBuilder.class, (proxy, method, args) ->
                method.getReturnType().isInstance(proxy)
                        ? proxy
                        : defaultValue(method.getReturnType()));
        return proxy(IRecipeLayoutBuilder.class, (proxy, method, args) -> {
            if (method.getName().equals("addSlot")) {
                RecipeIngredientRole role = (RecipeIngredientRole) args[0];
                slots.merge(role, 1, Integer::sum);
                return slot;
            }
            if (method.getName().equals("createFocusLink")) {
                fail("Variant slots with different ingredient counts must not be focus-linked");
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
