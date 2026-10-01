package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.SdvTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class LevelUpMenuTextures {
    private static final SdvTexture OK = SdvTexture.full(overnight("btn_ok"), 64, 64);
    private static final SdvTexture HEADER_RIBBON = SdvTexture.full(overnight("levelup_header_ribbon"), 58, 22);
    private static final SdvTexture[] PROFESSIONS = createProfessions();

    private LevelUpMenuTextures() {
    }

    static void drawOk(GuiGraphics graphics, int x, int y, float scale) {
        OK.drawPixelZoom(graphics, x, y, scale);
    }

    static void drawHeaderRibbon(GuiGraphics graphics, int x, int y, float scale) {
        HEADER_RIBBON.drawPixelZoom(graphics, x, y, scale);
    }

    public static void drawProfession(GuiGraphics graphics, int x, int y, int profession, float scale) {
        PROFESSIONS[profession].drawPixelZoom(graphics, x, y, scale);
    }

    private static SdvTexture[] createProfessions() {
        SdvTexture[] professions = new SdvTexture[36];
        for (int professionIndex = 0; professionIndex < professions.length; professionIndex++) {
            professions[professionIndex] = SdvTexture.full(overnight("profession_" + professionIndex), 16, 16);
        }
        return professions;
    }

    private static ResourceLocation overnight(String name) {
        return new ResourceLocation(StardewCraft.MODID, "textures/gui/overnight/" + name + ".png");
    }
}