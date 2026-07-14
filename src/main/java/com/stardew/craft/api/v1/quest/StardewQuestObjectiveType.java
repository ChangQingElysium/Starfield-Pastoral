package com.stardew.craft.api.v1.quest;

import com.mojang.serialization.Codec;

import java.util.Objects;

/** Codec and runtime factory registered under one namespaced objective type ID. */
public record StardewQuestObjectiveType<T>(
        Codec<T> codec,
        StardewQuestObjectiveFactory<T> factory
) {
    public StardewQuestObjectiveType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(factory, "factory");
    }
}
