package com.stardew.craft.api.v1.mapinteraction;

import net.minecraft.world.InteractionResult;

@FunctionalInterface
public interface StardewMapInteractionActionExecutor<T> {
    InteractionResult execute(
            StardewMapInteractionContext context,
            T data
    );
}
