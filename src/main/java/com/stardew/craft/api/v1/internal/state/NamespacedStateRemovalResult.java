package com.stardew.craft.api.v1.internal.state;

/** Result of applying an operator-confirmed raw state removal. */
public enum NamespacedStateRemovalResult {
    APPLIED,
    MISSING,
    STALE,
    NO_LONGER_ELIGIBLE
}
