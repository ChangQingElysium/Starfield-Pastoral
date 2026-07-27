package com.stardew.craft.api.v1.fishpond;

@FunctionalInterface
public interface StardewFishPondRequestListener {
    void onRequestCompleted(StardewFishPondRequestContext context);
}
