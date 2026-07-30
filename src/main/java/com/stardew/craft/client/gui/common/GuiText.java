package com.stardew.craft.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class GuiText {
    private GuiText() {
    }

    public static Component ellipsize(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        if (font.width(ellipsis) > maxWidth) {
            return Component.empty();
        }
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

    /**
     * Draws a fixed-control label without dropping text. Labels wider than the
     * available width are uniformly scaled around their visual center.
     * Descriptive copy should use wrapping instead of this method.
     */
    public static void drawCenteredFitted(
            GuiGraphics graphics,
            Font font,
            Component text,
            int centerX,
            int centerY,
            int maxWidth,
            int color,
            boolean shadow
    ) {
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(
                1.0F,
                Math.max(1, maxWidth) / (float) textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(
                font,
                text,
                -textWidth / 2,
                -font.lineHeight / 2,
                color,
                false);
        graphics.pose().popPose();
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
            List<FormattedCharSequence> shown = new ArrayList<>(
                    lines.subList(0, maxLines));
            String ellipsis = "…";
            int contentWidth = Math.max(
                    0,
                    safeWidth - font.width(ellipsis));
            String lastLine = plainText(shown.get(maxLines - 1));
            String clipped = font.plainSubstrByWidth(
                    lastLine,
                    contentWidth) + ellipsis;
            shown.set(
                    maxLines - 1,
                    FormattedCharSequence.forward(clipped, Style.EMPTY));
            return List.copyOf(shown);
        }
        return lines;
    }

    private static String plainText(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }
}
