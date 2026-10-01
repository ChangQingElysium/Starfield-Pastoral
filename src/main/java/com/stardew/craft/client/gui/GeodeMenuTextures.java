package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.SdvTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class GeodeMenuTextures {
    private static final SdvTexture GEODE_SPOT_BACKGROUND = SdvTexture.full(geode("geode_spot_background"), 140, 78);
    private static final SdvTexture GEODE_OK_BUTTON = SdvTexture.full(geode("geode_ok_button"), 64, 64);

    private GeodeMenuTextures() {
    }

    public static void drawGeodeSpotBackground(GuiGraphics graphics, int x, int y, float scale) {
        GEODE_SPOT_BACKGROUND.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawOkButton(GuiGraphics graphics, int x, int y, float scale) {
        GEODE_OK_BUTTON.drawPixelZoom(graphics, x, y, scale);
    }

    private static ResourceLocation geode(String name) {
        return new ResourceLocation(StardewCraft.MODID, "textures/gui/geode/" + name + ".png");
    }
}