package com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/** PORT(1.20.1): NeoForge data map value remover. */
@FunctionalInterface
public interface DataMapValueRemover<R, T> {
    Optional<T> remove(T value, Registry<R> registry, Either<TagKey<R>, ResourceKey<R>> source, R object);

    final class Default<T, R> implements DataMapValueRemover<R, T> {
        public static final Default<?, ?> INSTANCE = new Default<>();

        @SuppressWarnings("unchecked")
        public static <T, R> Default<T, R> defaultRemover() {
            return (Default<T, R>) INSTANCE;
        }

        public static <T, R> Codec<Default<T, R>> codec() {
            return Codec.unit(defaultRemover());
        }

        private Default() {}

        @Override
        public Optional<T> remove(T value, Registry<R> registry, Either<TagKey<R>, ResourceKey<R>> source, R object) {
            return Optional.empty();
        }
    }
}
