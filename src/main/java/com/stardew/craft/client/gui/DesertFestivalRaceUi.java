package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;

final class DesertFestivalRaceUi {
    static final ResourceLocation RACERS = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/race/desert_racers.png");
    static final ResourceLocation RACE_BACKGROUND = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/race/race_track_full.png");
    static final int BG_W = 672;
    static final int BG_H = 384;
    static final int TEXT = 0xFFFFF1D0;
    static final int DARK_TEXT = 0xFF3F2A13;
    static final int MUTED = 0xFFBFAF8F;
    static final int PANEL = 0xE0B0713A;
    static final int PANEL_DARK = 0xE05C3417;
    static final int BUTTON = 0xFFE0A756;
    static final int BUTTON_HOVER = 0xFFFFCB6A;
    static final int BUTTON_DISABLED = 0xFF77604A;
    static final int BORDER = 0xFF3B1E0C;
    static final int GOLD = 0xFF9A5A16;

    private DesertFestivalRaceUi() {
    }

    static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        CommonGuiTextures.drawTextureBox(graphics, x, y, w, h, scale(), true);
    }

    static void darkPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        CommonGuiTextures.drawMenuTextureBox(graphics, x, y, w, h, scale(), true);
    }

    static void card(GuiGraphics graphics, int x, int y, int w, int h, boolean selected) {
        if (selected) {
            CommonGuiTextures.drawOptionHighlightBox(graphics, x - 2, y - 2, w + 4, h + 4, scale());
        }
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, x, y, w, h, scale());
    }

    static void button(GuiGraphics graphics, Font font, int mouseX, int mouseY, ButtonHitbox hitbox, Component label, boolean enabled) {
        boolean hover = enabled && hitbox.contains(mouseX, mouseY);
        if (hover) {
            CommonGuiTextures.drawOptionHighlightBox(graphics, hitbox.x() - 2, hitbox.y() - 2, hitbox.w() + 4, hitbox.h() + 4, scale());
        }
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, hitbox.x(), hitbox.y(), hitbox.w(), hitbox.h(), scale());
        if (!enabled) {
            graphics.fill(hitbox.x() + 4, hitbox.y() + 4, hitbox.x() + hitbox.w() - 4, hitbox.y() + hitbox.h() - 4, 0x55706058);
        } else if (hover) {
            graphics.fill(hitbox.x() + 5, hitbox.y() + hitbox.h() - 7, hitbox.x() + hitbox.w() - 5, hitbox.y() + hitbox.h() - 5, 0x99D68B2D);
        }
        drawCentered(graphics, font, label, hitbox.x() + 8, hitbox.y() + 4, hitbox.w() - 16, hitbox.h() - 8,
            enabled ? DARK_TEXT : 0xFF5F5142, false);
    }

    static void menuTitle(GuiGraphics graphics, Font font, Component title, int x, int y, int w) {
        int maxWidth = Math.max(32, w - 48);
        Component fitted = fit(font, title, maxWidth);
        banner(graphics, font, fitted, x + w / 2, y + 18);
    }

    static void menuStat(GuiGraphics graphics, Font font, Component label, Component value, int x, int y, int w) {
        drawFitted(graphics, font, label, x, y, w, 10, GOLD, false, 0.72f);
        drawFitted(graphics, font, value, x, y + 12, w, 10, DARK_TEXT, false, 0.72f);
    }

    static void menuRow(GuiGraphics graphics, Font font, int mouseX, int mouseY, ButtonHitbox hitbox,
                        Component label, boolean enabled, boolean selected) {
        boolean hover = enabled && hitbox.contains(mouseX, mouseY);
        card(graphics, hitbox.x(), hitbox.y(), hitbox.w(), hitbox.h(), selected || hover);
        if (!enabled) {
            graphics.fill(hitbox.x() + 5, hitbox.y() + 5, hitbox.x() + hitbox.w() - 5, hitbox.y() + hitbox.h() - 5, 0x55706058);
        }
        drawFitted(graphics, font, label, hitbox.x() + 12, hitbox.y() + 4, hitbox.w() - 24, hitbox.h() - 8,
            enabled ? DARK_TEXT : 0xFF5F5142, false, 0.68f);
    }

    static int ui(int value) {
        return Math.round(value / (float) StardewGuiViewport.renderScale());
    }

    static void actionCard(GuiGraphics graphics, Font font, int mouseX, int mouseY, ButtonHitbox hitbox,
                           Component title, Component detail, boolean enabled, boolean selected) {
        boolean hover = enabled && hitbox.contains(mouseX, mouseY);
        card(graphics, hitbox.x(), hitbox.y(), hitbox.w(), hitbox.h(), selected || hover);
        if (!enabled) {
            graphics.fill(hitbox.x() + 5, hitbox.y() + 5, hitbox.x() + hitbox.w() - 5, hitbox.y() + hitbox.h() - 5, 0x55706058);
        }
        int titleColor = enabled ? DARK_TEXT : 0xFF5F5142;
        int detailColor = enabled ? GOLD : 0xFF6D6258;
        drawFitted(graphics, font, title, hitbox.x() + 12, hitbox.y() + 8, hitbox.w() - 24, 13, titleColor, false, 0.68f);
        drawFitted(graphics, font, detail, hitbox.x() + 12, hitbox.y() + 25, hitbox.w() - 24, 12, detailColor, false, 0.68f);
        if (selected || hover) {
            graphics.fill(hitbox.x() + hitbox.w() - 8, hitbox.y() + 8, hitbox.x() + hitbox.w() - 5, hitbox.y() + hitbox.h() - 8, 0xAA9A5A16);
        }
    }

    static void labelValue(GuiGraphics graphics, Font font, Component label, Component value, int x, int y, int w) {
        drawFitted(graphics, font, label, x, y, w, 10, GOLD, false, 0.72f);
        drawFitted(graphics, font, value, x, y + 11, w, 10, DARK_TEXT, false, 0.72f);
    }

    static Component fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String value = text.getString();
        while (value.length() > 1 && font.width(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return Component.literal(value + "...");
    }

    static void drawFitted(GuiGraphics graphics, Font font, Component text, int x, int y, int maxW, int maxH, int color, boolean shadow, float minScale) {
        String value = text.getString();
        if (value.isBlank() || maxW <= 0 || maxH <= 0) return;
        int textW = Math.max(1, font.width(text));
        float textScale = Math.min(1.0f, Math.min((float) maxW / textW, (float) maxH / 9.0f));
        if (textScale < minScale && 9.0f * minScale <= maxH) {
            textScale = minScale;
        }
        if (textW * textScale > maxW) {
            text = fit(font, text, Math.max(1, Math.round(maxW / textScale)));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + Math.max(0.0f, (maxH - 9.0f * textScale) / 2.0f), 0);
        graphics.pose().scale(textScale, textScale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    static void drawCentered(GuiGraphics graphics, Font font, Component text, int x, int y, int maxW, int maxH, int color, boolean shadow) {
        if (text.getString().isBlank() || maxW <= 0 || maxH <= 0) return;
        int textW = Math.max(1, font.width(text));
        float textScale = Math.min(1.0f, Math.min((float) maxW / textW, (float) maxH / 9.0f));
        if (textScale < 0.68f && 9.0f * 0.68f <= maxH) {
            textScale = 0.68f;
        }
        if (textW * textScale > maxW) {
            text = fit(font, text, Math.max(1, Math.round(maxW / textScale)));
            textW = Math.max(1, font.width(text));
        }
        float drawX = x + (maxW - textW * textScale) / 2.0f;
        float drawY = y + (maxH - 9.0f * textScale) / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(textScale, textScale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    static Layout layout(int screenW, int screenH, int baseW, int baseH) {
        float layoutScale = Math.min(1.0f, Math.min((float) Math.max(1, screenW - 24) / baseW, (float) Math.max(1, screenH - 24) / baseH));
        int w = Math.max(1, Math.round(baseW * layoutScale));
        int h = Math.max(1, Math.round(baseH * layoutScale));
        return new Layout(screenW / 2 - w / 2, screenH / 2 - h / 2, w, h, layoutScale);
    }

    static ButtonHitbox hitbox(Layout layout, int baseX, int baseY, int baseW, int baseH, String action, int racerIndex, int amount, String roomId) {
        return new ButtonHitbox(layout.x(baseX), layout.y(baseY), layout.w(baseW), layout.h(baseH), action, racerIndex, amount, roomId);
    }

    static void backdrop(GuiGraphics graphics, int width, int height) {
        float scale = Math.max((float) width / BG_W, (float) height / BG_H);
        int drawW = Math.round(BG_W * scale);
        int drawH = Math.round(BG_H * scale);
        int drawX = width / 2 - drawW / 2;
        int drawY = height / 2 - drawH / 2;
        graphics.blit(RACE_BACKGROUND, drawX, drawY, drawW, drawH, 0.0f, 0.0f, BG_W, BG_H, BG_W, BG_H);
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    static void banner(GuiGraphics graphics, Font font, Component text, int centerX, int y) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        text = fit(font, text, Math.max(32, screenW - 48));
        int textWidth = font.width(text);
        CommonGuiTextures.drawScrollBanner(graphics, centerX - textWidth / 2, y - 5, textWidth, scale());
        graphics.drawString(font, text, centerX - textWidth / 2, y, DARK_TEXT, false);
    }

    static void drawRacerIcon(GuiGraphics graphics, int racerIndex, int x, int y, int scale) {
        graphics.blit(RACERS, x, y, 16 * scale, 16 * scale, 64, racerIndex * 16, 16, 16, 112, 80);
    }

    static void drawRacerIconSize(GuiGraphics graphics, int racerIndex, int x, int y, int size) {
        graphics.blit(RACERS, x, y, size, size, 64, racerIndex * 16, 16, 16, 112, 80);
    }

    static Component racerName(int racerIndex) {
        return Component.translatable("stardewcraft.desert_festival.racer." + racerIndex);
    }

    static boolean raceInProgress(DesertFestivalRaceSnapshot snapshot) {
        return snapshot.raceState().equals("STARTING_LINE")
            || snapshot.raceState().equals("READY")
            || snapshot.raceState().equals("SET")
            || snapshot.raceState().equals("GO")
            || snapshot.raceState().startsWith("ANNOUNCE_WINNER");
    }

    static boolean roomRewardPending(DesertFestivalRaceSnapshot.RoomEntry room) {
        return room.playerPayout() > 0 && !room.playerClaimed();
    }

    static void play(DeferredHolder<SoundEvent, SoundEvent> sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0F));
    }

    static void playButton() {
        play(ModSounds.BUTTON_PRESS);
    }

    static void playBack() {
        play(ModSounds.BIG_DESELECT);
    }

    static float scale() {
        return 4.0f / (float) StardewGuiViewport.renderScale();
    }

    record ButtonHitbox(int x, int y, int w, int h, String action, int racerIndex, int amount, String roomId) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        }
    }

    record Layout(int x, int y, int w, int h, float s) {
        int x(int baseX) { return x + Math.round(baseX * s); }
        int y(int baseY) { return y + Math.round(baseY * s); }
        int w(int baseW) { return Math.max(1, Math.round(baseW * s)); }
        int h(int baseH) { return Math.max(1, Math.round(baseH * s)); }
    }
}