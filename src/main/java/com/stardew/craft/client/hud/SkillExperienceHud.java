package com.stardew.craft.client.hud;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.payload.SkillExperienceGainPayload;
import com.stardew.craft.player.SkillType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings("null")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SkillExperienceHud {
    private static final float SHOW_MS = 5250f;
    private static final float FADE_STEP = 0.04f;
    private static final float BASE_FRAME_MS = 16.6667f;
            
    private static final ResourceLocation TEX_FARMING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/ui_info/farming.png");
    private static final ResourceLocation TEX_FISHING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/ui_info/fishing.png");
    private static final ResourceLocation TEX_MINING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/ui_info/mining.png");
    private static final ResourceLocation TEX_FORAGING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/ui_info/foraging.png");
    private static final ResourceLocation TEX_COMBAT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/ui_info/combat.png");

    // UI Info Suite constants
    private static final int BAR_RENDER_MS = 8000; // 480 ticks in SV
    
    private static final Deque<LevelUpEvent> QUEUE = new ArrayDeque<>();
    private static LevelUpEvent active;
    
    private static SkillType currentXpSkill = null;
    private static float xpShowRemaining = 0;
    private static float lastXpBarFillPct = 0;
    private static int currentXpLevel = 1;
    
    private static long lastUpdateNanos = System.nanoTime();

    private SkillExperienceHud() {}

    private static final int[] EXP_TO_LEVEL = {
        0, 100, 380, 770, 1300, 2150, 3300, 4800, 6900, 10000, 15000
    };

    public static void onExperienceGained(SkillExperienceGainPayload payload) {
        if (payload.amount() <= 0) return;

        Minecraft mc = Minecraft.getInstance();

        if (payload.levelAfter() > payload.levelBefore()) {
            LevelUpEvent incoming = new LevelUpEvent(
                    SkillType.fromId(payload.skillId()),
                    payload.levelAfter(),
                    SHOW_MS,
                    1.0f
            );
            QUEUE.addLast(incoming);

            try {
                // Play vanilla challenge complete toast sound to ensure the user hears it clearly
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
            } catch (Exception e) {
                // ignore
            }
        }
        
        currentXpSkill = SkillType.fromId(payload.skillId());
        xpShowRemaining = BAR_RENDER_MS;
        currentXpLevel = payload.levelAfter();
        
        // Calculate the real fill percentage based on the StardewCraft experience system
        if (currentXpLevel >= 10) {
            lastXpBarFillPct = 1.0f;
        } else {
            int currentLevelBaseExp = EXP_TO_LEVEL[currentXpLevel];
            int nextLevelExp = EXP_TO_LEVEL[currentXpLevel + 1];
            int currentExp = payload.expAfter();
            lastXpBarFillPct = (float) (currentExp - currentLevelBaseExp) / (nextLevelExp - currentLevelBaseExp);
            if (lastXpBarFillPct < 0f) lastXpBarFillPct = 0f;
            if (lastXpBarFillPct > 1.0f) lastXpBarFillPct = 1.0f;
        }
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || mc.screen instanceof StardewHudLayoutEditorScreen) return;

        long nowNanos = System.nanoTime();
        float deltaMs = (nowNanos - lastUpdateNanos) / 1_000_000f;
        lastUpdateNanos = nowNanos;
        if (deltaMs <= 0f) deltaMs = BASE_FRAME_MS;
        float frameFactor = deltaMs / BASE_FRAME_MS;

        GuiGraphics g = event.getGuiGraphics();
        
        renderUiInfoSuite(g, mc, deltaMs);

        if (active == null && !QUEUE.isEmpty()) {
            active = QUEUE.pollFirst();
        }

        if (active != null) {
            active = active.update(deltaMs, frameFactor);
            if (active != null) {
                renderNotification(g, mc, active);
            }
        }
    }
    
    private static void renderUiInfoSuite(GuiGraphics g, Minecraft mc, float deltaMs) {
        if (currentXpSkill != null && xpShowRemaining > 0) {
            xpShowRemaining -= deltaMs;
            if (xpShowRemaining <= 0) {
                currentXpSkill = null;
            } else {
                StardewHudLayout.Placement placement = StardewHudLayout.current(
                        Config.HudElement.SKILL_XP, g.guiWidth(), g.guiHeight());
                g.pose().pushPose();
                g.pose().translate(placement.x(), placement.y(), 0.0F);
                g.pose().scale(placement.scale(), placement.scale(), 1.0F);
                renderXpPanel(g, StardewFonts.small(), currentXpSkill, currentXpLevel, lastXpBarFillPct);
                g.pose().popPose();
            }
        }
    }

    public static void renderPreview(GuiGraphics g, int x, int y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        renderXpPanel(g, StardewFonts.small(), SkillType.FARMING, 6, 0.62F);
        g.pose().popPose();
    }

    private static void renderXpPanel(GuiGraphics g, Font font, SkillType skill, int level, float fillPct) {
        int boxW = 100;
        int boxH = 28;
        g.fill(2, 2, boxW - 2, boxH - 2, 0xFFFFCC7A);
        g.fill(2, 0, boxW - 2, 2, 0xFF4B2413);
        g.fill(2, boxH - 2, boxW - 2, boxH, 0xFF4B2413);
        g.fill(0, 2, 2, boxH - 2, 0xFF4B2413);
        g.fill(boxW - 2, 2, boxW, boxH - 2, 0xFF4B2413);
        g.fill(1, 1, 2, 2, 0xFF4B2413);
        g.fill(boxW - 2, 1, boxW - 1, 2, 0xFF4B2413);
        g.fill(1, boxH - 2, 2, boxH - 1, 0xFF4B2413);
        g.fill(boxW - 2, boxH - 2, boxW - 1, boxH - 1, 0xFF4B2413);
        g.fill(2, 2, boxW - 2, 4, 0xFFFEE2AD);
        g.fill(2, 2, 4, boxH - 2, 0xFFFEE2AD);
        g.fill(2, boxH - 4, boxW - 2, boxH - 2, 0xFFD28B3B);
        g.fill(boxW - 4, 2, boxW - 2, boxH - 2, 0xFFD28B3B);

        ResourceLocation iconTex = getSkillIcon(skill);
        RenderSystem.setShaderTexture(0, iconTex);
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(6, 6, 0);
        g.pose().scale(1.6F, 1.6F, 1.0F);
        g.blit(iconTex, 0, 0, 0, 0, 10, 10, 10, 10);
        g.pose().popPose();

        int barX = 26;
        int barY = 14;
        int barW = 66;
        int barH = 6;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF8B4B32);
        g.fill(barX, barY, barX + barW, barY + 1, 0x55000000);
        int filledWidth = (int) (barW * Mth.clamp(fillPct, 0.0F, 1.0F));
        if (filledWidth > 0) {
            g.fill(barX, barY, barX + filledWidth, barY + barH, getSkillColor(skill));
            g.fill(barX, barY, barX + filledWidth, barY + 1, 0x44FFFFFF);
        }

        g.pose().pushPose();
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, String.valueOf(level), (int) (26 / 0.75F), (int) (5 / 0.75F), 0x50281F, false);
        g.pose().popPose();
    }


    
    private static int getSkillColor(SkillType type) {
        // Colors from UI Info Suite 2 (RGBA converted to ARGB)
        return switch (type) {
            case FARMING -> ((int) (0.38f * 255) << 24) | (255 << 16) | (251 << 8) | 35;
            case FISHING -> ((int) (0.63f * 255) << 24) | (17 << 16) | (84 << 8) | 252;
            case FORAGING -> ((int) (0.38f * 255) << 24) | (0 << 16) | (234 << 8) | 0;
            case MINING -> ((int) (0.38f * 255) << 24) | (145 << 16) | (104 << 8) | 63;
            case COMBAT -> ((int) (0.38f * 255) << 24) | (204 << 16) | (0 << 8) | 3;
        };
    }
    
    private static ResourceLocation getSkillIcon(SkillType type) {
        return switch (type) {
            case FARMING -> TEX_FARMING;
            case FISHING -> TEX_FISHING;
            case FORAGING -> TEX_FORAGING;
            case MINING -> TEX_MINING;
            case COMBAT -> TEX_COMBAT;
        };
    }

    private static int withAlpha(int color, int alpha255) {
        return (alpha255 << 24) | (color & 0xFFFFFF);
    }

    private static void renderNotification(GuiGraphics g, Minecraft mc, LevelUpEvent activeLocal) {
        String skillKey = activeLocal.skillId().name().toLowerCase();
        String message = I18n.get("ui.stardewcraft.skillhud.levelup", I18n.get("stardewcraft.skill." + skillKey));
        String line2 = I18n.get("ui.stardewcraft.skillhud.level", activeLocal.level());

        int textW = StardewFonts.small().width(message);
        int line2W = StardewFonts.small().width(line2);
        int boxW = Math.max(textW, line2W) + 40;
        int boxH = 40;

        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.SKILL_LEVEL_UP, g.guiWidth(), g.guiHeight());
        int boxX = (Config.HudElement.SKILL_LEVEL_UP.baseWidth() - boxW) / 2;
        int boxY = 0;

        int a = (int) (activeLocal.alpha() * 255);
        if (a <= 5) return;

        g.pose().pushPose();
        g.pose().translate(placement.x(), placement.y(), 0.0F);
        g.pose().scale(placement.scale(), placement.scale(), 1.0F);

        // Render Stardew Style Box with Alpha
        g.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + boxH - 2, withAlpha(0xFFFFCC7A, a));

        // Outer borders (#4B2413)
        g.fill(boxX + 2, boxY, boxX + boxW - 2, boxY + 2, withAlpha(0xFF4B2413, a)); // Top
        g.fill(boxX + 2, boxY + boxH - 2, boxX + boxW - 2, boxY + boxH, withAlpha(0xFF4B2413, a)); // Bottom
        g.fill(boxX, boxY + 2, boxX + 2, boxY + boxH - 2, withAlpha(0xFF4B2413, a)); // Left
        g.fill(boxX + boxW - 2, boxY + 2, boxX + boxW, boxY + boxH - 2, withAlpha(0xFF4B2413, a)); // Right
        
        // Corners: 2x2 dot
        g.fill(boxX + 1, boxY + 1, boxX + 2, boxY + 2, withAlpha(0xFF4B2413, a)); // TL
        g.fill(boxX + boxW - 2, boxY + 1, boxX + boxW - 1, boxY + 2, withAlpha(0xFF4B2413, a)); // TR
        g.fill(boxX + 1, boxY + boxH - 2, boxX + 2, boxY + boxH - 1, withAlpha(0xFF4B2413, a)); // BL
        g.fill(boxX + boxW - 2, boxY + boxH - 2, boxX + boxW - 1, boxY + boxH - 1, withAlpha(0xFF4B2413, a)); // BR

        // Inner Highlights (#FEE2AD)
        g.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + 4, withAlpha(0xFFFEE2AD, a)); // Top
        g.fill(boxX + 2, boxY + 2, boxX + 4, boxY + boxH - 2, withAlpha(0xFFFEE2AD, a)); // Left

        // Inner Shadows (#D28B3B)
        g.fill(boxX + 2, boxY + boxH - 4, boxX + boxW - 2, boxY + boxH - 2, withAlpha(0xFFD28B3B, a)); // Bottom
        g.fill(boxX + boxW - 4, boxY + 2, boxX + boxW - 2, boxY + boxH - 2, withAlpha(0xFFD28B3B, a)); // Right

        int textX = boxX + (boxW / 2) - (textW / 2);
        int textX2 = boxX + (boxW / 2) - (line2W / 2);

        g.drawString(StardewFonts.small(), message, textX, boxY + 8, withAlpha(0x50281F, a), false);
        g.drawString(StardewFonts.small(), line2, textX2, boxY + 22, withAlpha(0x1B5A24, a), false);
        g.pose().popPose();
    }

    private record LevelUpEvent(SkillType skillId, int level, float showMs, float alpha) {
        public LevelUpEvent update(float deltaMs, float frameFactor) {
            float ms = showMs - deltaMs;
            if (ms <= 0f) return null;

            float a = alpha;
            if (ms <= 500f) {
                a -= FADE_STEP * frameFactor;
            } else if (ms >= SHOW_MS - 500f) {
                a += FADE_STEP * frameFactor;
            } else {
                a = 1.0f;
            }

            if (a < 0f) a = 0f;
            if (a > 1.0f) a = 1.0f;

            return new LevelUpEvent(skillId, level, ms, a);
        }
    }


}
