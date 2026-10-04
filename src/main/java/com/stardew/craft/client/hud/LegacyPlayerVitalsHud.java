package com.stardew.craft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

/** Original continuous bars and artwork, sharing the current HUD's data and paused tick animation. */
final class LegacyPlayerVitalsHud {
    // The original 278-pixel group plus three pixels at each side for its shake.
    static final int WIDTH = 284;
    static final int HEIGHT = 18;
    private static final int BAR_WIDTH = 108;
    private static final int BAR_HEIGHT = 12;
    private static final int FILL_WIDTH = 102;
    private static final int FILL_HEIGHT = 6;
    private static final int ICON_SIZE = 12;
    private static final ResourceLocation BAR = texture("stardew_bars");
    private static final ResourceLocation CONTENT = texture("bar_content");
    private static final ResourceLocation HEALTH = texture("health_icon");
    private static final ResourceLocation ENERGY = texture("energy_icon");

    private LegacyPlayerVitalsHud() { }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/" + name + ".png");
    }

    static void renderAt(GuiGraphics graphics, int x, int y, float scale, float energy, int maxEnergy,
                         boolean exhausted, int health, int maxHealth, boolean healthShake,
                         boolean energyShake, float tick) {
        int frame = (int) tick;
        int healthDx = healthShake ? frame % 7 - 3 : 0;
        int healthDy = healthShake ? frame / 2 % 7 - 3 : 0;
        int energyDx = energyShake ? frame % 7 - 3 : 0;
        int energyDy = energyShake ? frame / 2 % 7 - 3 : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            graphics.blit(ENERGY, 3 + energyDx, 3 + energyDy, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            renderBar(graphics, 19 + energyDx, 3 + energyDy, energy, maxEnergy, exhausted,
                    false, tick);
            renderBar(graphics, 157 + healthDx, 3 + healthDy, health, maxHealth, false,
                    true, tick);
            graphics.blit(HEALTH, 269 + healthDx, 3 + healthDy, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static void renderBar(GuiGraphics graphics, int x, int y, float current, int maximum,
                                  boolean exhausted, boolean health, float tick) {
        float ratio = Mth.clamp(current / Math.max(1, maximum), 0.0F, 1.0F);
        int filled = fillWidth(current, maximum);
        int color = ratio >= 0.5F ? 0x00FF00 : ratio >= 0.2F ? 0xFFFF00 : 0xFF0000;
        graphics.blit(BAR, x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        if (filled > 0) {
            float alpha = health && ratio < 0.2F ? 0.5F + 0.5F * (float) Math.sin(tick / 4.0F) : 1.0F;
            graphics.setColor((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                    (color & 255) / 255.0F, alpha);
            graphics.blit(CONTENT, x + 3, y + 3, 0, 0, filled, FILL_HEIGHT, FILL_WIDTH, FILL_HEIGHT);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (exhausted) graphics.fill(x + 3, y + 3, x + 3 + FILL_WIDTH, y + 3 + FILL_HEIGHT, 0x80808080);
        String text = String.format(Locale.ROOT, "%.0f/%d", current, maximum);
        var font = Minecraft.getInstance().font;
        int textX = x + BAR_WIDTH / 2 - font.width(text) / 2;
        int textY = y + BAR_HEIGHT / 2 - 4;
        graphics.drawString(font, text, textX + 1, textY, 0xFF000000, false);
        graphics.drawString(font, text, textX - 1, textY, 0xFF000000, false);
        graphics.drawString(font, text, textX, textY + 1, 0xFF000000, false);
        graphics.drawString(font, text, textX, textY - 1, 0xFF000000, false);
        int textColor = exhausted ? 0xFFAAAAAA : health && ratio < 0.3F ? 0xFFFF5555 : 0xFFFFFFFF;
        graphics.drawString(font, text, textX, textY, textColor, false);
    }

    static int fillWidth(float current, int maximum) {
        return (int) (FILL_WIDTH * Mth.clamp(current / Math.max(1, maximum), 0.0F, 1.0F));
    }
}
