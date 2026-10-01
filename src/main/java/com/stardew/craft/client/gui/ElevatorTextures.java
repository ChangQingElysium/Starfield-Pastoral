package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.SdvTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class ElevatorTextures {
    private static final SdvTexture BUTTON = SdvTexture.full(elevator("button"), 10, 10);
    private static final SdvTexture BUTTON_HOVER = SdvTexture.full(elevator("button_hover"), 10, 10);
    private static final SdvTexture[] DIGITS = createDigits();

    private ElevatorTextures() {
    }

    static void drawButtonTint(GuiGraphics graphics, int x, int y, boolean hovered, float scale, float red, float green, float blue, float alpha) {
        SdvTexture texture = hovered ? BUTTON_HOVER : BUTTON;
        texture.drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    static void drawDigitTint(GuiGraphics graphics, int x, int y, int digit, float scale, float red, float green, float blue, float alpha) {
        DIGITS[Math.max(0, Math.min(DIGITS.length - 1, digit))].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    private static SdvTexture[] createDigits() {
        SdvTexture[] digits = new SdvTexture[10];
        for (int digit = 0; digit < digits.length; digit++) {
            digits[digit] = SdvTexture.full(elevator("digit_" + digit), 8, 8);
        }
        return digits;
    }

    private static ResourceLocation elevator(String name) {
        return new ResourceLocation(StardewCraft.MODID, "textures/gui/elevator/" + name + ".png");
    }
}