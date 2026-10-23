package com.stardew.craft.platform.fabric.data;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Retains the existing mod-owned SavedData format without invoking a vanilla DFU schema. */
public final class SavedDataFactories {
    private SavedDataFactories() {}

    public static <T extends SavedData> SavedData.Factory<T> create(Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> reader) {
        return new SavedData.Factory<>(constructor, reader, null);
    }

    public static <T extends SavedData> SavedData.Factory<T> create(Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> reader, DataFixTypes type) {
        return new SavedData.Factory<>(constructor, reader, type);
    }
}
