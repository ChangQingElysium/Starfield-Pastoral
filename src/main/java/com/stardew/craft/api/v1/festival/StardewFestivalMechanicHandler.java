package com.stardew.craft.api.v1.festival;

/**
 * One additive mechanic layer for a festival.
 *
 * <p>Handlers are failure-isolated and ordered by registration priority. A
 * festival can therefore combine independent NPC, reward, minigame or
 * presentation layers instead of requiring one monolithic handler.
 */
public interface StardewFestivalMechanicHandler {
    default void tick(StardewFestivalMechanicContext context) {
    }

    default void onSessionChanged(
            StardewFestivalMechanicContext context,
            StardewFestivalSessionEvent event
    ) {
    }
}
