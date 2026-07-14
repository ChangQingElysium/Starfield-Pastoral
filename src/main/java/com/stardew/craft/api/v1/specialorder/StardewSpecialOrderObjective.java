package com.stardew.craft.api.v1.specialorder;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Decoded custom objective payload stored in a reloadable special-order definition. */
public final class StardewSpecialOrderObjective {
    private final ResourceLocation type;
    private final Object data;

    StardewSpecialOrderObjective(ResourceLocation type, Object data) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = Objects.requireNonNull(data, "data");
    }

    public ResourceLocation type() { return type; }
    Object data() { return data; }
}
