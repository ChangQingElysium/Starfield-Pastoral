package com.stardew.craft.communitycenter.cutscene;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 客户端全屏 overlay 效果：淡入淡出/闪白/辉光。
 * 由 {@link CutscenePayload} 触发，在 HUD 渲染后绘制。
 * <p>
 * 使用方式：在 ModClientEvents 中的 RenderGuiLayerEvent.Post 调用 {@link #render(GuiGraphics)}。
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenFade {

    private ScreenFade() {}

    public enum Mode { NONE, FADE_TO_BLACK, FADE_FROM_BLACK, FLASH_WHITE, GLOW }

    private static Mode currentMode = Mode.NONE;
    private static float alpha = 0f;
    private static float targetAlpha = 0f;
    private static float speed = 0f;   // alpha per tick
    private static boolean active = false;
    private static int ticksSincePacket = 0;
    private static final int STALE_EFFECT_TIMEOUT_TICKS = 20 * 15;

    // 当前过场状态 (供外部查询)
    @SuppressWarnings("unused")
    private static byte currentCutsceneType = -1;
    private static byte currentPhase = -1;
    private static int currentAreaId = -1;

    /**
     * 收到服务端过场 packet 后的处理。
     */
    public static void onCutscenePacket(CutscenePayload payload) {
        if (payload.phase() == CutscenePayload.PHASE_END) {
            clear();
            return;
        }

        currentCutsceneType = payload.cutsceneType();
        currentPhase = payload.phase();
        currentAreaId = payload.areaId();
        ticksSincePacket = 0;

        if (payload.cutsceneType() == CutscenePayload.TYPE_AREA_RESTORE) {
            switch (payload.phase()) {
                case CutscenePayload.PHASE_FREEZE -> {
                    // Phase 0: 轻微辉光开始
                    startEffect(Mode.GLOW, 0.15f, 0.01f);
                }
                case CutscenePayload.PHASE_APPEAR -> {
                    // Phase 1: Junimo 出现，辉光增强
                    startEffect(Mode.GLOW, 0.3f, 0.015f);
                }
                case CutscenePayload.PHASE_GLOW -> {
                    // Phase 2: 辉光渐强
                    startEffect(Mode.GLOW, 0.5f, 0.02f);
                }
                case CutscenePayload.PHASE_RESTORE -> {
                    // Phase 3: 全屏闪白 → 淡出
                    startEffect(Mode.FLASH_WHITE, 1.0f, 0.0f);
                    // Vanilla starts junimoStarSong at the same instant the restored
                    // room is applied and the white flash begins.
                    com.stardew.craft.communitycenter.network.BundleClientData.INSTANCE
                            .markInteriorRefurbishedIfComplete();
                    com.stardew.craft.client.sound.StardewMusicManager.playOneShotForCutscene(
                            com.stardew.craft.sound.ModSounds.MUSIC_JUNIMO_STAR_SONG.get());
                }
            }
        }

        // T3.2: Goodbye dance visual effects
        if (payload.cutsceneType() == CutscenePayload.TYPE_GOODBYE_DANCE) {
            switch (payload.phase()) {
                case CutscenePayload.PHASE_FREEZE -> {
                    // Junimos appear — gentle glow
                    startEffect(Mode.GLOW, 0.2f, 0.01f);
                }
                case CutscenePayload.PHASE_APPEAR -> {
                    // Dance — warm glow
                    startEffect(Mode.GLOW, 0.4f, 0.015f);
                }
                case CutscenePayload.PHASE_RESTORE -> {
                    // Farewell — bright flash
                    startEffect(Mode.FLASH_WHITE, 1.0f, 0.0f);
                }
            }
        }
    }

    private static void startEffect(Mode mode, float target, float spd) {
        currentMode = mode;
        targetAlpha = target;
        speed = spd;
        active = true;
        if (mode == Mode.FLASH_WHITE) {
            alpha = 1.0f; // 闪白从满透明度开始
        }
    }

    /** Starts the same full-screen white flash used by restoration cutscenes. */
    public static void startFlashWhite() {
        startEffect(Mode.FLASH_WHITE, 1.0f, 0.0f);
    }

    /**
     * 每帧 tick 更新 (在 render 前调用)
     */
    public static void tick() {
        if (!active) return;

        ticksSincePacket++;
        if (ticksSincePacket > STALE_EFFECT_TIMEOUT_TICKS) {
            clear();
            return;
        }

        if (currentMode == Mode.FLASH_WHITE) {
            // 闪白：alpha 从 1.0 递减到 0
            alpha -= 0.04f; // ~25 tick = 1.25 秒
            if (alpha <= 0f) {
                alpha = 0f;
                active = false;
                currentMode = Mode.NONE;
            }
        } else if (currentMode == Mode.GLOW || currentMode == Mode.FADE_TO_BLACK) {
            // 渐变到目标
            if (alpha < targetAlpha) {
                alpha = Math.min(alpha + speed, targetAlpha);
            } else if (alpha > targetAlpha) {
                alpha = Math.max(alpha - speed, targetAlpha);
            }
        } else if (currentMode == Mode.FADE_FROM_BLACK) {
            alpha -= speed;
            if (alpha <= 0f) {
                alpha = 0f;
                active = false;
                currentMode = Mode.NONE;
            }
        }
    }

    /**
     * 在 HUD 渲染后调用，绘制全屏 overlay。
     */
    public static void render(GuiGraphics g) {
        if (!active || alpha <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int color;
        if (currentMode == Mode.FLASH_WHITE || currentMode == Mode.GLOW) {
            // 白色 overlay
            int a = (int) (alpha * 255) << 24;
            color = a | 0xFFFFFF;
        } else {
            // 黑色 overlay
            int a = (int) (alpha * 255) << 24;
            color = a;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, color);
        RenderSystem.disableBlend();
    }

    /**
     * 清除当前效果。
     */
    public static void clear() {
        active = false;
        currentMode = Mode.NONE;
        alpha = 0f;
        currentCutsceneType = -1;
        currentPhase = -1;
        currentAreaId = -1;
        ticksSincePacket = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static byte getCurrentPhase() {
        return currentPhase;
    }

    public static int getCurrentAreaId() {
        return currentAreaId;
    }
}
