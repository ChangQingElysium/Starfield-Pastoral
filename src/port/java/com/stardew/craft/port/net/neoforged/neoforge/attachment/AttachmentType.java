package com.stardew.craft.port.net.neoforged.neoforge.attachment;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge data attachment type. Values are stored in a Forge capability (see
 * {@code com.stardew.craft.port.PortAttachments}), serialized with the attachment's codec.
 */
public final class AttachmentType<T> {
    final Supplier<T> defaultValueSupplier;
    @Nullable
    final Codec<T> codec;
    final Predicate<? super T> shouldSerialize;
    final boolean copyOnDeath;

    private AttachmentType(Builder<T> builder) {
        this.defaultValueSupplier = builder.defaultValueSupplier;
        this.codec = builder.codec;
        this.shouldSerialize = builder.shouldSerialize;
        this.copyOnDeath = builder.copyOnDeath;
    }

    public static <T> Builder<T> builder(Supplier<T> defaultValueSupplier) {
        return new Builder<>(defaultValueSupplier);
    }

    public T createDefault() {
        return defaultValueSupplier.get();
    }

    @Nullable
    public Codec<T> codec() {
        return codec;
    }

    public boolean shouldSerialize(T value) {
        return codec != null && shouldSerialize.test(value);
    }

    public boolean copyOnDeath() {
        return copyOnDeath;
    }

    public static class Builder<T> {
        private final Supplier<T> defaultValueSupplier;
        @Nullable
        private Codec<T> codec;
        private Predicate<? super T> shouldSerialize = value -> true;
        private boolean copyOnDeath;

        private Builder(Supplier<T> defaultValueSupplier) {
            this.defaultValueSupplier = Objects.requireNonNull(defaultValueSupplier);
        }

        public Builder<T> serialize(Codec<T> codec) {
            return serialize(codec, value -> true);
        }

        public Builder<T> serialize(Codec<T> codec, Predicate<? super T> shouldSerialize) {
            if (this.codec != null) throw new IllegalStateException("Serializer already set");
            this.codec = Objects.requireNonNull(codec);
            this.shouldSerialize = Objects.requireNonNull(shouldSerialize);
            return this;
        }

        public Builder<T> copyOnDeath() {
            if (codec == null) throw new IllegalStateException("copyOnDeath requires a serializer");
            this.copyOnDeath = true;
            return this;
        }

        public AttachmentType<T> build() {
            return new AttachmentType<>(this);
        }
    }
}
