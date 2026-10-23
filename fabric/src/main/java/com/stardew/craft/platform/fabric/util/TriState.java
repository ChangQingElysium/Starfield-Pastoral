package com.stardew.craft.platform.fabric.util;

/** An event decision; DEFAULT leaves the underlying vanilla decision intact. */
public enum TriState {
    TRUE, DEFAULT, FALSE;

    public boolean isTrue() {
        return this == TRUE;
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }

    public boolean isFalse() {
        return this == FALSE;
    }
}
