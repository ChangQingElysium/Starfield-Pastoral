package com.stardew.craft.api.v1.query;

import com.mojang.serialization.Codec;

import java.util.Objects;

/** Codec and resolver registered for one item-query type ID. */
public record StardewItemQueryType<T>(
        Codec<T> codec,
        StardewItemQueryResolver<T> resolver
) {
    public StardewItemQueryType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(resolver, "resolver");
    }
}
