package com.stardew.craft.time;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Forge 1.20.1 persistence adapter for the shared Stardew season value.
 *
 * <p>This deliberately exposes the same semantic operation needed by the
 * leaves runtime ({@link #getCurrentSeason(ServerLevel)}) without importing
 * NeoForge's full clock, overnight settlement, or network implementation.</p>
 */
public final class ForgeStardewTimeService extends SavedData {
    private static final String DATA_NAME = "stardew_time_data";
    // Keep the 1.21.1 SavedData name/key so an eventual full Forge clock can
    // read the same world data without a migration fork.
    private static final String CURRENT_SEASON = "currentSeason";
    private int currentSeason;

    public static ForgeStardewTimeService get(ServerLevel level) {
        // StardewTimeManager is a server-global clock stored in the overworld,
        // even when a caller is currently inside the Stardew dimension.
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(
                ForgeStardewTimeService::load,
                ForgeStardewTimeService::new,
                DATA_NAME);
    }

    public static int getCurrentSeason(ServerLevel level) {
        return get(level).currentSeason;
    }

    public static void setCurrentSeason(ServerLevel level, int season) {
        if (season < 0 || season > 3) {
            throw new IllegalArgumentException("Stardew season must be in range 0..3");
        }
        ForgeStardewTimeService data = get(level);
        if (data.currentSeason != season) {
            data.currentSeason = season;
            data.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(CURRENT_SEASON, currentSeason);
        return tag;
    }

    public static ForgeStardewTimeService load(CompoundTag tag) {
        ForgeStardewTimeService data = new ForgeStardewTimeService();
        int season = tag.contains(CURRENT_SEASON) ? tag.getInt(CURRENT_SEASON) : tag.getInt("CurrentSeason");
        data.currentSeason = Math.max(0, Math.min(3, season));
        return data;
    }
}
