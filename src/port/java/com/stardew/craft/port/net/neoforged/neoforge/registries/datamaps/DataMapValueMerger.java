package com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/** PORT(1.20.1): NeoForge data map value merger. */
@FunctionalInterface
public interface DataMapValueMerger<R, T> {
    T merge(Registry<R> registry, Either<TagKey<R>, ResourceKey<R>> first, T firstValue, Either<TagKey<R>, ResourceKey<R>> second, T secondValue);

    static <T, R> DataMapValueMerger<R, T> defaultMerger() {
        return (registry, first, firstValue, second, secondValue) -> secondValue;
    }
}
