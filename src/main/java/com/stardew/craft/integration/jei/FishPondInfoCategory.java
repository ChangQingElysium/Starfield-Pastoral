package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.fishpond.service.FishPondDataService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.catalog.StardewItemCatalog;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fish pond source pages. Fish and every concrete product remain real JEI query slots. */
public final class FishPondInfoCategory implements IRecipeCategory<FishPondInfoCategory.DisplayEntry> {
    public static final RecipeType<DisplayEntry> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "fish_pond", DisplayEntry.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 102;
    private static final int INPUT_X = 10;
    private static final int POND_X = 78;
    private static final int OUTPUT_X = 139;
    private static final int SLOT_Y = 13;
    private static final int FIRST_BAND_Y = 47;
    private static final int SECOND_BAND_Y = 65;
    private static final int THIRD_BAND_Y = 83;

    private final IDrawable icon;
    private final Component title;

    public record DisplayEntry(
            ItemStack fish,
            ItemStack output,
            int requiredPopulation,
            double outputChance,
            double dailyMinChance,
            double dailyMaxChance,
            int minCount,
            int maxCount,
            boolean bonusCountPossible
    ) {
        public DisplayEntry {
            fish = fish == null ? ItemStack.EMPTY : fish.copy();
            output = output == null ? ItemStack.EMPTY : output.copy();
            requiredPopulation = Math.max(0, requiredPopulation);
            outputChance = clampChance(outputChance);
            dailyMinChance = clampChance(dailyMinChance);
            dailyMaxChance = clampChance(dailyMaxChance);
            minCount = Math.max(1, minCount);
            maxCount = Math.max(minCount, maxCount);
        }

        @Override
        public ItemStack fish() {
            return fish.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        public String contentSignature() {
            return JeiRecipeSignatures.stack(fish) + '|' + JeiRecipeSignatures.stack(output) + '|'
                    + requiredPopulation + '|' + outputChance + '|' + dailyMinChance + '|'
                    + dailyMaxChance + '|' + minCount + '|' + maxCount + '|' + bonusCountPossible;
        }

        private static double clampChance(double value) {
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }

    public FishPondInfoCategory(IGuiHelper guiHelper) {
        ItemStack pond = new ItemStack(ModItems.FISH_POND_MANAGER.get());
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, pond);
        this.title = Component.translatable("stardewcraft.jei.fish_pond");
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
                .addItemStack(recipe.fish())
                .setSlotName("pond_fish");
        builder.addSlot(RecipeIngredientRole.CATALYST, POND_X, SLOT_Y)
                .addItemStack(new ItemStack(ModItems.FISH_POND_MANAGER.get()))
                .setSlotName("fish_pond");
        ItemStack output = recipe.output();
        output.setCount(recipe.minCount());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y)
                .addItemStack(output)
                .setSlotName("pond_product");
    }

    @Override
    public void draw(DisplayEntry recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, 0, 0, WIDTH, HEIGHT, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, INPUT_X - 1, SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, POND_X - 1, SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, OUTPUT_X - 1, SLOT_Y - 1, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 56, SLOT_Y + 3, 1.0F);
        CommonGuiTextures.drawForwardArrow(graphics, 118, SLOT_Y + 3, 1.0F);

        MutableComponent requirement = Component.translatable(
                "stardewcraft.jei.fish_pond.population", recipe.requiredPopulation());
        requirement.append("  •  ").append(Component.translatable(
                "stardewcraft.jei.fish_pond.output_chance", percent(recipe.outputChance())));
        drawBand(graphics, font, FIRST_BAND_Y, requirement, mouseX, mouseY);

        MutableComponent production = dailyChance(recipe).copy();
        production.append("  •  ").append(amount(recipe));
        drawBand(graphics, font, SECOND_BAND_Y, production, mouseX, mouseY);

        drawBand(graphics, font, THIRD_BAND_Y,
                Component.translatable("stardewcraft.jei.fish_pond.cracker_double"),
                mouseX, mouseY);
    }

    private static Component dailyChance(DisplayEntry recipe) {
        double chanceAtRequiredPopulation = recipe.dailyMinChance();
        if (recipe.dailyMinChance() < recipe.dailyMaxChance()) {
            double populationProgress = Math.min(10, recipe.requiredPopulation()) / 10.0D;
            chanceAtRequiredPopulation +=
                    (recipe.dailyMaxChance() - recipe.dailyMinChance()) * populationProgress;
        }
        if (Math.abs(chanceAtRequiredPopulation - recipe.dailyMaxChance()) < 0.000_001D) {
            return Component.translatable(
                    "stardewcraft.jei.fish_pond.daily_chance", percent(chanceAtRequiredPopulation));
        }
        return Component.translatable(
                "stardewcraft.jei.fish_pond.daily_chance_range",
                percent(chanceAtRequiredPopulation), percent(recipe.dailyMaxChance()));
    }

    private static Component amount(DisplayEntry recipe) {
        if (recipe.bonusCountPossible()) {
            return Component.translatable("stardewcraft.jei.fish_pond.amount_plus", recipe.minCount());
        }
        if (recipe.minCount() != recipe.maxCount()) {
            return Component.translatable(
                    "stardewcraft.jei.fish_pond.amount_range", recipe.minCount(), recipe.maxCount());
        }
        return Component.translatable("stardewcraft.jei.fish_pond.amount", recipe.minCount());
    }

    private static void drawBand(GuiGraphics graphics, Font font, int y, Component text,
                                 double mouseX, double mouseY) {
        CommonGuiTextures.drawEntryBox(graphics, 8, y, WIDTH - 16, 15, 1.0F, false);
        GuiText.drawCenteredClamped(graphics, font, text, WIDTH / 2, y + 3,
                WIDTH - 28, JeiDrawHelper.TEXT_BODY, false);
        if (mouseX >= 8 && mouseX < WIDTH - 8 && mouseY >= y && mouseY < y + 15
                && font.width(text) > WIDTH - 28) {
            graphics.renderTooltip(font, text, (int) mouseX, (int) mouseY);
        }
    }

    private static String percent(double chance) {
        double value = chance * 100.0D;
        return Math.abs(value - Math.rint(value)) < 0.000_001D
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    public static List<DisplayEntry> buildAllEntries() {
        List<DisplayEntry> result = new ArrayList<>();
        FishPondDataService pondData = FishPondDataService.get();
        for (var fishItem : StardewItemCatalog.visibleItems()) {
            ItemStack fish = new ItemStack(fishItem);
            for (FishPondDataService.DisplayProduction production : pondData.getDisplayProductions(fish)) {
                result.add(new DisplayEntry(
                        fish,
                        production.output(),
                        production.requiredPopulation(),
                        production.outputChance(),
                        production.dailyMinChance(),
                        production.dailyMaxChance(),
                        production.minCount(),
                        production.maxCount(),
                        production.bonusCountPossible()));
            }
        }
        result.sort(Comparator.comparing(DisplayEntry::contentSignature));
        return List.copyOf(result);
    }
}
