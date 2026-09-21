package com.stardew.craft.forge.client;

/**
 * Client-only season selector for authored terrain textures.
 *
 * <p>This class deliberately has no clock or packet hookup.  A Forge time
 * bridge may call {@link #updateSeason(int)} when that bridge is ported; until
 * then the selector remains at the source default and never invents a season
 * from client-local time.</p>
 */
public final class TerrainSeasonTextures {
    private static volatile int currentSeason = -1;

    private TerrainSeasonTextures() {
    }

    public static int textureSet(int season) {
        return season >= 0 && season < 4 ? season : 0;
    }

    public static int currentTextureSet() {
        return textureSet(currentSeason);
    }

    /** Returns true once per actual calendar change. */
    public static boolean updateSeason(int season) {
        if (currentSeason == season) {
            return false;
        }
        currentSeason = season;
        return true;
    }
}
