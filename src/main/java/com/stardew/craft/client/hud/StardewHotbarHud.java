package com.stardew.craft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Seasonal art at vanilla hotbar coordinates; items and XP progression remain native. */
public final class StardewHotbarHud {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final String[] SPRITES = {
            "hotbar", "hotbar_selection", "hotbar_offhand_left", "hotbar_offhand_right",
            "hotbar_attack_indicator_background", "hotbar_attack_indicator_progress",
            "experience_bar_background", "experience_bar_progress", "hotbar_ornament",
            "experience_level_digits"
    };
    private static final ResourceLocation[][] THEMES = new ResourceLocation[4][SPRITES.length];
    private static final Map<ResourceLocation, Integer> VANILLA_SPRITES = new HashMap<>();

    static {
        for (int sprite = 0; sprite < SPRITES.length; sprite++) {
            if (sprite < 8) {
                VANILLA_SPRITES.put(new ResourceLocation("hud/" + SPRITES[sprite]), sprite);
            }
            for (int season = 0; season < SEASONS.length; season++) {
                THEMES[season][sprite] = new ResourceLocation(StardewCraft.MODID,
                        "hud/seasonal_hotbar/" + SEASONS[season] + "/" + SPRITES[sprite]);
            }
        }
    }

    private StardewHotbarHud() {}

    private static boolean enabled(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.isSpectator() || minecraft.options.hideGui) return false;
        var dimension = minecraft.player.level().dimension();
        return dimension == ModDimensions.STARDEW_VALLEY || dimension == ModMiningDimensions.STARDEW_MINING;
    }

    public static boolean isSeasonalThemeEnabled() {
        return enabled(Minecraft.getInstance());
    }

    static ResourceLocation resolve(ResourceLocation original, int season, boolean enabled) {
        Integer sprite = enabled ? VANILLA_SPRITES.get(original) : null;
        return sprite == null ? original : THEMES[TerrainSeasonTextures.textureSet(season)][sprite];
    }

    public static ResourceLocation replaceSprite(ResourceLocation original) {
        return resolve(original, TerrainSeasonTextures.currentTextureSet(), enabled(Minecraft.getInstance()));
    }

    public static void renderOrnament(GuiGraphics graphics) {
        if (!enabled(Minecraft.getInstance())) return;
        // XP occupies H-29..H-25. The two exterior pixels end before the hotbar's H-22 top edge.
        com.stardew.craft.port.PortGuiSprites.blitSprite(graphics, THEMES[TerrainSeasonTextures.currentTextureSet()][8],
                graphics.guiWidth() / 2 - 91, graphics.guiHeight() - 24, 182, 2);
    }

    static int levelWidth(int level) {
        return Integer.toString(Math.max(0, level)).length() * 4 - 1;
    }

    static int levelX(int guiWidth, int level) {
        // Centre the glyphs including their one-pixel shadow in the 20-pixel gap between status rows.
        return guiWidth / 2 - (levelWidth(level) + 1) / 2;
    }

    static int experienceGap(int level) {
        int width = levelWidth(level);
        return width > 19 ? width + 4 : 0;
    }

    static int levelY(int guiHeight, int level) {
        return guiHeight - (experienceGap(level) == 0 ? 37 : 30);
    }

    private static boolean experienceVisible(Minecraft minecraft) {
        return minecraft.gameMode != null && minecraft.gameMode.hasExperience()
                && minecraft.player.jumpableVehicle() == null;
    }

    /** Exceptionally long levels get their own opening in the XP rail, clear of both status rows. */
    public static boolean renderWideExperienceBar(GuiGraphics graphics, int x) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled(minecraft) || !experienceVisible(minecraft)) return false;
        int gap = experienceGap(minecraft.player.experienceLevel);
        if (gap == 0 || minecraft.player.getXpNeededForNextLevel() <= 0) return false;
        ResourceLocation[] theme = THEMES[TerrainSeasonTextures.currentTextureSet()];
        int y = graphics.guiHeight() - 29;
        int left = (182 - gap) / 2;
        int right = left + gap;
        int filled = Mth.clamp((int) (minecraft.player.experienceProgress * 183.0F), 0, 182);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            drawExperienceRange(graphics, theme[6], x, y, 0, left);
            drawExperienceRange(graphics, theme[6], x, y, right, 182 - right);
            drawExperienceRange(graphics, theme[7], x, y, 0, Math.min(left, filled));
            drawExperienceRange(graphics, theme[7], x, y, right, Math.max(0, filled - right));
        } finally {
            RenderSystem.disableBlend();
        }
        return true;
    }

    private static void drawExperienceRange(GuiGraphics graphics, ResourceLocation sprite,
                                            int x, int y, int sourceX, int width) {
        if (width > 0) com.stardew.craft.port.PortGuiSprites.blitSprite(graphics, sprite, 182, 5, sourceX, 0, x + sourceX, y, width, 5);
    }

    public static boolean renderExperienceLevel(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled(minecraft)) return false;
        if (!experienceVisible(minecraft) || minecraft.player.experienceLevel <= 0) return true;
        int level = minecraft.player.experienceLevel;
        String text = Integer.toString(level);
        int x = levelX(graphics.guiWidth(), level);
        int y = levelY(graphics.guiHeight(), level);
        ResourceLocation digits = THEMES[TerrainSeasonTextures.currentTextureSet()][9];
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            for (int index = 0; index < text.length(); index++) {
                int glyph = text.charAt(index) - '0';
                graphics.setColor(0.25F, 0.25F, 0.25F, 1.0F);
                com.stardew.craft.port.PortGuiSprites.blitSprite(graphics, digits, 48, 8, glyph * 4, 0, x + 1, y + 1, 3, 5);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                com.stardew.craft.port.PortGuiSprites.blitSprite(graphics, digits, 48, 8, glyph * 4, 0, x, y, 3, 5);
                x += 4;
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        return true;
    }
}
