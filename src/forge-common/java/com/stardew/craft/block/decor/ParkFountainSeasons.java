package com.stardew.craft.block.decor;

/** Shared winter rule for the visible water and its ambient sound. */
public final class ParkFountainSeasons {
    private ParkFountainSeasons() {}

    public static boolean flows(int season) { return season != 3; }
}
