package com.stardew.craft.api.v1.cutscene;

import net.minecraft.server.level.ServerPlayer;

/** Evaluates an add-on cutscene trigger on the logical server. */
@FunctionalInterface
public interface StardewCutsceneTriggerEvaluator<T> {
    boolean test(ServerPlayer player, T data);
}
