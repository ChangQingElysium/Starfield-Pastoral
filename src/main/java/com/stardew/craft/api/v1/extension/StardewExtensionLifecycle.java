package com.stardew.craft.api.v1.extension;

/** Registration lifecycle exposed by extension-point diagnostics. */
public enum StardewExtensionLifecycle {
    /** Java addons may still register handlers. */
    REGISTERING,
    /** The server has started and the ordered handler snapshot is immutable. */
    FROZEN
}
