package com.stardew.craft.api.v1.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A quest objective payload decoded through a registered namespaced type. */
public final class StardewQuestObjective {
    private final ResourceLocation type;
    private final Object data;

    StardewQuestObjective(ResourceLocation type, Object data) {
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
