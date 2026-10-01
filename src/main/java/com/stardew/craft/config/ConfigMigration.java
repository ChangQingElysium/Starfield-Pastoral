package com.stardew.craft.config;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** One-time migration from the historical common config to correctly scoped configs. */
public final class ConfigMigration {
    private ConfigMigration() {
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.CLIENT_SPEC) {
            migratePlayerVitalsLayout();
        }
        if (event.getConfig().getSpec() == Config.COMMON_SPEC
                || event.getConfig().getSpec() == Config.CLIENT_SPEC) {
            migrateClientValuesWhenReady();
        }
        if (event.getConfig().getSpec() == Config.COMMON_SPEC
                || event.getConfig().getSpec() == Config.SERVER_SPEC) {
            migrateServerValuesWhenReady();
        }
    }

    private static void migratePlayerVitalsLayout() {
        if (!Config.CLIENT_SPEC.isLoaded() || Config.CLIENT.PLAYER_VITALS_LAYOUT_IMPORTED.get()) return;
        Config.HudElementSettings settings = Config.CLIENT.HUD_ELEMENTS.get(Config.HudElement.PLAYER_BARS);
        if (settings.scalePercent().get() == 100
                && settings.horizontalAnchor().get() == Config.HudHorizontalAnchor.CENTER
                && settings.verticalAnchor().get() == Config.HudVerticalAnchor.BOTTOM
                && settings.offsetX().get() == 0 && settings.offsetY().get() == 31) {
            settings.offsetY().set(Config.HudElement.PLAYER_BARS.defaultOffsetY());
        }
        Config.CLIENT.PLAYER_VITALS_LAYOUT_IMPORTED.set(true);
        Config.CLIENT_SPEC.save();
    }

    private static void migrateClientValuesWhenReady() {
        if (!Config.COMMON_SPEC.isLoaded() || !Config.CLIENT_SPEC.isLoaded()
                || Config.CLIENT.LEGACY_COMMON_IMPORTED.get()) {
            return;
        }

        Config.SHOW_MONSTER_HP_BAR.set(Config.MINING.LEGACY_SHOW_MONSTER_HP_BAR.get());
        Config.CLIENT.LEGACY_COMMON_IMPORTED.set(true);
        Config.CLIENT_SPEC.save();
        StardewCraft.LOGGER.info("Imported legacy client-facing settings from stardewcraft-common.toml");
    }

    private static void migrateServerValuesWhenReady() {
        if (!Config.COMMON_SPEC.isLoaded() || !Config.SERVER_SPEC.isLoaded()
                || Config.SERVER.LEGACY_COMMON_IMPORTED.get()) {
            return;
        }

        Config.TIME_SPEED_MULTIPLIER.set(Config.GENERAL.LEGACY_TIME_SPEED_MULTIPLIER.get());
        Config.ENABLE_FISHING_MINIGAME.set(Config.FISHING.LEGACY_ENABLE_MINIGAME.get());
        Config.SERVER.LEGACY_COMMON_IMPORTED.set(true);
        Config.SERVER_SPEC.save();
        StardewCraft.LOGGER.info("Imported legacy gameplay settings into the current world's server config");
    }
}
