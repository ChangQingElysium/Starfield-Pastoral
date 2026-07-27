package com.stardew.craft.api.v1.fishpond;

@FunctionalInterface
public interface StardewFishPondDailyListener {
    void onDailyUpdate(StardewFishPondDailyContext context);
}
