package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.player.StardewCraftingRecipeData.RecipeEntry;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JEI projection of the real Stardew crafting registry. */
public final class StardewCraftingCategory implements IRecipeCategory<StardewCraftingCategory.DisplayRecipe> {
    public static final RecipeType<DisplayRecipe> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "stardew_crafting", DisplayRecipe.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 76;
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 10;
    private static final int OUTPUT_X = 138;
    private static final int MAIN_SLOT_Y = 21;
    private static final int BAND_Y = 56;

    private final IDrawable icon;
    private final Component title;

    public record DisplayRecipe(
            String recipeId,
            List<JeiIngredientStacks.Input> inputs,
            ItemStack output,
            Optional<Component> unlockText
    ) {
        public DisplayRecipe {
            inputs = List.copyOf(inputs);
            output = output.copy();
            unlockText = unlockText == null ? Optional.empty() : unlockText;
        }

        public String contentSignature() {
            return recipeId + '|' + JeiRecipeSignatures.inputs(inputs) + '|'
                    + JeiRecipeSignatures.stack(output) + '|'
                    + unlockText.map(text -> text.getContents().toString()).orElse("");
        }
    }

    public StardewCraftingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.drawableBuilder(
                        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                                "textures/gui/common/game_menu_tab_4.png"),
                        0, 0, 16, 16)
                .setTextureSize(16, 16)
                .build();
        this.title = Component.translatable("stardewcraft.jei.stardew_crafting");
    }

    @Override
    public RecipeType<DisplayRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, DisplayRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size() && i < 4; i++) {
            JeiInputGrid.Position position = JeiInputGrid.position(
                    recipe.inputs().size(), i, INPUT_X, INPUT_Y);
            builder.addSlot(RecipeIngredientRole.INPUT, position.x(), position.y())
                    .addItemStacks(recipe.inputs().get(i).stacks())
                    .setSlotName("input_" + i);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, MAIN_SLOT_Y)
                .addItemStack(recipe.output())
                .setSlotName("output");
    }

    @Override
    public void draw(DisplayRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, 0, 0, WIDTH, HEIGHT, 1.0F);

        for (int i = 0; i < recipe.inputs().size() && i < 4; i++) {
            JeiInputGrid.Position position = JeiInputGrid.position(
                    recipe.inputs().size(), i, INPUT_X, INPUT_Y);
            CommonGuiTextures.drawItemSlot18(graphics,
                    position.x() - 1, position.y() - 1, 1.0F);
        }
        CommonGuiTextures.drawItemSlot18(graphics, OUTPUT_X - 1, MAIN_SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 91, MAIN_SLOT_Y + 3, 1.0F);

        recipe.unlockText().ifPresent(text -> {
            CommonGuiTextures.drawEntryBox(graphics, 8, BAND_Y, WIDTH - 16, 15, 1.0F, false);
            GuiText.drawCenteredClamped(graphics, font, text, WIDTH / 2,
                    BAND_Y + 3, WIDTH - 28, JeiDrawHelper.TEXT_BODY, false);
            if (mouseX >= 8 && mouseX < WIDTH - 8 && mouseY >= BAND_Y && mouseY < BAND_Y + 15
                    && font.width(text) > WIDTH - 28) {
                graphics.renderTooltip(font, text, (int) mouseX, (int) mouseY);
            }
        });
    }

    public static List<DisplayRecipe> buildAllRecipes() {
        List<DisplayRecipe> result = new ArrayList<>();
        for (RecipeEntry entry : StardewCraftingRecipeData.getRecipes()) {
            ItemStack output = StardewCraftingRecipeData.getOutputStack(entry.id());
            if (output.isEmpty()) continue;

            List<JeiIngredientStacks.Input> inputs = StardewCraftingRecipeData
                    .getIngredientEntries(entry.id()).stream()
                    .map(JeiIngredientStacks::crafting)
                    .filter(input -> !input.isEmpty())
                    .toList();
            if (inputs.isEmpty() || inputs.size() > 4) continue;
            result.add(new DisplayRecipe(entry.id(), inputs, output, JeiUnlockText.crafting(entry)));
        }
        return List.copyOf(result);
    }
}
