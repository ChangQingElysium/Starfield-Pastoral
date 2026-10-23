package com.stardew.craft.platform.fabric.datamap;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

@FunctionalInterface
public interface DataMapValueMerger<R, T> {
    T merge(Registry<R> registry, Either<TagKey<R>, ResourceKey<R>> first, T firstValue,
            Either<TagKey<R>, ResourceKey<R>> second, T secondValue);

    static <T, R> DataMapValueMerger<R, T> defaultMerger() { return (registry, a, old, b, newer) -> newer; }
    static <T, R> DataMapValueMerger<R, List<T>> listMerger() {
        return (registry, a, old, b, newer) -> { var result = new ArrayList<>(old); result.addAll(newer); return result; };
    }
    static <T, R> DataMapValueMerger<R, Set<T>> setMerger() {
        return (registry, a, old, b, newer) -> { var result = new HashSet<>(old); result.addAll(newer); return result; };
    }
    static <K, V, R> DataMapValueMerger<R, Map<K, V>> mapMerger() {
        return (registry, a, old, b, newer) -> { var result = new HashMap<>(old); result.putAll(newer); return result; };
    }
}
