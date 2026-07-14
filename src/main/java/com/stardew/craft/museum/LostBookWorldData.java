package com.stardew.craft.museum;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/** World-shared lost-book discovery count, matching SDV's NetWorldState field. */
public final class LostBookWorldData extends SavedData {
    private static final String DATA_NAME = "stardewcraft_lost_books";
    private int foundCount;

    public static LostBookWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LostBookWorldData::new, LostBookWorldData::load), DATA_NAME);
    }

    public int foundCount() {
        return foundCount;
    }

    public boolean discoverNext(int maximum) {
        int cappedMaximum = Math.max(0, maximum);
        if (foundCount >= cappedMaximum) return false;
        foundCount++;
        setDirty();
        return true;
    }

    public void setFoundCount(int value, int maximum) {
        int next = Math.max(0, Math.min(value, Math.max(0, maximum)));
        if (foundCount == next) return;
        foundCount = next;
        setDirty();
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt("FoundCount", foundCount);
        return tag;
    }

    private static LostBookWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        LostBookWorldData data = new LostBookWorldData();
        data.foundCount = Math.max(0, tag.getInt("FoundCount"));
        return data;
    }
}
