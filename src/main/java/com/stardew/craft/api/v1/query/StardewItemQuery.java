package com.stardew.craft.api.v1.query;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** An item query payload decoded through a registered namespaced type. */
public final class StardewItemQuery {
    private final ResourceLocation type;
    private final Object data;

    StardewItemQuery(ResourceLocation type, Object data) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = Objects.requireNonNull(data, "data");
    }

    public ResourceLocation type() {
        return type;
    }

    Object data() {
        return data;
    }
}
