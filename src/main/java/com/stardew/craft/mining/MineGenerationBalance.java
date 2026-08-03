package com.stardew.craft.mining;

/**
 * Probability conversion rules for StardewCraft's three-dimensional mine.
 *
 * <p>Stardew Valley rolls resources over traversable map tiles. StardewCraft has
 * a much larger cave surface, so neither the full solid volume nor a fixed
 * per-floor cap is an appropriate denominator. We preserve the original
 * per-traversable-tile probability and distribute its expected results over the
 * actual exposed stone surface.</p>
 */
public final class MineGenerationBalance {
    private static final double NORMAL_ORE_CHANCE_PER_STONE = 0.029;
    private static final double EARTH_COAL_CHANCE_PER_STONE = 0.0060;
    private static final double FROST_COAL_CHANCE_PER_STONE = 0.0066;
    private static final double LAVA_COAL_CHANCE_PER_STONE = 0.0078;
    private static final double BASE_GEM_CHANCE_PER_STONE = 0.003;
    private static final double BASE_DIAMOND_CHANCE_PER_STONE = 0.00025;
    private static final double SURFACE_ITEM_CHANCE = 0.0025;
    private static final double BARREL_CHANCE_PER_TRAVERSABLE_TILE = 1.0 / 1_200.0;

    /**
     * One Minecraft mob controls a wider area and takes longer to fight than one
     * Stardew sprite, so convert the original 0.2%-2.19% tile roll at one quarter
     * density. The result still scales with the actual navigable surface.
     */
    private static final double MINECRAFT_MONSTER_DENSITY_SCALE = 0.25;

    private MineGenerationBalance() {
    }

    public static int pickaxeStrikes(int stoneHealth, int effectivePickaxeTier) {
        int damagePerStrike = Math.max(1, effectivePickaxeTier + 1);
        return Math.max(1, (Math.max(1, stoneHealth) + damagePerStrike - 1) / damagePerStrike);
    }

    public static float miningEnergyCost(
            int stoneHealth,
            int effectivePickaxeTier,
            int miningLevel
    ) {
        float energyPerStrike = Math.max(0.5F, 2.0F - Math.max(0, miningLevel) * 0.1F);
        return pickaxeStrikes(stoneHealth, effectivePickaxeTier) * energyPerStrike;
    }

    static double stonePlacementChance(int originalRollFromZeroToNineteen) {
        return 0.10 + Math.clamp(originalRollFromZeroToNineteen, 0, 19) / 100.0;
    }

    static int ladderStoneBudget(int traversableTileCount, double stonePlacementChance) {
        if (traversableTileCount <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(traversableTileCount * clampProbability(stonePlacementChance)));
    }

    static double metalChancePerTraversableTile(int floor, double stonePlacementChance) {
        if (floor <= 0 || floor == 120) {
            return 0.0;
        }
        if (floor < 120) {
            if (floor % 5 == 0) {
                return 0.0;
            }
            return clampProbability(stonePlacementChance) * NORMAL_ORE_CHANCE_PER_STONE;
        }
        return clampProbability(stonePlacementChance) * skullCavernOreChance(floor);
    }

    static double metalChancePerExposedStone(
            int floor,
            double stonePlacementChance,
            int traversableTileCount,
            int exposedStoneCount
    ) {
        return distributeTileProbability(
                metalChancePerTraversableTile(floor, stonePlacementChance),
                traversableTileCount,
                exposedStoneCount);
    }

    /**
     * Coal is a standalone StardewCraft node, not a fallback metal type. Keep
     * the custom per-area rates that existed before the surface-density rewrite
     * and make them available on elevator floors as well. Floors 120 and 121
     * are non-mining transition areas and intentionally contain no nodes.
     */
    static double coalChancePerTraversableTile(int floor, double stonePlacementChance) {
        if (floor <= 0 || floor == 120 || floor == 121) {
            return 0.0;
        }
        double conditionalChance;
        if (floor < 40) {
            conditionalChance = EARTH_COAL_CHANCE_PER_STONE;
        } else if (floor < 80) {
            conditionalChance = FROST_COAL_CHANCE_PER_STONE;
        } else {
            conditionalChance = LAVA_COAL_CHANCE_PER_STONE;
        }
        return clampProbability(stonePlacementChance) * conditionalChance;
    }

    static double coalChancePerExposedStone(
            int floor,
            double stonePlacementChance,
            int traversableTileCount,
            int exposedStoneCount
    ) {
        return distributeTileProbability(
                coalChancePerTraversableTile(floor, stonePlacementChance),
                traversableTileCount,
                exposedStoneCount);
    }

    static double gemChancePerTraversableTile(int floor, double stonePlacementChance) {
        if (floor <= 0 || floor == 120 || (floor < 120 && floor % 5 == 0)) {
            return 0.0;
        }
        double conditionalGemChance = BASE_GEM_CHANCE_PER_STONE + Math.max(0, floor) / 24_000.0;
        return clampProbability(stonePlacementChance) * clampProbability(conditionalGemChance);
    }

