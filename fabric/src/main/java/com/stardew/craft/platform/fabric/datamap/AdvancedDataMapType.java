package com.stardew.craft.platform.fabric.datamap;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class AdvancedDataMapType<R, T, VR extends DataMapValueRemover<R, T>> extends DataMapType<R, T> {
    private final Codec<VR> remover;
    private final DataMapValueMerger<R, T> merger;

    private AdvancedDataMapType(Builder<T, R, VR> builder) {
        super(builder);
        remover = Objects.requireNonNull(builder.remover, "remover");
        merger = Objects.requireNonNull(builder.merger, "merger");
    }

    public Codec<VR> remover() { return remover; }
    public DataMapValueMerger<R, T> merger() { return merger; }

    public static <T, R> Builder<T, R, DataMapValueRemover.Default<T, R>> builder(
            ResourceLocation id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
        return new Builder<T, R, DataMapValueRemover.Default<T, R>>(id, registry, codec)
                .remover(DataMapValueRemover.Default.codec());
    }

    public static final class Builder<T, R, VR extends DataMapValueRemover<R, T>> extends DataMapType.Builder<T, R> {
        private Codec<VR> remover;
        private DataMapValueMerger<R, T> merger = DataMapValueMerger.defaultMerger();

        private Builder(ResourceLocation id, ResourceKey<Registry<R>> registry, Codec<T> codec) { super(id, registry, codec); }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <VR1 extends DataMapValueRemover<R, T>> Builder<T, R, VR1> remover(Codec<VR1> codec) {
            remover = (Codec) Objects.requireNonNull(codec, "remover");
            return (Builder) this;
        }

        public Builder<T, R, VR> merger(DataMapValueMerger<R, T> merger) {
            this.merger = Objects.requireNonNull(merger, "merger");
            return this;
        }

        @Override public Builder<T, R, VR> synced(Codec<T> codec, boolean mandatory) { super.synced(codec, mandatory); return this; }
        @Override public AdvancedDataMapType<R, T, VR> build() { return new AdvancedDataMapType<>(this); }
    }
}
