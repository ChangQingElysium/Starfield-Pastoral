package com.stardew.craft.client.gui.common;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

final class DialogueTextWrapper {
    private DialogueTextWrapper() {
    }

    static List<String> wrap(Font font, String text, int maxWidth) {
        return wrap(text, maxWidth, font::width);
    }

    static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        String source = text == null ? "" : text;
        List<String> lines = new ArrayList<>();
        String[] paragraphs = source.split("\n", -1);
        for (String paragraph : paragraphs) {
            wrapParagraph(paragraph, Math.max(1, maxWidth), width, lines);
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    private static void wrapParagraph(
            String paragraph,
            int maxWidth,
            ToIntFunction<String> width,
            List<String> lines
    ) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }

        int lineStart = 0;
        while (lineStart < paragraph.length()) {
            int cursor = lineStart;
            int lastFit = lineStart;
            int lastSpace = -1;

            while (cursor < paragraph.length()) {
                int codePoint = paragraph.codePointAt(cursor);
                int next = cursor + Character.charCount(codePoint);
                if (width.applyAsInt(paragraph.substring(lineStart, next)) > maxWidth) {
                    int lineEnd;
                    if (lastSpace > lineStart) {
                        lineEnd = lastSpace;
                    } else if (lastFit > lineStart) {
                        lineEnd = lastFit;
                    } else {
                        lineEnd = next;
                    }
                    lines.add(paragraph.substring(lineStart, lineEnd));
                    lineStart = skipSpaces(paragraph, lineEnd);
                    break;
                }

                if (Character.isWhitespace(codePoint)) {
                    lastSpace = cursor;
                }
                lastFit = next;
                cursor = next;
            }

            if (cursor >= paragraph.length()) {
                lines.add(paragraph.substring(lineStart));
                return;
            }
        }
    }

    private static int skipSpaces(String text, int start) {
        int cursor = start;
        while (cursor < text.length()) {
            int codePoint = text.codePointAt(cursor);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            cursor += Character.charCount(codePoint);
        }
        return cursor;
    }
}
