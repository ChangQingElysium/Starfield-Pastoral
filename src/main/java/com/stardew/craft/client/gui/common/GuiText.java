package com.stardew.craft.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class GuiText {
    private GuiText() {
    }

    public static Component ellipsize(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int contentWidth = Math.max(0, maxWidth - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(text.getString(), contentWidth) + ellipsis);
    }

    public static void drawCenteredClamped(GuiGraphics graphics, Font font, Component text,
                                           int centerX, int y, int maxWidth, int color, boolean shadow) {
        Component shown = ellipsize(font, text, maxWidth);
        // Vanilla's boolean shadow is opaque black and becomes unreadable over
        // pixel-art panels. This project deliberately renders UI text cleanly.
        graphics.drawString(font, shown, centerX - font.width(shown) / 2, y, color, false);
    }

    public static int drawWrapped(GuiGraphics graphics, Font font, Component text, int x, int y,
                                  int maxWidth, int color, boolean shadow, int maxLines) {
        List<FormattedCharSequence> lines = limitedLines(font, text, maxWidth, maxLines);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    public static int drawWrappedCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y,
                                          int maxWidth, int color, boolean shadow, int maxLines) {
        List<FormattedCharSequence> lines = limitedLines(font, text, maxWidth, maxLines);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, centerX - font.width(line) / 2, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    public static int wrappedLineCount(Font font, Component text, int maxWidth, int maxLines) {
        return limitedLines(font, text, maxWidth, maxLines).size();
    }

    private static List<FormattedCharSequence> limitedLines(Font font, Component text, int maxWidth, int maxLines) {
        int safeWidth = Math.max(1, maxWidth);
        List<FormattedCharSequence> lines = font.split(text, safeWidth);
        if (maxLines > 0 && lines.size() > maxLines) {
            return lines.subList(0, maxLines);
        }
        return lines;
    }
}
