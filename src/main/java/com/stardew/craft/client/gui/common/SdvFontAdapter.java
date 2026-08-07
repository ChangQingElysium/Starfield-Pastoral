package com.stardew.craft.client.gui.common;

import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Maps Minecraft font metrics onto SDV's dialogueFont and smallFont metrics. */
public final class SdvFontAdapter {
    public enum Style {
        DIALOGUE,
        SMALL,
        SPRITE_TEXT,
        SPRITE_TEXT_COLORED
    }

    private SdvFontAdapter() {
    }

    public static float scale(Font font, String languageCode, float guiScale, Style style) {
        // Providers are normalized so SmallFont's 28px runtime line spacing
        // occupies MC's 9–10px text row. Undo that normalization when drawing
        // a source-sized Stardew screen. SpriteText's FontPixelZoom is already
        // part of the provider scale.
        return 3.0F / Math.max(1.0F, guiScale);
    }

    public static int lineStep(String languageCode, float guiScale, Style style) {
        StardewFonts.Role role = switch (style) {
            case DIALOGUE -> StardewFonts.Role.DIALOGUE;
            case SMALL -> StardewFonts.Role.SMALL;
            case SPRITE_TEXT -> StardewFonts.Role.SPRITE_TEXT;
            case SPRITE_TEXT_COLORED -> StardewFonts.Role.SPRITE_TEXT_COLORED;
        };
        return Math.max(1, Math.round(
                StardewFonts.lineHeight(role) * 3.0F / Math.max(1.0F, guiScale)));
    }

    public static int width(Font font, Component text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    public static int width(Font ignored, Component text, float scale, Style style) {
        return Math.round(font(style).width(text) * scale);
    }

    public static void draw(GuiGraphics graphics, Font font, Component text,
                            int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void draw(GuiGraphics graphics, Font ignored, Component text,
                            int x, int y, float scale, int color, Style style) {
        if (style == Style.SPRITE_TEXT) {
            color = (color & 0xFF000000) | StardewFonts.spriteTextDefaultRgb();
        }
        draw(graphics, font(style), text, x, y, scale, color);
    }

    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text,
                            int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void draw(GuiGraphics graphics, Font ignored, FormattedCharSequence text,
                            int x, int y, float scale, int color, Style style) {
        if (style == Style.SPRITE_TEXT) {
            color = (color & 0xFF000000) | StardewFonts.spriteTextDefaultRgb();
        }
        draw(graphics, font(style), text, x, y, scale, color);
    }

    public static Font font(Style style) {
        return switch (style) {
            case DIALOGUE -> StardewFonts.dialogue();
            case SMALL -> StardewFonts.small();
            case SPRITE_TEXT -> StardewFonts.spriteText();
            case SPRITE_TEXT_COLORED -> StardewFonts.spriteTextColored();
        };
    }
}
