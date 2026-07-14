package com.stardew.craft.api.v1.action;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A server action payload decoded through a registered namespaced type. */
public final class StardewAction {
    private final ResourceLocation type;
    private final Object data;

    StardewAction(ResourceLocation type, Object data) {
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
