package com.stardew.craft.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Locale;

/** Maps Minecraft font metrics onto SDV's dialogueFont and smallFont metrics. */
public final class SdvFontAdapter {
    public enum Style {
        DIALOGUE,
        SMALL,
        SPRITE_TEXT
    }

    private SdvFontAdapter() {
    }

    public static float scale(Font font, String languageCode, float guiScale, Style style) {
        String language = languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
        String probe;
        float sourceAdvance;
        if (language.startsWith("zh")) {
            probe = "年龄阿豆鸭子家畜名字";
            sourceAdvance = switch (style) {
                case SMALL -> 18.0F;
                case DIALOGUE -> 25.0F;
                case SPRITE_TEXT -> 32.59F;
            };
        } else if (language.startsWith("ja")) {
            probe = "あいうえおカキクケコ";
            sourceAdvance = switch (style) {
                case SMALL -> 23.0F;
                case DIALOGUE -> 29.0F;
                case SPRITE_TEXT -> 41.20F;
            };
        } else if (language.startsWith("ko")) {
            probe = "가나다라마바사아자차";
            sourceAdvance = switch (style) {
                case SMALL -> 30.9F;
                case DIALOGUE -> 35.9F;
                case SPRITE_TEXT -> 44.09F;
            };
        } else if (language.startsWith("ru")) {
            probe = "АБВГДЕабвгде";
            sourceAdvance = style == Style.SPRITE_TEXT ? 24.09F : 15.47F;
        } else {
            probe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
            sourceAdvance = switch (style) {
                case SMALL -> 14.7F;
                case DIALOGUE -> 21.5F;
                case SPRITE_TEXT -> 24.0F;
            };
        }

        int minecraftWidth = Math.max(1, font.width(probe));
        int codePoints = Math.max(1, probe.codePointCount(0, probe.length()));
        float targetGuiWidth = sourceAdvance * codePoints / Math.max(1.0F, guiScale);
        return Math.max(0.35F, Math.min(6.0F, targetGuiWidth / minecraftWidth));
    }

    public static int lineStep(String languageCode, float guiScale, Style style) {
        String language = languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
        float sourceLineSpacing;
        if (language.startsWith("zh")) {
            sourceLineSpacing = style == Style.SMALL ? 28.0F : 38.0F;
        } else if (language.startsWith("ja")) {
            sourceLineSpacing = style == Style.SMALL ? 24.0F : 30.0F;
        } else if (language.startsWith("ko")) {
            sourceLineSpacing = style == Style.SMALL ? 44.0F : 58.0F;
        } else if (language.startsWith("ru")) {
            sourceLineSpacing = 33.0F;
        } else {
            sourceLineSpacing = style == Style.SMALL ? 33.0F : 50.0F;
        }
        return Math.max(1, Math.round(sourceLineSpacing / Math.max(1.0F, guiScale)));
    }

    public static int width(Font font, Component text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    public static void draw(GuiGraphics graphics, Font font, Component text,
                            int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text,
                            int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
