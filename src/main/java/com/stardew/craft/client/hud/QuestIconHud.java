package com.stardew.craft.client.hud;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ModKeyMappings;
import com.stardew.craft.client.font.StardewFonts;
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
 * Coordinates are DayTimeMoneyBox's source-space coordinates divided by its pixelZoom=4.
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class QuestIconHud {

    // ─── Cursors sprite dimensions ───
    private static final int BUTTON_X = 53;
    private static final int BUTTON_Y = 60;

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
        if (com.stardew.craft.client.hud.FestivalHudState.hidden()
                && !FestivalCurrencyHudState.active()) return;

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

    private static boolean hasVisibleQuests() {
        for (StardewQuest quest : ClientQuestData.getQuestLog()) {
            if (!quest.isSecretQuest()) {
                return true;
            }
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
        if (!hasVisibleQuests()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(hudX, hudY, 0.0F);
        g.pose().scale(hudScale, hudScale, 1.0F);
        try {
            CommonGuiTextures.drawQuestHudButton(g, BUTTON_X, BUTTON_Y, 1.0F);

            // ─── Exclamation "!" pulse ───
            // SDV: at (bounds.X+24, bounds.Y+32), origin(2,4), bounds=44×46 → (54.5%, 69.6%)
            // Our icon=11×14, so anchor at (ceil(11*0.545), round(14*0.696)) = (6, 10)
            if (questPulseTimer > 0) {
                float scaleMult = 1.0f / (Math.max(300f, Math.abs(questPulseTimer % 1000 - 500)) / 500f);
                float shakeX = 0.0F, shakeY = 0.0F;
                if (scaleMult > 1.0f) {
                    shakeX = (random.nextInt(3) - 1) / 4.0F;
                    shakeY = (random.nextInt(3) - 1) / 4.0F;
                }

                g.pose().pushPose();
                g.pose().translate(BUTTON_X + 6 + shakeX, BUTTON_Y + 8 + shakeY, 0);
                g.pose().scale(scaleMult, scaleMult, 1.0f);
                CommonGuiTextures.drawQuestDotAtCurrentPose(g, -2, -4);
                g.pose().popPose();
            }

            // ─── Ping flash below the key hint ───
            // SDV: (bounds.Left-16, bounds.Bottom+8) at 4×
            // Proportionally: slightly left of icon, below
            if (questPingTimer > 0) {
                int pingFrame = ((questPingTimer / 200) % 2 != 0) ? 1 : 0;
                g.pose().pushPose();
                g.pose().translate(BUTTON_X - 4, BUTTON_Y + 25.0F, 0.0F);
                CommonGuiTextures.drawQuestHudPing(g, 0, 0, pingFrame, 1.0F);
                g.pose().popPose();
            }

            // Functional Minecraft-side affordance retained below the SDV icon.
            // Keep it on the same HUD transform so user scaling and anchoring
            // apply to the icon and its key hint as one unit.
            if (!ModKeyMappings.QUEST_LOG.isUnbound()) {
                String hint = "[" + ModKeyMappings.QUEST_LOG.getTranslatedKeyMessage().getString() + "]";
                int hintY = BUTTON_Y + 15;
                int hintX = BUTTON_X + 6 - StardewFonts.tooltip().width(hint) / 2;
                g.drawString(StardewFonts.tooltip(), hint, hintX, hintY, 0xFFA0A0A0, true);
            }
        } finally {
            g.pose().popPose();
        }
    }
}
