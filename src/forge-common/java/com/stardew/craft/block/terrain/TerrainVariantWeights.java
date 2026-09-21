package com.stardew.craft.block.terrain;

/** Placement weights in percent; zero is the undecorated top. */
public final class TerrainVariantWeights {
    private TerrainVariantWeights() {}

    public static int grass(int roll) {
        if (roll < 88) return 0;
        if (roll < 96) return 1;
        return 2;
    }

    public static int dirt(int roll) {
        if (roll < 89) return 0;
        if (roll < 90) return 1;
        if (roll < 93) return 2;
        if (roll < 97) return 3;
        return 4;
    }

    /** 0..999: structures 55/25/20 percent, with an independent 10 percent moss chance. */
    public static int cliff(int roll) {
        if (roll < 495) return 0;
        if (roll < 720) return 1;
        if (roll < 900) return 2;
        if (roll < 955) return 3;
        if (roll < 980) return 4;
        return 5;
    }

    /** Authored firing-color balance for our red brick; not original SDV random probabilities. */
    public static int redPaving(int roll) {
        if (roll < 50) return 0;
        if (roll < 64) return 1;
        if (roll < 76) return 2;
        if (roll < 86) return 3;
        if (roll < 95) return 4;
        return 5;
    }

    /** Rounded Town map frequencies: 736/641/737/768/769/770; not an SDV RNG rule. */
    public static int paving(int roll) {
        if (roll < 28) return 0;
        if (roll < 38) return 1;
        if (roll < 52) return 2;
        if (roll < 75) return 3;
        if (roll < 97) return 4;
        return 5;
    }
}
