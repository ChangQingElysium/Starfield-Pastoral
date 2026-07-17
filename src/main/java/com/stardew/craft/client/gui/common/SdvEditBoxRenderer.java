package com.stardew.craft.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Visual half of SDV's null-texture TextBox; the EditBox still owns input and focus. */
public final class SdvEditBoxRenderer {
    private SdvEditBoxRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font font, EditBox field,
                            int x, int y, int maxWidth, float scale, float guiScale, int color) {
        String visible = field.getValue();
        int unscaledWidth = Math.max(1, (int) Math.floor(maxWidth / scale));
        while (!visible.isEmpty() && font.width(visible) > unscaledWidth) {
            int firstCodePointLength = Character.charCount(visible.codePointAt(0));
            visible = visible.substring(firstCodePointLength);
        }

        Component text = Component.literal(visible);
        SdvFontAdapter.draw(graphics, font, text, x, y, scale, color);
        if (field.isFocused() && System.currentTimeMillis() % 1000L >= 500L) {
            int caretX = x + SdvFontAdapter.width(font, text, scale) + Math.max(1, Math.round(2.0F / guiScale));
            int caretW = Math.max(1, Math.round(4.0F / guiScale));
            int caretH = Math.max(1, Math.round(font.lineHeight * scale));
            graphics.fill(caretX, y, caretX + caretW, y + caretH, color);
        }
    }
}
