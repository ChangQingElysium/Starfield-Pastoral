package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Small JEI-only helpers. Frames, slots and arrows come from real shared GUI textures. */
@SuppressWarnings("null")
public final class JeiDrawHelper {
    public static final int TEXT_TITLE = 0xFF5B3A1F;
    public static final int TEXT_BODY = 0xFF6B5244;
    public static final int TEXT_GOLD = 0xFFB8860B;
    public static final int TEXT_MUTED = 0xFF9E8E7E;

    private static final ResourceLocation GOLD_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/gold_icon.png");
    private static IDrawable goldIconDrawable;

    private JeiDrawHelper() {
    }

    public static void initGoldIcon(IGuiHelper guiHelper) {
        if (goldIconDrawable == null) {
            goldIconDrawable = guiHelper.drawableBuilder(GOLD_ICON, 0, 0, 16, 16)
                    .setTextureSize(16, 16)
                    .build();
        }
    }

    public static int drawGoldAmount(GuiGraphics graphics, Font font, int x, int y, int amount) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y - 1, 0);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        if (goldIconDrawable != null) {
            goldIconDrawable.draw(graphics, 0, 0);
        }
        graphics.pose().popPose();

        String text = amount + "g";
        graphics.drawString(font, text, x + 10, y, TEXT_GOLD, false);
        return 10 + font.width(text);
    }

    public static String formatTime(int minutes) {
        String day = Component.translatable("stardewcraft.jei.time.day_unit").getString();
        String hour = Component.translatable("stardewcraft.jei.time.hour_unit").getString();
        String minute = Component.translatable("stardewcraft.jei.time.minute_unit").getString();
        if (minutes >= 1440) {
            int days = minutes / 1440;
            int remainingHours = (minutes % 1440) / 60;
            return remainingHours > 0 ? days + day + " " + remainingHours + hour : days + day;
        }
        if (minutes >= 60) {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            return remainingMinutes > 0
                    ? hours + hour + " " + remainingMinutes + minute
                    : hours + hour;
        }
        return minutes + minute;
    }
}
