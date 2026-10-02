package com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge data map type. Values are loaded from {@code data/<ns>/data_maps/<registry>/<path>.json}
 * by {@code com.stardew.craft.port.PortDataMaps} and read with {@code PortDataMaps.getData(holder, type)}.
 */
public sealed class DataMapType<R, T> permits AdvancedDataMapType {
    private final ResourceKey<Registry<R>> registryKey;
    private final ResourceLocation id;
    private final Codec<T> codec;
    @Nullable
    private final Codec<T> networkCodec;
    private final boolean mandatorySync;

    DataMapType(ResourceKey<Registry<R>> registryKey, ResourceLocation id, Codec<T> codec, @Nullable Codec<T> networkCodec, boolean mandatorySync) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.id = Objects.requireNonNull(id);
        this.codec = Objects.requireNonNull(codec);
        this.networkCodec = networkCodec;
        this.mandatorySync = mandatorySync;
    }

    public static <T, R> Builder<T, R> builder(ResourceLocation id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
        return new Builder<>(registry, id, codec);
    }

    public ResourceKey<Registry<R>> registryKey() {
        return registryKey;
    }

    public ResourceLocation id() {
        return id;
    }

    public Codec<T> codec() {
        return codec;
    }

    @Nullable
    public Codec<T> networkCodec() {
        return networkCodec;
    }

    public boolean mandatorySync() {
        return mandatorySync;
    }

    @Override
    public String toString() {
        return "DataMapType[" + registryKey.location() + " -> " + id + "]";
    }

    public static sealed class Builder<T, R> permits AdvancedDataMapType.Builder {
        protected final ResourceKey<Registry<R>> registryKey;
        protected final ResourceLocation id;
        protected final Codec<T> codec;
        @Nullable
        protected Codec<T> networkCodec;
        protected boolean mandatorySync;

        Builder(ResourceKey<Registry<R>> registryKey, ResourceLocation id, Codec<T> codec) {
            this.registryKey = registryKey;
            this.id = id;
            this.codec = codec;
        }

        public Builder<T, R> synced(Codec<T> networkCodec, boolean mandatory) {
            this.networkCodec = Objects.requireNonNull(networkCodec);
            this.mandatorySync = mandatory;
            return this;
        }

        public DataMapType<R, T> build() {
            return new DataMapType<>(registryKey, id, codec, networkCodec, mandatorySync);
        }
    }
}
