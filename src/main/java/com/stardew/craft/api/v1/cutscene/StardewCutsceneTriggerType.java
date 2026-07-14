package com.stardew.craft.api.v1.cutscene;

import com.mojang.serialization.Codec;

import java.util.Objects;

public record StardewCutsceneTriggerType<T>(
        Codec<T> codec,
        StardewCutsceneTriggerEvaluator<T> evaluator
) {
    public StardewCutsceneTriggerType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(evaluator, "evaluator");
    }
}
