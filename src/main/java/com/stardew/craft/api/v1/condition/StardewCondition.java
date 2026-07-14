package com.stardew.craft.api.v1.condition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A condition payload decoded through a registered namespaced type. */
public final class StardewCondition {
    private final ResourceLocation type;
    private final Object data;

    StardewCondition(ResourceLocation type, Object data) {
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
