package com.stardew.craft.api.v1.action;

import com.mojang.serialization.Codec;

import java.util.Objects;

/** Codec and executor registered for one server-action type ID. */
public record StardewActionType<T>(
        Codec<T> codec,
        StardewActionExecutor<T> executor
) {
    public StardewActionType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(executor, "executor");
    }
}
