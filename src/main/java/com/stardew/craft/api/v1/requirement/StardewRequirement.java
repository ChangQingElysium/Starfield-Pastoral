package com.stardew.craft.api.v1.requirement;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One server-evaluated condition rendered as a reusable lock/requirement row. */
public record StardewRequirement(
        ResourceLocation type,
        State state,
        Component description,
        boolean specific
) {
    public StardewRequirement {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        description = Objects.requireNonNull(description, "description").copy();
    }

    @Override
    public Component description() {
        return description.copy();
    }

    public boolean blocks() {
        return state != State.SATISFIED;
    }

    public enum State {
        SATISFIED,
        UNSATISFIED,
        ERROR
    }
}
