package com.stardew.craft.api.v1.extension;

import com.stardew.craft.api.v1.internal.state.NamespacedStateKeyRegistry;

import java.util.List;

/** Read-only diagnostics for registered namespaced persistent-state keys. */
public final class StardewStateDiagnostics {
    private StardewStateDiagnostics() {
    }

    public static List<StardewStateKeySnapshot> registeredKeys() {
        return NamespacedStateKeyRegistry.snapshots();
    }
}
