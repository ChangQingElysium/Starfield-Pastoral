package com.stardew.craft.client.hud;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ModKeyMappings;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.quest.StardewQuest;
import com.stardew.craft.quest.network.ClientQuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Random;

/**
 * SDV DayTimeMoneyBox questButton 复刻。
 *
 * 关键：quest icon 与 StardewTimeHud 共用 SDV 4x 像素缩放和右上角锚点。
 *
 * SDV 比例关系:
 *   moneyBox 背景 71×43 source at 4× = 284×172 screen px
 *   questButton   11×14 source at 4× = 44×56  screen px  → icon/bg ratio ≈ 15.5%
 * 我们的逻辑 HUD:
 *   背景 72×57 source px  →  icon 11×14 source px  →  11/72 ≈ 15.3%  ✓ 比例一致
 *
 * exclamation "!" 绘制位置:
 *   SDV: (bounds.X+24, bounds.Y+32) with origin(2,4), bounds=(44,46)
 *   转换: 24/44≈54.5%, 32/46≈69.6% → 在我们的 11×14 icon 上: (6, 10)
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class QuestIconHud {

    // ─── Cursors sprite dimensions ───
    private static final int ICON_W = 11, ICON_H = 14;
    private static final int PING_W = 16, PING_H = 16;

    // ─── 固定缩放比例（与 StardewTimeHud 的 1:1 GUI 坐标体系一致） ───
    private static final float ICON_SCALE = 1.0f;

    // ─── SDV 计时器 ───
    private static int questPulseTimer;
    private static int whenToPulseTimer;
    private static int questPingTimer;

    private static final Random random = new Random();

    public static void pingQuestLog() { questPingTimer = 6000; }
    public static void dismissQuestPing() { questPingTimer = 0; }
    public static void pingNewQuest() { questPulseTimer = 1000; }
    public static void pingQuestComplete() { questPulseTimer = 1000; }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof StardewHudLayoutEditorScreen) return;
        if (mc.options.hideGui || mc.player.isSpectator()) return;
        if (com.stardew.craft.client.hud.FestivalHudState.hidden()) return;

        @SuppressWarnings("null")
        boolean isStardew = mc.level.dimension() == ModDimensions.STARDEW_VALLEY
                || mc.level.dimension() == ModMiningDimensions.STARDEW_MINING;
        if (!isStardew) return;

        int elapsed = (int) (mc.getTimer().getRealtimeDeltaTicks() * 50);
        if (questPulseTimer > 0) questPulseTimer = Math.max(0, questPulseTimer - elapsed);
        if (questPingTimer > 0) questPingTimer = Math.max(0, questPingTimer - elapsed);

        whenToPulseTimer -= elapsed;
        if (whenToPulseTimer <= 0) {
            whenToPulseTimer = 3000;
            if (hasNewQuestActivity()) questPulseTimer = 1000;
        }

        render(event.getGuiGraphics(), mc);
    }

    private static boolean hasNewQuestActivity() {
        for (StardewQuest q : ClientQuestData.getQuestLog()) {
            if (q.isSecretQuest()) continue;
            if (q.isShowNew()) return true;
            if (q.isCompleted() && q.hasReward()) return true;
        }
        return false;
    }

    @SuppressWarnings("null")
    private static void render(GuiGraphics g, Minecraft mc) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        StardewHudLayout.Placement placement = StardewHudLayout.current(screenWidth, screenHeight);
        renderAt(g, mc, placement.x(), placement.y(), placement.scale());
    }

    static void renderPreview(GuiGraphics g, int x, int y, float scale) {
        renderAt(g, Minecraft.getInstance(), x, y, scale);
    }

    private static void renderAt(GuiGraphics g, Minecraft mc, int hudX, int hudY, float hudScale) {
        g.pose().pushPose();
        g.pose().translate(hudX, hudY, 0.0F);
        g.pose().scale(hudScale, hudScale, 1.0F);
        try {
            // ─── Quest button: anchored to bottom-right of moneybox ───
            // SDV: questButton 在 moneyBox 右下角外侧
            int iconW = Math.round(ICON_W * ICON_SCALE);
            int iconH = Math.round(ICON_H * ICON_SCALE);
            int btnX = StardewHudLayout.TIME_BG_WIDTH - iconW; // 右对齐 HUD 右边缘
            int btnY = StardewHudLayout.TIME_BG_HEIGHT + 2; // 紧贴 HUD 底部下方 2px

            CommonGuiTextures.drawQuestHudButton(g, btnX, btnY, ICON_SCALE);

            // ─── Exclamation "!" pulse ───
            // SDV: at (bounds.X+24, bounds.Y+32), origin(2,4), bounds=44×46 → (54.5%, 69.6%)
            // Our icon=11×14, so anchor at (ceil(11*0.545), round(14*0.696)) = (6, 10)
            if (questPulseTimer > 0) {
                float scaleMult = 1.0f / (Math.max(300f, Math.abs(questPulseTimer % 1000 - 500)) / 500f);
                float exclScale = ICON_SCALE * scaleMult;

                int exclAnchorX = btnX + iconW / 2;
                int exclAnchorY = btnY + Math.round(iconH * 0.70f);

                int shakeX = 0, shakeY = 0;
                if (scaleMult > 1.0f) {
                    shakeX = random.nextInt(3) - 1;
                    shakeY = random.nextInt(3) - 1;
                }

                g.pose().pushPose();
                g.pose().translate(exclAnchorX + shakeX, exclAnchorY + shakeY, 0);
                g.pose().scale(exclScale, exclScale, 1.0f);
                CommonGuiTextures.drawQuestDotAtCurrentPose(g, -2, -4);
                g.pose().popPose();
            }

            // ─── Ping flash below button ───
            // SDV: (bounds.Left-16, bounds.Bottom+8) at 4×
            // Proportionally: slightly left of icon, below
            if (questPingTimer > 0) {
                int pingFrame = ((questPingTimer / 200) % 2 != 0) ? 1 : 0;
                int pingW = Math.round(PING_W * ICON_SCALE);
                int pingX = btnX + iconW / 2 - pingW / 2;
                int pingY = btnY + iconH + 2;
                CommonGuiTextures.drawQuestHudPing(g, pingX, pingY, pingFrame, ICON_SCALE);
            }

            // ─── Key hint ───
            String keyName = ModKeyMappings.QUEST_LOG.getTranslatedKeyMessage().getString();
            String hint = "[" + keyName + "]";
            int hintWidth = mc.font.width(hint);
            int hintX = btnX + iconW / 2 - hintWidth / 2;
            int hintY = btnY + iconH + 2;
            if (questPingTimer > 0) hintY += Math.round(PING_H * ICON_SCALE) + 2;
            g.drawString(mc.font, hint, hintX, hintY, 0x808080, false);
        } finally {
            g.pose().popPose();
        }
    }
}
