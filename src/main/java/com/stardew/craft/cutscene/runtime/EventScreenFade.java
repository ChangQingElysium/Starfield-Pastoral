package com.stardew.craft.cutscene.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.client.TemporaryGuiVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Simple screen fade overlay for the cutscene event system.
 * Separate from the CC {@link com.stardew.craft.communitycenter.cutscene.ScreenFade}
 * to avoid coupling.
 */
@OnlyIn(Dist.CLIENT)
public final class EventScreenFade {

    private EventScreenFade() {}

    private static float alpha = 0f;
    private static float alphaPerTick = 0f;
    private static boolean fadingOut = false;
    private static boolean active = false;

    /** 屏幕基本变黑时强制隐藏 HUD（含 hotbar 物品、状态栏、自定义任务 HUD 等）。 */
    private static final float HIDE_HUD_THRESHOLD = 0.5f;
    private static boolean hidingGui = false;

    public static void startFadeToBlack(int ticks) {
        alpha = 0f;
        alphaPerTick = 1.0f / ticks;
        fadingOut = true;
        active = true;
    }

    public static void startFadeFromBlack(int ticks) {
        alpha = 1f;
        alphaPerTick = 1.0f / ticks;
        fadingOut = false;
        active = true;
    }

    /** Keeps the frame black until a later fade-from-black command takes ownership. */
    public static void holdBlack() {
        alpha = 1f;
        alphaPerTick = 0f;
        fadingOut = true;
        active = true;
        updateHideGui();
    }

    public static void tick() {
        if (active) {
            if (fadingOut) {
                alpha += alphaPerTick;
                if (alpha >= 1f) {
                    alpha = 1f;
                }
            } else {
                alpha -= alphaPerTick;
                if (alpha <= 0f) {
                    alpha = 0f;
                    active = false;
                }
            }
        }
        updateHideGui();
    }

    /**
     * 屏幕基本变黑时强制 {@code mc.options.hideGui = true}，让所有 vanilla 层
     * （hotbar 物品、生命/经验/食物条）和已正确尊重 hideGui 的自定义 HUD（如 QuestIconHud）
     * 一并隐藏。
     *
     * 通过共享所有权协调器与剧情播放器共存，最后一个临时隐藏者退出时才恢复玩家原始设置。
     */
    private static void updateHideGui() {
        boolean wantHide = active && alpha >= HIDE_HUD_THRESHOLD;
        if (wantHide) {
            hidingGui = true;
            TemporaryGuiVisibility.acquire(TemporaryGuiVisibility.Owner.SCREEN_FADE);
        } else if (hidingGui) {
            hidingGui = false;
            TemporaryGuiVisibility.release(TemporaryGuiVisibility.Owner.SCREEN_FADE);
        }
    }

    public static void render(GuiGraphics g) {
        if (!active && alpha <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int a = (int) (alpha * 255) << 24;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, a); // black with alpha
        RenderSystem.disableBlend();
    }

    public static void clear() {
        alpha = 0f;
        active = false;
        if (hidingGui) {
            hidingGui = false;
            TemporaryGuiVisibility.release(TemporaryGuiVisibility.Owner.SCREEN_FADE);
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isHidingGui() {
        return hidingGui;
    }
}
