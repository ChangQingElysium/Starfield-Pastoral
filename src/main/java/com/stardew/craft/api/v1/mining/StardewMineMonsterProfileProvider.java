package com.stardew.craft.api.v1.mining;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

@FunctionalInterface
public interface StardewMineMonsterProfileProvider {
    /** Returns a registered profile ID, or {@code null} to continue. */
    @Nullable ResourceLocation select(StardewMineMonsterContext context);
}
