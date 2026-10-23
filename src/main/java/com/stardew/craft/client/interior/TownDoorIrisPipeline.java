package com.stardew.craft.client.interior;

/**
 * Captures the vanilla pipeline Iris already constructs during its normal lifecycle.
 * Constructing another one while a pack is active resets WorldRenderingSettings (including
 * Sodium's vertex format and the pack's water/material mappings) for the entire client.
 */
public final class TownDoorIrisPipeline {
    private static Object vanilla;
    private static Object nested;

    private TownDoorIrisPipeline() {}

    public static Object vanilla() {
        return vanilla;
    }

    public static Object nested() {
        return nested != null ? nested : vanilla;
    }

    static Object exchangeNested(Object pipeline) {
        Object previous = nested;
        nested = pipeline;
        return previous;
    }

    public static void captureVanilla(Object pipeline) {
        vanilla = java.util.Objects.requireNonNull(pipeline);
    }
}
