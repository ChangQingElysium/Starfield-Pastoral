package com.stardew.craft.api.v1.extension;

/** Result of explicitly applying a namespaced-state migration preview. */
public enum StardewStateMigrationResult {
    /** The preview still matched and the migrated payload was committed. */
    APPLIED,
    /** The entry no longer exists. */
    MISSING,
    /** The entry changed after preview creation, so nothing was written. */
    STALE
}
