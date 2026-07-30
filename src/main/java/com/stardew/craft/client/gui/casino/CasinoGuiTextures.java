package com.stardew.craft.client.gui.casino;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class CasinoGuiTextures {
    static final ResourceLocation CARD_FACE = texture("card_face");
    static final ResourceLocation CARD_BACK = texture("card_back");
    static final ResourceLocation CALICO_BAT = texture("calico_bat");
    static final ResourceLocation SLOT_WINDOW = texture("slot_window");
    static final ResourceLocation SLOT_FRAME = texture("slot_frame");
    static final ResourceLocation SLOT_PAYOUT_BOX = texture("slot_payout_box");
    static final ResourceLocation SLOT_BACKDROP_BOX = texture("slot_backdrop_box");
    static final ResourceLocation SLOT_GRADIENT_LINE = texture("slot_gradient_line");
    private static final ResourceLocation[] SLOT_ICONS = new ResourceLocation[8];

    static {
        for (int index = 0; index < SLOT_ICONS.length; index++) {
            SLOT_ICONS[index] = texture("slot_icon_" + index);
        }
    }

    private CasinoGuiTextures() {
    }

    static void drawCard(
            GuiGraphics graphics, boolean hidden, int x, int y, int width, int height, float pixelZoom
    ) {
        ResourceLocation texture = hidden ? CARD_BACK : CARD_FACE;
        StardewGuiUtil.drawTextureBox(
                graphics, texture, 15, 15, 0, 0, 15, 15,
                x, y, width, height, pixelZoom, true);
    }

    static void drawCard(GuiGraphics graphics, boolean hidden, int x, int y, int width, int height) {
        drawCard(graphics, hidden, x, y, width, height, 2.0F);
    }

    static void drawPayoutBox(
            GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
            float pixelZoom, boolean shadow, float red, float green, float blue, float alpha
    ) {
        StardewGuiUtil.drawTextureBoxTint(
                graphics, texture, 3, 3, 0, 0, 3, 3,
                x, y, width, height, pixelZoom, shadow,
                red, green, blue, alpha, 0);
    }

    static void drawPayoutBox(GuiGraphics graphics, int x, int y, int width, int height) {
        drawPayoutBox(
                graphics, SLOT_PAYOUT_BOX, x, y, width, height,
                2.0F, false, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    static void drawSlotIcon(GuiGraphics graphics, int icon, int x, int y, int size) {
        if (icon < 0 || icon >= SLOT_ICONS.length) {
            return;
        }
        graphics.blit(SLOT_ICONS[icon], x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    static ResourceLocation slotTitle() {
        return localized("slot_title");
    }

    static ResourceLocation slotButton10() {
        return localized("slot_button_10");
    }

    static ResourceLocation slotButton100() {
        return localized("slot_button_100");
    }

    static ResourceLocation slotButtonDone() {
        return localized("slot_button_done");
    }

    static int slotButtonExtraWidth() {
        return switch (language()) {
            case "de_de" -> 3;
            case "fr_fr" -> 6;
            case "hu_hu" -> 4;
            case "it_it" -> 2;
            case "pt_br" -> 10;
            case "ru_ru" -> 9;
            default -> 0;
        };
    }

    private static ResourceLocation localized(String name) {
        String language = language();
        return texture(name + "_" + switch (language) {
            case "de_de", "es_es", "fr_fr", "hu_hu", "it_it", "ja_jp", "ko_kr",
                    "pt_br", "ru_ru", "tr_tr", "zh_cn" -> language;
            default -> "en_us";
        });
    }

    private static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase();
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "textures/gui/casino/" + name + ".png");
    }
}
