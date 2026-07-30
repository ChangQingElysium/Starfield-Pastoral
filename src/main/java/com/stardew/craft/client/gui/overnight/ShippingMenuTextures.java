package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.SdvTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class ShippingMenuTextures {
    private static final SdvTexture COIN = SdvTexture.full(overnight("coin"), 9, 11);
    private static final SdvTexture DIAL_DOTS = SdvTexture.full(overnight("dial_dots"), 7, 11);
    private static final SdvTexture OK = SdvTexture.full(overnight("btn_ok"), 64, 64);
    private static final SdvTexture BACK = SdvTexture.full(overnight("btn_back"), 12, 11);
    private static final SdvTexture FORWARD = SdvTexture.full(overnight("btn_forward"), 12, 11);
    private static final SdvTexture STAR_BACKDROP = SdvTexture.full(overnight("bg_stars"), 639, 195);
    private static final SdvTexture SKY_STRIP = SdvTexture.full(overnight("shipping_sky_strip"), 1, 184);
    private static final SdvTexture GREEN_RAIN_SKY_STRIP = SdvTexture.full(overnight("shipping_green_rain_sky_strip"), 1, 184);
    private static final SdvTexture PLUS_BUTTON = SdvTexture.full(overnight("btn_plus"), 10, 11);
    private static final SdvTexture PLUS_BUTTON_HOVER = SdvTexture.full(overnight("btn_plus_hover"), 10, 11);
    private static final SdvTexture[] DIGITS = new SdvTexture[] {
            SdvTexture.full(overnight("digit_0"), 5, 8),
            SdvTexture.full(overnight("digit_1"), 5, 8),
            SdvTexture.full(overnight("digit_2"), 5, 8),
            SdvTexture.full(overnight("digit_3"), 5, 8),
            SdvTexture.full(overnight("digit_4"), 5, 8),
            SdvTexture.full(overnight("digit_5"), 5, 8),
            SdvTexture.full(overnight("digit_6"), 5, 8),
            SdvTexture.full(overnight("digit_7"), 5, 8),
            SdvTexture.full(overnight("digit_8"), 5, 8),
            SdvTexture.full(overnight("digit_9"), 5, 8)
    };

    private ShippingMenuTextures() {
    }

    static void drawCoin(GuiGraphics graphics, int x, int y, float scale) {
        COIN.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawDialDots(GuiGraphics graphics, int x, int y, float scale) {
        DIAL_DOTS.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawOk(GuiGraphics graphics, int x, int y, float scale) {
        OK.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawBack(GuiGraphics graphics, int x, int y, float scale) {
        BACK.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawForward(GuiGraphics graphics, int x, int y, float scale) {
        FORWARD.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawStarBackdrop(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        STAR_BACKDROP.drawPixelZoomTint(graphics, x, y, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    static void drawSkyStrip(GuiGraphics graphics, int width, int height, boolean greenRain, float red, float green, float blue, float alpha) {
        SdvTexture texture = greenRain ? GREEN_RAIN_SKY_STRIP : SKY_STRIP;
        texture.drawStretchedTint(graphics, 0, 0, width, height, red, green, blue, alpha);
    }

    static void drawWeatherCloudTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        StardewGuiUtil.drawFromCursorsTint(
            graphics, x, y, 643, 1142, 61, 53, scale, red, green, blue, alpha);
    }

    static void drawLandBackTint(GuiGraphics graphics, int x, int y, boolean winter, float scale, float red, float green, float blue, float alpha) {
        StardewGuiUtil.drawFromCursorsTintFlipped(
            graphics, x, y, 0, winter ? 1034 : 737, 639, 48,
            scale, red, green, blue, alpha);
    }

    static void drawLandFrontTint(GuiGraphics graphics, int x, int y, boolean winter, float scale, float red, float green, float blue, float alpha) {
        StardewGuiUtil.drawFromCursorsTint(
            graphics, x, y, 0, winter ? 1034 : 737, 639, 32,
            scale, red, green, blue, alpha);
    }

    static void drawShippingBin(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        StardewGuiUtil.drawFromCursorsTint(
            graphics, x, y, 653, 880, 10, 10, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    static void drawFullMoon(GuiGraphics graphics, int x, int y, float scale, float alpha) {
        StardewGuiUtil.drawFromCursorsTint(
            graphics, x, y, 642, 835, 43, 43, scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    static void drawMoonFace(GuiGraphics graphics, int x, int y, boolean blink, float scale, float alpha) {
        StardewGuiUtil.drawFromCursorsTint(
            graphics, x, y, 685, blink ? 865 : 844, 19, 21,
            scale, 1.0f, 1.0f, 1.0f, alpha);
    }

    static void drawPlusButton(GuiGraphics graphics, int x, int y, boolean hovering, float scale) {
        SdvTexture texture = hovering ? PLUS_BUTTON_HOVER : PLUS_BUTTON;
        texture.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawDigit(GuiGraphics graphics, int x, int y, int digit, float scale, float red, float green, float blue, float alpha) {
        DIGITS[Math.max(0, Math.min(9, digit))].drawPixelZoomTint(graphics, x, y, scale, red, green, blue, alpha);
    }

    private static ResourceLocation overnight(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/" + name + ".png");
    }
}
