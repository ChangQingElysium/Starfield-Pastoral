package com.stardew.craft.api.v1.mapinteraction;

import com.mojang.serialization.Codec;

import java.util.Objects;

record StardewMapInteractionActionType<T>(
        Codec<T> codec,
        StardewMapInteractionActionExecutor<T> executor
) {
    public StardewMapInteractionActionType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(executor, "executor");
    }
}
