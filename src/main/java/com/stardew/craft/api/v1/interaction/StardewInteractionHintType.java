package com.stardew.craft.api.v1.interaction;

/** Stable semantic types used by the crosshair-side world interaction hint. */
public enum StardewInteractionHintType {
    GRAB,
    GIFT,
    TALK,
    LOOK,
    HARVEST;

    private static final StardewInteractionHintType[] VALUES = values();

    public int networkId() {
        return ordinal();
    }

    public static StardewInteractionHintType byNetworkId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : GRAB;
    }
}
