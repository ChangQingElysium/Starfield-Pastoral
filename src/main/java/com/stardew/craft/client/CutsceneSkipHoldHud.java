package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class CutsceneSkipHoldHud {
    private static final int HOLD_TICKS = 30;
    private static final int SDV_SKIP_U = 205;
    private static final int SDV_SKIP_V = 406;
    private static final int SDV_SKIP_W = 22;
    private static final int SDV_SKIP_H = 15;
    private static final int BUTTON_SIZE = 44;
    private static final int SDV_SKIP_MARGIN_RIGHT = 8;
    private static final int SDV_SKIP_MARGIN_TOP = 18;

    private static int heldTicks;
    private static boolean skipRequested;

    private CutsceneSkipHoldHud() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!canChargeSkip(mc)) {
            reset();
            return;
        }

        if (isSkipKeyDown()) {
            if (skipRequested) {
                return;
            }
            heldTicks = Math.min(HOLD_TICKS, heldTicks + 1);
            if (heldTicks >= HOLD_TICKS) {
                skipRequested = true;
                EventPlayer.get().trySkip();
            }
        } else if (heldTicks > 0) {
            heldTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !canChargeSkip(mc)) {
            return;
        }
        render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !canChargeSkip(mc)) {
            return;
        }
        render(event.getGuiGraphics());
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int cx = Math.max(BUTTON_SIZE / 2 + 4, graphics.guiWidth() - SDV_SKIP_MARGIN_RIGHT - BUTTON_SIZE / 2);
        int cy = SDV_SKIP_MARGIN_TOP + BUTTON_SIZE / 2;
        float progress = Mth.clamp(heldTicks / (float) HOLD_TICKS, 0.0F, 1.0F);

        drawFilledCircle(graphics, cx, cy, 16.0F, 0x66000000);
        drawArcRing(graphics, cx, cy, 17.0F, 19.0F, -90.0F, 360.0F, 0x44FFFFFF);
        drawArcRing(graphics, cx, cy, 17.0F, 19.0F, -90.0F, progress * 360.0F, 0xDDF2D56B);

        StardewGuiUtil.drawFromCursors(graphics, cx - SDV_SKIP_W / 2, cy - SDV_SKIP_H / 2,
                SDV_SKIP_U, SDV_SKIP_V, SDV_SKIP_W, SDV_SKIP_H, 1.0F);

        String keyText = "[" + ModKeyMappings.CUTSCENE_SKIP.getTranslatedKeyMessage().getString() + "]";
        graphics.drawString(font, keyText, cx - font.width(keyText) / 2, cy + 27, 0xFFFFFFFF, false);
    }

    private static void reset() {
        heldTicks = 0;
        skipRequested = false;
    }

    private static boolean canChargeSkip(Minecraft mc) {
        return mc.player != null && EventPlayer.get().isSkippable();
    }

    private static boolean isSkipKeyDown() {
        return ModKeyMappings.isDown(ModKeyMappings.CUTSCENE_SKIP);
    }

    private static void drawFilledCircle(GuiGraphics graphics, int cx, int cy, float radius, int argb) {
        int r = Math.round(radius);
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(r2 - dy * dy));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
        }
    }

    private static void drawArcRing(GuiGraphics graphics, int cx, int cy, float innerR, float outerR,
                                    float startDeg, float sweepDeg, int argb) {
        if (sweepDeg <= 0.0F) {
            return;
        }
        int inner = Math.round(innerR);
        int outer = Math.round(outerR);
        int segments = Math.max(80, (int) (360.0F * (sweepDeg / 360.0F)));
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            double angle = Math.toRadians(startDeg + sweepDeg * t);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            int x1 = cx + Math.round(cos * inner);
            int y1 = cy + Math.round(sin * inner);
            int x2 = cx + Math.round(cos * outer);
            int y2 = cy + Math.round(sin * outer);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2),
                    Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, argb);
        }
    }
}
