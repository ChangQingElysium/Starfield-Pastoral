package com.stardew.craft.api.v1.mining;

import net.minecraft.util.RandomSource;

/** Runtime context offered to ordered mine-monster providers. */
public record StardewMineMonsterContext(
        int floor,
        boolean dark,
        double distanceFromSpawn,
        RandomSource random
) {
}
