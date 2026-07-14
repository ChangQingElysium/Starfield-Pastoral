package com.stardew.craft.api.v1.specialorder;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class StardewSpecialOrderReward {
    private final ResourceLocation type;
    private final Object data;

    StardewSpecialOrderReward(ResourceLocation type, Object data) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = Objects.requireNonNull(data, "data");
    }

    public ResourceLocation type() { return type; }
    Object data() { return data; }
}
