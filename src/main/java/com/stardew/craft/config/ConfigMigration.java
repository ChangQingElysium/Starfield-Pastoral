package com.stardew.craft.config;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import net.neoforged.fml.event.config.ModConfigEvent;

/** One-time migration from the historical common config to correctly scoped configs. */
public final class ConfigMigration {
    private ConfigMigration() {
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.COMMON_SPEC
                || event.getConfig().getSpec() == Config.CLIENT_SPEC) {
            migrateClientValuesWhenReady();
        }
        if (event.getConfig().getSpec() == Config.COMMON_SPEC
                || event.getConfig().getSpec() == Config.SERVER_SPEC) {
            migrateServerValuesWhenReady();
        }
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
