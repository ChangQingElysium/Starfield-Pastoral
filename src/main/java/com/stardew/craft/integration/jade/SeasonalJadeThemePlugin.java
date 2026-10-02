package com.stardew.craft.integration.jade;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.hud.StardewHotbarHud;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.port.PortGuiSprites;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.theme.Theme;
import snownee.jade.api.ui.ITooltipRenderer;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/** PORT(1.20.1): Jade 11 measures the seasonal frame at collection time and keeps its native content. */
@WailaPlugin(StardewCraft.MODID)
public final class SeasonalJadeThemePlugin implements IWailaPlugin {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final ResourceLocation[] FRAMES = new ResourceLocation[SEASONS.length];
    // Jade's current and lingering tooltips retain their collected season; retired renderers are not retained.
    private static final Map<ITooltipRenderer, ResourceLocation> COLLECTED_FRAMES = new WeakHashMap<>();

    static {
        for (int season = 0; season < SEASONS.length; season++) {
            FRAMES[season] = new ResourceLocation(StardewCraft.MODID,
                    "jade/seasonal/" + SEASONS[season] + "/frame");
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRenderBackgroundCallback((renderer, rect, graphics, accessor, colors) -> {
            ResourceLocation frame = COLLECTED_FRAMES.get(renderer);
            if (frame == null) {
                return false;
            }
            // Jade has already translated to the morph rectangle and applied the renderer's real scale.
            float scale = renderer.getRealScale();
            int width = Math.round(rect.getWidth() / scale);
            int height = Math.round(rect.getHeight() / scale);
            RenderSystem.enableBlend();
            graphics.setColor(1.0F, 1.0F, 1.0F, colors.alpha);
            try {
                PortGuiSprites.blitSprite(graphics, frame, 0, 0, width, height);
            } finally {
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            // Replaces only the outer background; Jade still renders its icon, content and callbacks.
            return true;
        });
    }

    /** Called only at Jade 11's final collection-time padding measurement, before real-rectangle placement. */
    public static Theme collectTheme(ITooltipRenderer renderer, Theme original) {
        if (!StardewHotbarHud.isSeasonalThemeEnabled()) {
            COLLECTED_FRAMES.remove(renderer);
            return original;
        }
        Theme themed = copyWithFramePadding(original);
        int season = TerrainSeasonTextures.textureSet(TerrainSeasonTextures.currentTextureSet());
        COLLECTED_FRAMES.put(renderer, FRAMES[season]);
        return themed;
    }

    public static boolean hasCollectedFrame(ITooltipRenderer renderer) {
        return COLLECTED_FRAMES.containsKey(renderer);
    }

    private static Theme copyWithFramePadding(Theme original) {
        // This temporary copy is only passed to setPaddingFromTheme; the configured/global Theme stays intact.
        Theme themed = new Theme();
        themed.id = original.id;
        themed.backgroundColor = original.backgroundColor;
        System.arraycopy(original.borderColor, 0, themed.borderColor, 0, themed.borderColor.length);
        themed.titleColor = original.titleColor;
        themed.normalColor = original.normalColor;
        themed.infoColor = original.infoColor;
        themed.successColor = original.successColor;
        themed.warningColor = original.warningColor;
        themed.dangerColor = original.dangerColor;
        themed.failureColor = original.failureColor;
        themed.boxBorderColor = original.boxBorderColor;
        themed.itemAmountColor = original.itemAmountColor;
        themed.textShadow = original.textShadow;
        themed.backgroundTexture = original.backgroundTexture;
        themed.backgroundTextureUV = original.backgroundTextureUV == null ? null : original.backgroundTextureUV.clone();
        themed.backgroundTexture_withIcon = original.backgroundTexture_withIcon;
        themed.backgroundTextureUV_withIcon = original.backgroundTextureUV_withIcon == null
                ? null : original.backgroundTextureUV_withIcon.clone();
        Arrays.fill(themed.padding, 8);
        themed.squareBorder = original.squareBorder;
        themed.opacity = original.opacity;
        themed.bottomProgressOffset = original.bottomProgressOffset == null ? null : original.bottomProgressOffset.clone();
        themed.bottomProgressNormalColor = original.bottomProgressNormalColor;
        themed.bottomProgressFailureColor = original.bottomProgressFailureColor;
        themed.lightColorScheme = original.lightColorScheme;
        themed.hidden = original.hidden;
        return themed;
    }
}
