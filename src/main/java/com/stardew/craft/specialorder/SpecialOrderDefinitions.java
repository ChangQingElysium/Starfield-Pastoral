package com.stardew.craft.specialorder;

import java.util.List;

/** Compatibility facade over the reloadable special-order registry. */
public final class SpecialOrderDefinitions {
    private SpecialOrderDefinitions() {
    }

    public static List<SpecialOrderDefinition> all() {
        return SpecialOrderDataLoader.all();
    }

    public static SpecialOrderDefinition get(String id) {
        return SpecialOrderDataLoader.get(id);
    }
}
