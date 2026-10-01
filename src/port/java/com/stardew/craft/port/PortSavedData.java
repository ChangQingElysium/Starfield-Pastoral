package com.stardew.craft.port;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.21 {@code SavedData.Factory}. 1.20.1 {@code DimensionDataStorage} takes the loader and the
 * constructor separately; call sites pass {@link #loader} / {@link #constructor} of the factory.
 */
public final class PortSavedData {
    private PortSavedData() {
    }

    public record Factory<T extends SavedData>(
            Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer,
            DataFixTypes type) {
        public Factory(Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer) {
            this(constructor, deserializer, null);
        }
    }

    public static <T extends SavedData> Function<CompoundTag, T> loader(Factory<T> factory) {
        return tag -> factory.deserializer().apply(tag, PortRegistries.lookup());
    }

    public static <T extends SavedData> Supplier<T> constructor(Factory<T> factory) {
        return factory.constructor();
    }
}
