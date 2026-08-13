package com.stardew.craft.api.v1.world;

import net.minecraft.resources.ResourceLocation;

/**
 * Core namespaced keys used by resolved logical-location environments.
 *
 * <p>Addons may use their own namespaced tags and properties without
 * registering them here.
 */
public final class StardewLocationEnvironmentKeys {
    public static final ResourceLocation INDOOR =
            core("indoor");
    public static final ResourceLocation OUTDOOR =
            core("outdoor");
    public static final ResourceLocation CLIMATE =
            core("climate");
    public static final ResourceLocation MUSIC_PROFILE =
            core("music_profile");
    public static final ResourceLocation MUSIC_START_TIME =
            core("music_start_time");
    public static final ResourceLocation MUSIC_END_TIME =
            core("music_end_time");
    public static final ResourceLocation MUSIC_IGNORED_IN_RAIN =
            core("music_ignored_in_rain");
    /** A music profile value that deliberately suppresses fallback music. */
    public static final String MUSIC_SILENT =
            "stardewcraft:silent";
    public static final ResourceLocation WATER_PROFILE =
            core("water_profile");

    private StardewLocationEnvironmentKeys() {
    }

    private static ResourceLocation core(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", path);
    }
}
