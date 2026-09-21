package com.stardew.craft.tree;

import java.util.List;

/**
 * Loader-independent wild-tree growth contract.
 *
 * <p>The block/registry and prefab placement sides of wild-tree growth are
 * migrated separately.  Keeping these values in a pure data class prevents
 * those migrations from re-inventing Stardew's daily probability rules.</p>
 */
public final class WildTreeGrowthRules {
    public static final int SAPLING1_GROWTH_STAGE = 3;
    public static final int MATURE_GROWTH_STAGE = 5;
    public static final int SEASON_WINTER = 3;

    public record Species(
            String id,
            float growthChance,
            float fertilizedGrowthChance,
            float seedSpreadChance,
            float seedOnShakeChance,
            float seedOnChopChance,
            boolean growsInWinter) {
    }

    public static final Species OAK = new Species("oak", .2f, 1.0f, .15f, .05f, .75f, false);
    public static final Species MAPLE = new Species("maple", .2f, 1.0f, .15f, .05f, .75f, false);
    public static final Species PINE = new Species("pine", .2f, 1.0f, .15f, .05f, .75f, false);
    public static final Species MAHOGANY = new Species("mahogany", .15f, .6f, .15f, .05f, .5625f, false);
    public static final Species MYSTIC_TREE = new Species("mystic_tree", .15f, .3f, 0.0f, 0.0f, 0.0f, false);
    public static final List<Species> ALL = List.of(OAK, MAPLE, PINE, MAHOGANY, MYSTIC_TREE);

    private WildTreeGrowthRules() {
    }

    public static boolean canGrowInSeason(Species species, boolean fertilized, int season) {
        return season != SEASON_WINTER || fertilized || species.growsInWinter();
    }

    public static int seasonOfAbsoluteDay(int absoluteDay) {
        return Math.floorMod((absoluteDay - 1) / 28, 4);
    }

    public static int visualSaplingStage(int growthStage) {
        return growthStage >= SAPLING1_GROWTH_STAGE ? 1 : 0;
    }
}
