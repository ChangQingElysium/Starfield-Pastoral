package com.stardew.craft.api.v1.npc;

import net.minecraft.world.InteractionResult;

/** Return PASS to let lower-priority providers and StardewCraft's normal interaction continue. */
@FunctionalInterface
public interface StardewNpcInteractionProvider {
    InteractionResult interact(StardewNpcInteractionContext context);
}
