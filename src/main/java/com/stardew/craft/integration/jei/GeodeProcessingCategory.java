package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.shop.GeodeLootService;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Geode source pages built from the output candidates exposed by the gameplay service. */
public final class GeodeProcessingCategory implements IRecipeCategory<GeodeProcessingCategory.DisplayEntry> {
    public static final RecipeType<DisplayEntry> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "geode_processing", DisplayEntry.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 64;
    private static final int INPUT_X = 10;
    private static final int CRUSHER_X = 77;
    private static final int OUTPUT_X = 138;
    private static final int SLOT_Y = 13;

    private final IDrawable icon;
    private final Component title;

    public record DisplayEntry(ItemStack geode, ItemStack output, int minCount, int maxCount) {
        public DisplayEntry {
            geode = geode == null ? ItemStack.EMPTY : geode.copy();
            output = output == null ? ItemStack.EMPTY : output.copy();
            minCount = Math.max(1, minCount);
            maxCount = Math.max(minCount, maxCount);
        }

        public DisplayEntry(ItemStack geode, ItemStack output) {
            this(geode, output, Math.max(1, output.getCount()), Math.max(1, output.getCount()));
        }

        @Override
        public ItemStack geode() {
            return geode.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        public String contentSignature() {
            return JeiRecipeSignatures.stack(geode) + '|' + JeiRecipeSignatures.stack(output)
                    + '|' + minCount + '|' + maxCount;
        }
    }

    public GeodeProcessingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.GEODE.get()));
        this.title = Component.translatable("stardewcraft.jei.geode_processing");
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
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DisplayEntry recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
                .addItemStack(recipe.geode())
                .setSlotName("geode");
        builder.addSlot(RecipeIngredientRole.CATALYST, CRUSHER_X, SLOT_Y)
                .addItemStack(new ItemStack(ModItems.GEODE_CRUSHER.get()))
                .setSlotName("processor");
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y)
                .addItemStack(recipe.output())
                .setSlotName("output");
    }

    @Override
    public void draw(DisplayEntry recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, 0, 0, WIDTH, HEIGHT, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, INPUT_X - 1, SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, CRUSHER_X - 1, SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, OUTPUT_X - 1, SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 56, SLOT_Y + 3, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 117, SLOT_Y + 3, 1.0F);

        CommonGuiTextures.drawEntryBox(graphics, 8, 44, WIDTH - 16, 15, 1.0F, false);
        MutableComponent details = Component.translatable("stardewcraft.jei.geode.cost", 25);
        if (recipe.minCount() != recipe.maxCount()) {
            details.append("  •  ").append(Component.translatable(
                    "stardewcraft.jei.geode.output_range", recipe.minCount(), recipe.maxCount()));
        }
        GuiText.drawCenteredClamped(graphics, font, details, WIDTH / 2, 47,
                WIDTH - 28, JeiDrawHelper.TEXT_BODY, false);
    }

    public static List<DisplayEntry> buildAllEntries() {
        List<DisplayEntry> result = new ArrayList<>();
        addVanilla(result, new ItemStack(ModItems.GEODE.get()));
        addVanilla(result, new ItemStack(ModItems.FROZEN_GEODE.get()));
        addVanilla(result, new ItemStack(ModItems.MAGMA_GEODE.get()));
        addVanilla(result, new ItemStack(ModItems.OMNI_GEODE.get()));
        return List.copyOf(result);
    }

    private static void addVanilla(List<DisplayEntry> result, ItemStack geode) {
        for (GeodeLootService.DisplayOutput output : GeodeLootService.getDisplayOutputs(geode)) {
            result.add(new DisplayEntry(geode, output.stack(), output.minCount(), output.maxCount()));
        }
    }
}
