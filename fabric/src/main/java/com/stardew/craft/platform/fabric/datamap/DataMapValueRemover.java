package com.stardew.craft.platform.fabric.datamap;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

@FunctionalInterface
public interface DataMapValueRemover<R, T> {
    Optional<T> remove(T value, Registry<R> registry, Either<TagKey<R>, ResourceKey<R>> source, R object);

    final class Default<T, R> implements DataMapValueRemover<R, T> {
        private static final Default<?, ?> INSTANCE = new Default<>();
        private Default() {}
        @SuppressWarnings("unchecked")
        public static <T, R> Default<T, R> defaultRemover() { return (Default<T, R>) INSTANCE; }
        public static <T, R> Codec<Default<T, R>> codec() { return Codec.unit(defaultRemover()); }
        @Override public Optional<T> remove(T value, Registry<R> registry,
                Either<TagKey<R>, ResourceKey<R>> source, R object) { return Optional.empty(); }
    }
}
