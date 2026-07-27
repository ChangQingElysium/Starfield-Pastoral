package com.stardew.craft.api.v1.mining;

import net.minecraft.world.entity.Mob;

@FunctionalInterface
public interface StardewMineMonsterConfigurator {
    /** Applies authoritative attributes, goals or other profile-specific state. */
    void configure(Mob mob, StardewMineMonsterProfileContext context);
}