    static double diamondChancePerTraversableTile(int floor, double stonePlacementChance) {
        if (floor <= 50 || floor == 120 || (floor < 121 && floor % 10 == 0)) {
            return 0.0;
        }
        double conditionalDiamondChance =
                BASE_DIAMOND_CHANCE_PER_STONE + floor / 120_000.0;
        return clampProbability(stonePlacementChance)
                * clampProbability(conditionalDiamondChance);
    }

    static double diamondChancePerExposedStone(
            int floor,
            double stonePlacementChance,
            int traversableTileCount,
            int exposedStoneCount
    ) {
        return distributeTileProbability(
                diamondChancePerTraversableTile(floor, stonePlacementChance),
                traversableTileCount,
                exposedStoneCount);
    }

    static double gemChancePerExposedStone(
            int floor,
            double stonePlacementChance,
            int traversableTileCount,
            int exposedStoneCount
    ) {
        return distributeTileProbability(
                gemChancePerTraversableTile(floor, stonePlacementChance),
                traversableTileCount,
                exposedStoneCount);
    }

    static double richRoomMetalChance(
            int floor,
            double stonePlacementChance,
            double richnessMultiplier
    ) {
        return clampProbability(
                metalChancePerTraversableTile(floor, stonePlacementChance)
                        * Math.max(0.0, richnessMultiplier));
    }

    static double richRoomIridiumChance(
            int floor,
            double stonePlacementChance,
            double richnessMultiplier
    ) {
        if (floor <= 120) {
            return 0.0;
        }
        return clampProbability(
                metalChancePerTraversableTile(floor, stonePlacementChance)
                        * skullCavernIridiumChance(floor)
                        * Math.max(0.0, richnessMultiplier));
    }

    static double surfaceItemChancePerTile(
            int floor,
            double stonePlacementChance,
            double originalMonsterRoll
    ) {
        if (floor <= 0 || floor == 120 || (floor < 120 && floor % 5 == 0)) {
            return 0.0;
        }
        return (1.0 - clampProbability(stonePlacementChance))
                * (1.0 - originalMonsterChance(floor, originalMonsterRoll))
                * SURFACE_ITEM_CHANCE;
    }

    static double barrelChancePerTile(int floor) {
        if (floor <= 1 || (floor % 5 == 0 && floor < 121)) {
            return 0.0;
        }
        return BARREL_CHANCE_PER_TRAVERSABLE_TILE;
    }

    static double originalMonsterChance(int floor, double originalMonsterRoll) {
        if (floor <= 0 || floor == 1 || (floor < 121 && floor % 10 == 0)) {
            return 0.0;
        }
        return 0.002 + Math.clamp(originalMonsterRoll, 0.0, Math.nextDown(1.0)) * 0.02;
    }

    static double monsterChancePerTile(
            int floor,
            double stonePlacementChance,
            double originalMonsterRoll
    ) {
        double originalChance = originalMonsterChance(floor, originalMonsterRoll);
        if (originalChance <= 0.0) {
            return 0.0;
        }
        double dimensionScale = floor > 120 ? 1.25 : 1.0;
        return (1.0 - clampProbability(stonePlacementChance))
                * originalChance
                * MINECRAFT_MONSTER_DENSITY_SCALE
                * dimensionScale;
    }

    static double skullCavernOreChance(int floor) {
        int skullLevel = Math.max(0, floor - 120);
        double chanceForOre = 0.02 + skullLevel * 0.0005;
        if (floor >= 130) {
            chanceForOre += 0.01 * ((Math.min(100, skullLevel) - 10) / 10.0);
        }
        return clampProbability(chanceForOre);
    }

    static double skullCavernIridiumChance(int floor) {
        int skullLevel = Math.max(0, floor - 120);
        double iridiumBoost = 0.0;
        if (floor >= 130) {
            iridiumBoost += 0.001 * ((skullLevel - 10) / 10.0);
        }
        iridiumBoost = Math.min(iridiumBoost, 0.004);
        if (skullLevel > 100) {
            iridiumBoost += skullLevel / 1_000_000.0;
        }
        return clampProbability(Math.min(100, skullLevel) * (0.0003 + iridiumBoost));
    }

    static String pickRegularOreKey(int floor, double firstRoll, double secondRoll) {
        if (floor < 40) {
            return floor >= 20 && firstRoll < 0.10 ? "iron" : "copper";
        }
        if (floor < 80) {
            if (floor >= 60 && firstRoll < 0.10) {
                return "gold";
            }
            return secondRoll < 0.75 ? "iron" : "copper";
        }
        if (floor < 120) {
            if (firstRoll < 0.75) {
                return "gold";
            }
            return secondRoll < 0.75 ? "iron" : "copper";
        }
        return "copper";
    }

    static double distributeTileProbability(
            double probabilityPerTraversableTile,
            int traversableTileCount,
            int exposedStoneCount
    ) {
        if (probabilityPerTraversableTile <= 0.0
                || traversableTileCount <= 0
                || exposedStoneCount <= 0) {
            return 0.0;
        }
        double expectedNodes = probabilityPerTraversableTile * traversableTileCount;
        return clampProbability(expectedNodes / exposedStoneCount);
    }

    private static double clampProbability(double probability) {
        return Math.clamp(probability, 0.0, 1.0);
    }
}
