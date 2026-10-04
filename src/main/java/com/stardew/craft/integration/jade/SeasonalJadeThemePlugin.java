package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.hud.StardewHotbarHud;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.theme.Theme;
import snownee.jade.api.ui.BoxStyle;

import java.util.Optional;

/** Uses Jade's own layout and sprite renderer for the seasonal outer frame. */
@WailaPlugin(StardewCraft.MODID)
public final class SeasonalJadeThemePlugin implements IWailaPlugin {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private static final BoxStyle[] FRAMES = new BoxStyle[SEASONS.length];

    static {
        for (int season = 0; season < SEASONS.length; season++) {
            FRAMES[season] = BoxStyle.getSprite(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                    "jade/seasonal/" + SEASONS[season] + "/frame"), new int[]{8, 8, 8, 8});
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addBeforeTooltipCollectCallback((theme, accessor) -> {
            if (StardewHotbarHud.isSeasonalThemeEnabled()) {
                theme.setValue(withSeason(theme.getValue(), TerrainSeasonTextures.currentTextureSet()));
            }
            return false;
        });
    }

    static Theme withSeason(Theme original, int season) {
        // Jade measures this frame during collection and restores the configured theme afterwards.
        Theme themed = new Theme(FRAMES[TerrainSeasonTextures.textureSet(season)],
                original.nestedBoxStyle, original.viewGroupStyle, original.text,
                Optional.ofNullable(original.changeRoundCorner), original.changeOpacity,
                original.lightColorScheme, original.hidden,
                Optional.ofNullable(original.iconSlotSprite), original.iconSlotInflation);
        themed.id = original.id;
        return themed;
    }
}
