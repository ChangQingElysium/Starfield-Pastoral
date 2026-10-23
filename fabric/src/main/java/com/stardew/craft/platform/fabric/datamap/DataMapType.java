package com.stardew.craft.platform.fabric.datamap;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Project-owned registry attachments, loaded from the existing data_maps pack format. */
public class DataMapType<R, T> {
    private final ResourceKey<Registry<R>> registry;
    private final ResourceLocation id;
    private final Codec<T> codec;
    private final @Nullable Codec<T> networkCodec;
    private final boolean mandatory;

    protected DataMapType(Builder<T, R> builder) {
        registry = Objects.requireNonNull(builder.registry, "registry");
        id = Objects.requireNonNull(builder.id, "id");
        codec = Objects.requireNonNull(builder.codec, "codec");
        networkCodec = builder.networkCodec;
        mandatory = builder.mandatory;
        if (mandatory && networkCodec == null) throw new IllegalArgumentException("Mandatory maps must have a network codec");
    }

    public static <T, R> Builder<T, R> builder(ResourceLocation id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
        return new Builder<>(id, registry, codec);
    }

    public ResourceKey<Registry<R>> registryKey() { return registry; }
    public ResourceLocation id() { return id; }
    public Codec<T> codec() { return codec; }
    public @Nullable Codec<T> networkCodec() { return networkCodec; }
    public boolean mandatorySync() { return mandatory; }

    public static class Builder<T, R> {
        protected final ResourceLocation id;
        protected final ResourceKey<Registry<R>> registry;
        protected final Codec<T> codec;
        protected Codec<T> networkCodec;
        protected boolean mandatory;

        protected Builder(ResourceLocation id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
            this.id = id;
            this.registry = registry;
            this.codec = codec;
        }

        public Builder<T, R> synced(Codec<T> codec, boolean mandatory) {
            networkCodec = Objects.requireNonNull(codec, "network codec");
            this.mandatory = mandatory;
            return this;
        }

        public DataMapType<R, T> build() { return new DataMapType<>(this); }
    }
}
