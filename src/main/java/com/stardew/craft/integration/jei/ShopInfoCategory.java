package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JEI category for NPC shop information.
 * Shows: [item icon] | shop name | price | season availability
 */
public class ShopInfoCategory implements IRecipeCategory<ShopInfoCategory.DisplayEntry> {
    public static final RecipeType<DisplayEntry> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "shop_info", DisplayEntry.class);

    private static final int GUI_WIDTH = 166;
    private static final int GUI_HEIGHT = 52;

    private final IDrawable icon;
    private final Component title;

    public record DisplayEntry(
            ItemStack item,
            String shopId,
            int price,
            int stock,
            Set<Integer> seasons,
            int minYear,
            boolean isRecipe
    ) {}

    @SuppressWarnings("null")
    public ShopInfoCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                ArtisanRecipeCategory.getItemStack("stardewcraft:gold_bar"));
        this.title = Component.translatable("stardewcraft.jei.shop_info");
        JeiDrawHelper.initGoldIcon(guiHelper);
    }

    @Override
    public RecipeType<DisplayEntry> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return GUI_WIDTH;
    }

    @Override
    public int getHeight() {
        return GUI_HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @SuppressWarnings("null")
    @Override
    public void setRecipe(@SuppressWarnings("null") IRecipeLayoutBuilder builder,
                          @SuppressWarnings("null") DisplayEntry recipe,
                          @SuppressWarnings("null") IFocusGroup focuses) {
        // Item slot — shifted right to make room for portrait
        builder.addSlot(RecipeIngredientRole.OUTPUT, 38, 18)
                .addItemStack(recipe.item());
    }

    @SuppressWarnings("null")
    @Override
    public void draw(@SuppressWarnings("null") DisplayEntry recipe,
                     @SuppressWarnings("null") IRecipeSlotsView recipeSlotsView,
                     @SuppressWarnings("null") GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // Background panel
        JeiDrawHelper.drawPanel(guiGraphics, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // NPC portrait — left side (28×28)
        String portraitKey = JeiPortraitCache.shopIdToPortraitKey(recipe.shopId());
        if (portraitKey != null) {
            // Draw a subtle border around the portrait
            guiGraphics.fill(5, 5, 35, 35, 0xFF8B5E34); // border
            guiGraphics.fill(6, 6, 34, 34, 0xFFF5E4C8); // bg
            JeiDrawHelper.drawNpcPortrait(guiGraphics, portraitKey, 6, 6, 28);
        }

        // Item slot
        JeiDrawHelper.drawSlotBg(guiGraphics, 37, 17);

        int x = 60;

        // Shop name — styled header (translated)
        String shopLabel = Component.translatable("stardewcraft.jei.shop.name." + recipe.shopId()).getString();
        guiGraphics.drawString(font, shopLabel, x, 6, JeiDrawHelper.TEXT_TITLE, false);

        // Price with gold icon
        if (recipe.isRecipe()) {
            JeiDrawHelper.drawGoldAmount(guiGraphics, font, x, 20, recipe.price());
            String priceText = recipe.price() + Component.translatable("stardewcraft.jei.gold_suffix").getString();
            int pw = 10 + font.width(priceText) + 3;
            String recipeTag = Component.translatable("stardewcraft.jei.shop.recipe_tag").getString();
            guiGraphics.drawString(font, recipeTag, x + pw, 20, JeiDrawHelper.TEXT_MUTED, false);
        } else if (recipe.price() <= 0) {
            String freeText = Component.translatable("stardewcraft.jei.shop.free").getString();
            guiGraphics.drawString(font, freeText, x, 20, 0xFF4CAF50, false);
        } else {
            int pw = JeiDrawHelper.drawGoldAmount(guiGraphics, font, x, 20, recipe.price());
            // Stock info
            if (recipe.stock() != Integer.MAX_VALUE) {
                guiGraphics.drawString(font, " [×" + recipe.stock() + "]",
                        x + pw, 20, JeiDrawHelper.TEXT_MUTED, false);
            }
        }

        // Season dots + year info — bottom row
        int bottomY = 36;
        int dx = x;
        if (!recipe.seasons().isEmpty()) {
            dx += JeiDrawHelper.drawSeasonDots(guiGraphics, dx, bottomY + 1, recipe.seasons());
            dx += 2;
        }
        if (recipe.minYear() > 1) {
            guiGraphics.drawString(font, "Y" + recipe.minYear() + "+", dx, bottomY,
                    JeiDrawHelper.TEXT_BODY, false);
        }
    }

    /**
     * Build all display entries from all shops.
     */
    public static List<DisplayEntry> buildAllEntries() {
        List<DisplayEntry> result = new ArrayList<>();
        for (String shopId : ShopRegistry.allShopIds()) {
            ShopRegistry.ShopDefinition def = ShopRegistry.get(shopId);
            if (def == null) continue;
            for (ShopItemEntry entry : def.items()) {
                String itemId = entry.itemId();
                boolean isRecipe = itemId.startsWith("recipe:");
                String actualItemId = isRecipe ? itemId.substring("recipe:".length()) : itemId;

                ItemStack stack = isRecipe
                        ? com.stardew.craft.player.StardewCraftingRecipeData.getOutputStack(actualItemId)
                        : ItemStack.EMPTY;
                if (stack.isEmpty() && isRecipe) {
                    stack = com.stardew.craft.cooking.service.VanillaCookingRecipeData.getDefinition(actualItemId)
                            .map(definition -> ArtisanRecipeCategory.getItemStack(definition.output().toString()))
                            .orElse(ItemStack.EMPTY);
                }
                if (stack.isEmpty()) stack = ArtisanRecipeCategory.getItemStack(actualItemId);
                if (stack.isEmpty()) continue;

                result.add(new DisplayEntry(
                        stack, shopId, entry.price(), entry.stock(),
                        entry.seasons(), entry.minYear(), isRecipe));
            }
        }
        return result;
    }
}
