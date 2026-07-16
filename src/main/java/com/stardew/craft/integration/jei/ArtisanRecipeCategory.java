package com.stardew.craft.integration.jei;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.item.quality.QualityHelper;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/** JEI category instance for one fixed artisan machine type. */
@SuppressWarnings("null")
public final class ArtisanRecipeCategory implements IRecipeCategory<ArtisanJeiRecipe> {
    private static final int WIDTH = 176;
    private static final int SLOT_SIZE = 18;

    private final MachineJeiRegistry.Machine machine;
    private final IDrawable icon;
    private final Component title;

    public ArtisanRecipeCategory(IGuiHelper guiHelper, MachineJeiRegistry.Machine machine, ItemStack machineIcon) {
        this.machine = machine;
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, machineIcon);
        this.title = Component.translatable(machine.translationKey());
    }

    @Override
    public RecipeType<ArtisanJeiRecipe> getRecipeType() {
        return machine.recipeType();
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
        return machine.layout().height();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ArtisanJeiRecipe recipe, IFocusGroup focuses) {
        LayoutPositions positions = positions(recipe);
        for (int i = 0; i < recipe.inputs().size(); i++) {
            ArtisanJeiRecipe.Input input = recipe.inputs().get(i);
            int x = i == 0 ? positions.primaryInputX() : positions.auxiliaryInputX();
            builder.addSlot(RecipeIngredientRole.INPUT, x, positions.slotY())
                    .addItemStacks(input.stacks())
                    .setSlotName(input.auxiliary() ? "auxiliary_input" : "primary_input");
        }

        builder.addSlot(RecipeIngredientRole.CATALYST, positions.machineX(), positions.slotY())
                .addItemStack(itemStack(machine.itemId().toString()))
                .setSlotName("machine");

        for (int i = 0; i < recipe.outputs().size(); i++) {
            ArtisanJeiRecipe.Output output = recipe.outputs().get(i);
            int x = positions.outputX() + i * 22;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, positions.slotY())
                    .addItemStacks(output.stacks())
                    .setSlotName("output_" + i);
        }
    }

    @Override
    public void draw(ArtisanJeiRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        int height = getHeight();
        Font font = Minecraft.getInstance().font;
        LayoutPositions positions = positions(recipe);

        CommonGuiTextures.drawTextureBoxNoShadow(graphics, 0, 0, WIDTH, height, 1.0F);

        for (int i = 0; i < recipe.inputs().size(); i++) {
            int x = i == 0 ? positions.primaryInputX() : positions.auxiliaryInputX();
            CommonGuiTextures.drawItemSlot18(graphics, x - 1, positions.slotY() - 1, 1.0F);
        }
        CommonGuiTextures.drawItemSlot18(graphics,
                positions.machineX() - 1, positions.slotY() - 1, 1.0F);
        for (int i = 0; i < recipe.outputs().size(); i++) {
            int x = positions.outputX() + i * 22;
            CommonGuiTextures.drawItemSlot18(graphics, x - 1, positions.slotY() - 1, 1.0F);
        }

        drawFlow(graphics, font, recipe, positions);
        drawMetadata(graphics, font, recipe, height, positions);
    }

    private static void drawFlow(
            GuiGraphics graphics, Font font, ArtisanJeiRecipe recipe, LayoutPositions positions
    ) {
        if (recipe.inputs().size() > 1) {
            graphics.drawString(font, "+", positions.auxiliaryInputX() - 9,
                    positions.slotY() + 5, JeiDrawHelper.TEXT_BODY, false);
        }

        int firstArrowX = positions.machineX() - 20;
        CommonGuiTextures.drawForwardArrow(graphics, firstArrowX, positions.slotY() + 3, 1.0F);
        if (!recipe.outputs().isEmpty()) {
            CommonGuiTextures.drawForwardArrow(graphics,
                    positions.outputX() - 20, positions.slotY() + 3, 1.0F);
        }
    }

    private static void drawMetadata(
            GuiGraphics graphics, Font font, ArtisanJeiRecipe recipe,
            int height, LayoutPositions positions
    ) {
        if (recipe.machine().layout() == MachineJeiRegistry.Layout.RANDOM_OUTPUT) {
            for (int i = 0; i < recipe.outputs().size(); i++) {
                ArtisanJeiRecipe.Output output = recipe.outputs().get(i);
                int centerX = positions.outputX() + i * 22 + 8;
                int detailY = positions.slotY() + 20;
                if (output.minCount() != output.maxCount()) {
                    GuiText.drawCenteredClamped(graphics, font,
                            Component.literal(output.minCount() + "-" + output.maxCount()),
                            centerX, detailY, 22, JeiDrawHelper.TEXT_BODY, false);
                    detailY += font.lineHeight + 1;
                }
                GuiText.drawCenteredClamped(graphics, font, chanceText(output.chance()), centerX,
                        detailY, 22, JeiDrawHelper.TEXT_MUTED, false);
            }
        }

        int bandY = height - 20;
        CommonGuiTextures.drawEntryBox(graphics, 8, bandY, WIDTH - 16, 15, 1.0F, false);
        Component time = Component.translatable(
                "stardewcraft.jei.time", JeiDrawHelper.formatTime(recipe.minutes()));
        Component quality = qualityText(recipe);
        if (quality == null) {
            GuiText.drawCenteredClamped(graphics, font, time, WIDTH / 2,
                    bandY + 3, WIDTH - 28, JeiDrawHelper.TEXT_BODY, false);
            return;
        }

        GuiText.drawCenteredClamped(graphics, font, time, 49,
                bandY + 3, 76, JeiDrawHelper.TEXT_BODY, false);
        GuiText.drawCenteredClamped(graphics, font, quality, 127,
                bandY + 3, 72, JeiDrawHelper.TEXT_MUTED, false);
    }

    private static Component chanceText(double chance) {
        double percent = chance * 100.0D;
        String formatted = percent < 1.0D
                ? String.format(Locale.ROOT, "%.1f", percent)
                : String.valueOf(Math.round(percent));
        return Component.translatable("stardewcraft.jei.chance", formatted);
    }

    private static Component qualityText(ArtisanJeiRecipe recipe) {
        if (recipe.keepInputQuality()) {
            return Component.translatable("stardewcraft.jei.quality.keep");
        }
        if (recipe.outputQuality() < QualityHelper.NORMAL) {
            return null;
        }
        String key = switch (recipe.outputQuality()) {
            case QualityHelper.SILVER -> "silver";
            case QualityHelper.GOLD -> "gold";
            case QualityHelper.IRIDIUM -> "iridium";
            default -> "normal";
        };
        return Component.literal("★ ").append(Component.translatable("stardewcraft.jei.quality." + key));
    }

    private LayoutPositions positions(ArtisanJeiRecipe recipe) {
        return switch (machine.layout()) {
            case AUXILIARY_INPUT -> new LayoutPositions(9, 35, 78, 137, 17);
            case RANDOM_OUTPUT -> new LayoutPositions(10, 10, 68, 110, 17);
            case STANDARD -> recipe.outputs().isEmpty()
                    ? new LayoutPositions(38, 38, 106, 0, 17)
                    : new LayoutPositions(14, 14, 76, 137, 17);
        };
    }

    public static ItemStack itemStack(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private record LayoutPositions(
            int primaryInputX,
            int auxiliaryInputX,
            int machineX,
            int outputX,
            int slotY
    ) {
    }
}
