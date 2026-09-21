package com.stardew.craft.api.v1.machine;

/** Listener for committed timed-production lifecycle transitions. */
@FunctionalInterface
public interface StardewProductionListener {
    void onTransition(StardewProductionEvent event);
}
