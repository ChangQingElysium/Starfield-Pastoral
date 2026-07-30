package com.stardew.craft.api.v1.mapinteraction;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Decoded data-pack action with payload owned by its registered type. */
public final class StardewMapInteractionAction {
    private final ResourceLocation type;
    private final Object data;

    StardewMapInteractionAction(
            ResourceLocation type,
            Object data
    ) {
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
