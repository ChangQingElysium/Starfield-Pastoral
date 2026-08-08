package com.stardew.craft.client.gui.common;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Visual half of SDV's null-texture TextBox; the EditBox still owns input and focus. */
public final class SdvEditBoxRenderer {
    private static final ResourceLocation TEXT_BOX = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/textbox_frame.png");
    private static final int TEXT_BOX_WIDTH = 192;
    private static final int TEXT_BOX_HEIGHT = 48;
    private static final int TEXT_SHADOW = 0xFFDD9454;

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
        drawTextWithShadow(graphics, font, text, x, y, scale, color, false);
        if (field.isFocused() && System.currentTimeMillis() % 1000L >= 500L) {
            int caretX = x + SdvFontAdapter.width(font, text, scale) + Math.max(1, Math.round(2.0F / guiScale));
            int caretW = Math.max(1, Math.round(4.0F / guiScale));
            int caretH = Math.max(1, Math.round(32.0F / guiScale));
            graphics.fill(caretX, y, caretX + caretW, y + caretH, color);
        }
    }

    /** TextBox.Draw when LooseSprites/textBox is supplied (smallFont call sites). */
    public static void drawTextured(GuiGraphics graphics, Font font, EditBox field,
                                    int x, int y, int width, int height, float guiScale, int color) {
        int edge = Math.max(1, Math.round(16.0F / guiScale));
        int middleWidth = Math.max(0, width - edge * 2);
        graphics.blit(TEXT_BOX, x, y, edge, height,
                0, 0, 16, TEXT_BOX_HEIGHT, TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT);
        if (middleWidth > 0) {
            graphics.blit(TEXT_BOX, x + edge, y, middleWidth, height,
                    16, 0, 4, TEXT_BOX_HEIGHT, TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT);
        }
        graphics.blit(TEXT_BOX, x + width - edge, y, edge, height,
                TEXT_BOX_WIDTH - 16, 0, 16, TEXT_BOX_HEIGHT, TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT);

        float textScale = 3.0F / Math.max(1.0F, guiScale);
        int maxTextWidth = Math.max(1, width - edge * 2);
        int unscaledWidth = Math.max(1, (int) Math.floor(maxTextWidth / textScale));
        String visible = field.getValue();
        while (!visible.isEmpty() && font.width(visible) > unscaledWidth) {
            visible = visible.substring(Character.charCount(visible.codePointAt(0)));
        }

        int textX = x + edge;
        int textY = y + Math.max(1, Math.round(12.0F / guiScale));
        Component text = Component.literal(visible);
        drawTextWithShadow(graphics, font, text, textX, textY, textScale, color, true);
        if (field.isFocused() && System.currentTimeMillis() % 1000L >= 500L) {
            int caretX = textX + SdvFontAdapter.width(font, text, textScale)
                    + Math.max(1, Math.round(2.0F / guiScale));
            int caretY = y + Math.max(1, Math.round(8.0F / guiScale));
            int caretW = Math.max(1, Math.round(4.0F / guiScale));
            int caretH = Math.max(1, Math.round(32.0F / guiScale));
            graphics.fill(caretX, caretY, caretX + caretW, caretY + caretH, color);
        }
    }

    private static void drawTextWithShadow(GuiGraphics graphics, Font font, Component text,
                                           int x, int y, float scale, int color,
                                           boolean compactShadow) {
        float shadowX = compactShadow ? -2.0F / 3.0F : -1.0F;
        float shadowY = compactShadow ? 2.0F / 3.0F : 1.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        drawAt(graphics, font, text, shadowX, shadowY, TEXT_SHADOW);
        drawAt(graphics, font, text, shadowX, 0.0F, TEXT_SHADOW);
        drawAt(graphics, font, text, 0.0F, shadowY, TEXT_SHADOW);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawAt(GuiGraphics graphics, Font font, Component text,
                               float x, float y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
