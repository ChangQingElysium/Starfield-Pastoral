package com.stardew.craft.client.hud;

import com.stardew.craft.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Shared screen-space presentation for nearby world interaction hints. */
public final class InteractionHintHud {
    private static final int MAX_TEXT_WIDTH = 220;
    private static final int SCREEN_MARGIN = 8;
    private static final int HORIZONTAL_PADDING = 7;
    private static final int VERTICAL_PADDING = 4;
    private static final int LINE_GAP = 1;

    private InteractionHintHud() {
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            Component action,
            Component destination,
            int accentRgb,
            float alpha
    ) {
        if (alpha <= 0.01F || graphics.guiWidth() <= SCREEN_MARGIN * 2) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof StardewHudLayoutEditorScreen) {
            return;
        }

        int maxTextWidth = Math.min(MAX_TEXT_WIDTH,
                Config.HudElement.INTERACTION_HINT.baseWidth() - HORIZONTAL_PADDING * 2);
        if (maxTextWidth <= 0) {
            return;
        }

        MutableComponent label = action.copy().withStyle(ChatFormatting.WHITE);
        if (!destination.getString().isBlank()) {
            label.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY));
            label.append(destination.copy().withStyle(ChatFormatting.WHITE));
        }
        List<FormattedCharSequence> lines = font.split(label, maxTextWidth);

        int contentWidth = widest(font, lines);
        int panelWidth = Math.min(Config.HudElement.INTERACTION_HINT.baseWidth(),
                contentWidth + HORIZONTAL_PADDING * 2);
        int lineStep = font.lineHeight + LINE_GAP;
        int contentHeight = lines.size() * lineStep - LINE_GAP;
        int panelHeight = VERTICAL_PADDING * 2 + Math.max(font.lineHeight, contentHeight);

        int x = (Config.HudElement.INTERACTION_HINT.baseWidth() - panelWidth) / 2;
        int y = (Config.HudElement.INTERACTION_HINT.baseHeight() - panelHeight) / 2;

        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.INTERACTION_HINT, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        graphics.pose().translate(placement.x(), placement.y(), 0.0F);
        graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);

        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int borderAlpha = Math.round(Math.min(1.0F, alpha) * 0.9F * 255.0F);
        int borderColor = (borderAlpha << 24) | (accentRgb & 0xFFFFFF);
        int fillAlpha = Math.round(Math.min(1.0F, alpha) * 0.18F * 255.0F);
        int fillColor = (fillAlpha << 24) | (accentRgb & 0xFFFFFF);
        graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, fillColor);
        graphics.fill(x, y, x + panelWidth, y + 1, borderColor);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, borderColor);
        graphics.fill(x, y + 1, x + 1, y + panelHeight - 1, borderColor);
        graphics.fill(x + panelWidth - 1, y + 1, x + panelWidth, y + panelHeight - 1, borderColor);

        int textX = x + HORIZONTAL_PADDING;
        int textY = y + VERTICAL_PADDING;
        int textColor = (alphaByte << 24) | 0xFFFFFF;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, textX, textY, textColor, false);
            textY += lineStep;
        }
        graphics.pose().popPose();
    }

    @SafeVarargs
    private static int widest(Font font, List<FormattedCharSequence>... lineGroups) {
        int width = 0;
        for (List<FormattedCharSequence> lines : lineGroups) {
            for (FormattedCharSequence line : lines) {
                width = Math.max(width, font.width(line));
            }
        }
        return width;
    }
}
