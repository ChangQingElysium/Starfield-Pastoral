package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.production.StardewCookingRecipeDefinition;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.item.ModItems;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Formal JEI cooking category backed by the synchronized cooking definition registry. */
public final class CookingRecipeCategory implements IRecipeCategory<CookingRecipeCategory.DisplayRecipe> {
    public static final RecipeType<DisplayRecipe> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "cooking", DisplayRecipe.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 94;
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 10;
    private static final int POT_X = 77;
    private static final int OUTPUT_X = 138;
    private static final int MAIN_SLOT_Y = 21;
    private static final int NUTRITION_Y = 58;
    private static final int UNLOCK_Y = 76;

    private final IDrawable icon;
    private final Component title;

    public record DisplayRecipe(
            ResourceLocation recipeId,
            List<JeiIngredientStacks.Input> inputs,
            ItemStack output,
            int energy,
            int health,
            Optional<Component> unlockText
    ) {
        public DisplayRecipe {
            inputs = List.copyOf(inputs);
            output = output.copy();
            unlockText = unlockText == null ? Optional.empty() : unlockText;
        }

        public String contentSignature() {
            return recipeId + "|" + JeiRecipeSignatures.inputs(inputs) + '|'
                    + JeiRecipeSignatures.stack(output) + '|' + energy + '|' + health + '|'
                    + unlockText.map(text -> text.getContents().toString()).orElse("");
        }
    }

    public CookingRecipeCategory(IGuiHelper guiHelper) {
        ItemStack pot = new ItemStack(ModItems.COOKING_POT.get());
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, pot);
        this.title = Component.translatable("stardewcraft.jei.cooking_recipe");
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
        builder.addSlot(RecipeIngredientRole.CATALYST, POT_X, MAIN_SLOT_Y)
                .addItemStack(new ItemStack(ModItems.COOKING_POT.get()))
                .setSlotName("cooking_pot");
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
        CommonGuiTextures.drawItemSlot18(graphics, POT_X - 1, MAIN_SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, OUTPUT_X - 1, MAIN_SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 56, MAIN_SLOT_Y + 3, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 117, MAIN_SLOT_Y + 3, 1.0F);

        CommonGuiTextures.drawEntryBox(graphics, 8, NUTRITION_Y, WIDTH - 16, 15, 1.0F, false);
        GuiText.drawCenteredClamped(graphics, font,
                Component.translatable("stardewcraft.jei.energy", recipe.energy()),
                50, NUTRITION_Y + 3, 76, JeiDrawHelper.TEXT_BODY, false);
        GuiText.drawCenteredClamped(graphics, font,
                Component.translatable("stardewcraft.jei.health", recipe.health()),
                128, NUTRITION_Y + 3, 72, JeiDrawHelper.TEXT_BODY, false);

        recipe.unlockText().ifPresent(text -> {
            CommonGuiTextures.drawEntryBox(graphics, 8, UNLOCK_Y, WIDTH - 16, 15, 1.0F, false);
            GuiText.drawCenteredClamped(graphics, font, text, WIDTH / 2,
                    UNLOCK_Y + 3, WIDTH - 28, JeiDrawHelper.TEXT_MUTED, false);
            if (mouseX >= 8 && mouseX < WIDTH - 8 && mouseY >= UNLOCK_Y && mouseY < UNLOCK_Y + 15
                    && font.width(text) > WIDTH - 28) {
                graphics.renderTooltip(font, text, (int) mouseX, (int) mouseY);
            }
        });
    }

    public static List<DisplayRecipe> buildAllRecipes() {
        List<DisplayRecipe> recipes = new ArrayList<>();
        for (ResourceLocation recipeId : VanillaCookingRecipeData.getRecipeIds()) {
            StardewCookingRecipeDefinition definition = VanillaCookingRecipeData
                    .getDefinition(recipeId).orElse(null);
            if (definition == null || definition.ingredients().size() > 4) continue;

            ItemStack output = VanillaCookingRecipeData.getOutputStack(recipeId, 1);
            if (output.isEmpty()) continue;
            List<JeiIngredientStacks.Input> inputs = definition.ingredients().stream()
                    .map(JeiIngredientStacks::cooking)
                    .filter(input -> !input.isEmpty())
                    .toList();
            if (inputs.size() != definition.ingredients().size()) continue;

            var data = StardewItemDataApi.resolve(output).orElse(null);
            int energy = data == null ? 0 : data.energy();
            int health = data == null ? 0 : data.health();
            recipes.add(new DisplayRecipe(recipeId, inputs, output, energy, health,
                    JeiUnlockText.knownSource(VanillaCookingRecipeData.storageId(recipeId))));
        }
        return List.copyOf(recipes);
    }
}
