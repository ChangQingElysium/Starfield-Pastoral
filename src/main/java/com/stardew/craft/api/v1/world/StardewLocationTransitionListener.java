package com.stardew.craft.api.v1.world;

/**
 * Observer for authoritative server-side logical-location transitions.
 *
 * <p>Callbacks cannot veto movement. Failures are logged and isolated.
 */
@FunctionalInterface
public interface StardewLocationTransitionListener {
    void onLocationTransition(StardewLocationTransition transition);
}
