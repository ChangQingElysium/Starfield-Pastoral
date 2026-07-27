package com.stardew.craft.api.v1.festival;

/** Observes completed festival session state transitions. */
@FunctionalInterface
public interface StardewFestivalSessionListener {
    void onSessionChanged(StardewFestivalSessionEvent event);
}
