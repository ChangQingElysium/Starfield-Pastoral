package com.stardew.craft.api.v1.mapinteraction;

import net.minecraft.world.InteractionResult;

@FunctionalInterface
public interface StardewMapInteractionProvider {
    InteractionResult interact(StardewMapInteractionContext context);
}
