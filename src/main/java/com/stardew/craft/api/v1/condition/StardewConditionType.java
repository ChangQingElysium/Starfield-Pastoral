package com.stardew.craft.api.v1.condition;

import com.mojang.serialization.Codec;

import java.util.Objects;

/** Codec and server evaluator registered for one condition type ID. */
public record StardewConditionType<T>(
        Codec<T> codec,
        StardewConditionEvaluator<T> evaluator
) {
    public StardewConditionType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(evaluator, "evaluator");
    }
}
