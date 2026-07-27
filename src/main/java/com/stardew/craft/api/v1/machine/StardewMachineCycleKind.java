package com.stardew.craft.api.v1.machine;

/** Semantic production shapes used by the general machine-cycle API. */
public enum StardewMachineCycleKind {
    /** Consumes a finite input and stops after its output is collected. */
    BATCH,
    /** Retains a catalyst/template and starts another cycle after collection. */
    REPEATING,
    /** Produces on an internal schedule without a player-supplied input. */
    PASSIVE,
    /** Starts because the world environment supplied an external trigger. */
    ENVIRONMENTAL
}
