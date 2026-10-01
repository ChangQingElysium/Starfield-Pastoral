package com.stardew.craft.client.gui.common;

import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Reading-size transform for mod text hosted by vanilla HUD lines (action bar, selected item name).
 * PORT(1.20.1): moved out of StardewReadingHudTextMixin because 1.20.1 needs it from two mixins.
 */
public final class StardewReadingHudText {
    private StardewReadingHudText() {}

    /** Pushes the 1.21 transform around (x + width / 2, y); returns false (nothing pushed) for native-size text. */
    public static boolean push(GuiGraphics g, Component text, int x, int y, int width) {
        float requested = StardewFonts.readingScale();
        if (requested == 1 || text == null || !modText(text)) return false;
        float scale = Math.min(requested, Math.max(1, g.guiWidth() - 20) / (float) Math.max(1, width));
        float center = x + width / 2.0F;
        g.pose().pushPose();
        g.pose().translate(center, y, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-center, -y, 0);
        return true;
    }

    static boolean modText(Component text) {
        if (text.getContents() instanceof TranslatableContents translation) {
            if (translation.getKey().contains("stardewcraft")) return true;
            for (Object argument : translation.getArgs()) {
                if (argument instanceof Component child && modText(child)) return true;
            }
        }
        return text.getSiblings().stream().anyMatch(StardewReadingHudText::modText);
    }
}
