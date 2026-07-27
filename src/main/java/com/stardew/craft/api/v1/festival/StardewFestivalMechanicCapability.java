package com.stardew.craft.api.v1.festival;

/**
 * Coarse capabilities contributed by one festival mechanic layer.
 *
 * <p>Capabilities are descriptive. They let other addons discover integration
 * opportunities without coupling to a concrete festival implementation.
 */
public enum StardewFestivalMechanicCapability {
    SESSION_LIFECYCLE,
    PARTICIPANTS,
    MAP_OVERLAY,
    NPC_ACTORS,
    DIALOGUE,
    SHOPS,
    MAIN_EVENT,
    MINIGAME,
    REWARDS,
    CURRENCY,
    PERSISTENT_STATE,
    CLIENT_PRESENTATION
}
