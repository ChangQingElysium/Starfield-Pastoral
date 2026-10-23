package com.stardew.craft.platform.fabric.data;

import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;

/** Matches the nullable custom-data fix type accepted by the original loader. */
public final class SavedDataFixes {
    private SavedDataFixes() {}

    public static CompoundTag update(DataFixTypes type, DataFixer fixer,
            CompoundTag data, int sourceVersion, int targetVersion) {
        return type == null ? data : type.update(fixer, data, sourceVersion, targetVersion);
    }
}
