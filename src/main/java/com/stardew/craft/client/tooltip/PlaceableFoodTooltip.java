package com.stardew.craft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.StardewCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

/** A full-height pixel badge, so the 16px icon cannot collide with adjacent text lines. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class PlaceableFoodTooltip implements TooltipComponent, ClientTooltipComponent {
    private static final ResourceLocation ICON = new ResourceLocation(
            StardewCraft.MODID, "textures/gui/tooltip/placeable_food.png");
    private static final Component LABEL = Component.translatable("tooltip.stardewcraft.placeable_food")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

    public static boolean supportsPlacement(ItemStack stack) {
        return com.stardew.craft.item.cooking.PlacedFoodPlacement.blockFor(stack) != null;
    }

    @SubscribeEvent
    public static void onGather(RenderTooltipEvent.GatherComponents event) {
        if (!supportsPlacement(event.getItemStack())) return;
        var elements = event.getTooltipElements();
        int index = Math.min(2, elements.size());
        elements.add(index, Either.right(new PlaceableFoodTooltip()));
        var options = Minecraft.getInstance().options;
        elements.add(index + 1, Either.left(Component.translatable("tooltip.stardewcraft.place_food_controls",
                options.keyShift.getTranslatedKeyMessage(), options.keyUse.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.GRAY)));
    }

    @Override
    public int getHeight() {
        return 20;
    }

    @Override
    public int getWidth(Font font) {
        return 21 + font.width(LABEL);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.blit(ICON, x, y + 1, 0, 0, 16, 16, 16, 16);
        graphics.drawString(font, LABEL, x + 21, y + 1 + Math.max(0, (16 - font.lineHeight) / 2), 0xFFFFFFFF);
    }
}
