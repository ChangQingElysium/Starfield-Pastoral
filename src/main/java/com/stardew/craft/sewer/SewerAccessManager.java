package com.stardew.craft.sewer;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class SewerAccessManager extends SavedData {
    private static final String DATA_NAME = "stardew_sewer_access";
    private static final String TAG_SEWER = "sdv_portal_marker:sewer_access";
    private static final int CURRENT_VERSION = 2;

    private static final int TOWN_TRIGGER_X = 14;
    private static final int TOWN_TRIGGER_Y = 64;
    private static final int TOWN_TRIGGER_Z = 49;

    private static final int FOREST_TRIGGER_X_MIN = -102;
    private static final int FOREST_TRIGGER_X_MAX = -100;
    private static final int FOREST_TRIGGER_Y = 62;
    private static final int FOREST_TRIGGER_Z = 132;

    private static final int SEWER_EXIT_X = 14;
    private static final int SEWER_EXIT_Y_MIN = 51;
    private static final int SEWER_EXIT_Y_MAX = 52;
    private static final int SEWER_EXIT_Z = 49;

    public static final double ENTRY_DEST_X = 14.5D;
    public static final double ENTRY_DEST_Y = 51.0D;
    public static final double ENTRY_DEST_Z = 50.5D;
    public static final float ENTRY_DEST_YAW = 180.0F;
    public static final float ENTRY_DEST_PITCH = 0.0F;

    public static final double EXIT_DEST_X = 14.5D;
    public static final double EXIT_DEST_Y = 64.0D;
    public static final double EXIT_DEST_Z = 50.5D;
    public static final float EXIT_DEST_YAW = 180.0F;
    public static final float EXIT_DEST_PITCH = 0.0F;

    private int placedVersion = 0;

    public SewerAccessManager() {
    }

    public static SewerAccessManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return new SewerAccessManager();
        return overworld.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    public void resetForMigration() {
        placedVersion = 0;
        setDirty();
    }

    public void ensurePlaced(ServerLevel stardewLevel) {
        if (placedVersion >= CURRENT_VERSION) return;
        if (!ModDimensions.STARDEW_VALLEY.equals(stardewLevel.dimension())) return;

        StardewCraft.LOGGER.info("[SEWER] Placing sewer access triggers (oldVersion={}, newVersion={})",
                placedVersion, CURRENT_VERSION);

        placeTrigger(stardewLevel,
                TOWN_TRIGGER_X, TOWN_TRIGGER_Y, TOWN_TRIGGER_Z,
                TOWN_TRIGGER_X, TOWN_TRIGGER_Y, TOWN_TRIGGER_Z,
                "sdv_portal_target:sewer_enter");
        placeTrigger(stardewLevel,
                FOREST_TRIGGER_X_MIN, FOREST_TRIGGER_Y, FOREST_TRIGGER_Z,
                FOREST_TRIGGER_X_MAX, FOREST_TRIGGER_Y, FOREST_TRIGGER_Z,
                "sdv_portal_target:sewer_enter");
        placeTrigger(stardewLevel,
                SEWER_EXIT_X, SEWER_EXIT_Y_MIN, SEWER_EXIT_Z,
                SEWER_EXIT_X, SEWER_EXIT_Y_MAX, SEWER_EXIT_Z,
                "sdv_portal_target:sewer_exit");

        placedVersion = CURRENT_VERSION;
        setDirty();
        StardewCraft.LOGGER.info("[SEWER] Sewer access setup complete.");
    }

    private void placeTrigger(ServerLevel level,
                              int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ,
                              String targetTag) {
        int lx = Math.min(minX, maxX);
        int ly = Math.min(minY, maxY);
        int lz = Math.min(minZ, maxZ);
        int hx = Math.max(minX, maxX);
        int hy = Math.max(minY, maxY);
        int hz = Math.max(minZ, maxZ);
        int xBlocks = hx - lx + 1;
        int yBlocks = hy - ly + 1;
        int zBlocks = hz - lz + 1;
        InteriorSubspaceManager.placePortalTriggerArea(
                level, new BlockPos(lx, ly, lz),
                yBlocks, xBlocks, zBlocks,
                TAG_SEWER, targetTag);
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("PlacedVersion", placedVersion);
        return tag;
    }

    private static SewerAccessManager load(CompoundTag tag, HolderLookup.Provider provider) {
        SewerAccessManager manager = new SewerAccessManager();
        if (tag.contains("PlacedVersion")) {
            manager.placedVersion = tag.getInt("PlacedVersion");
        }
        return manager;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<SewerAccessManager> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(SewerAccessManager::new, SewerAccessManager::load);
    }
}