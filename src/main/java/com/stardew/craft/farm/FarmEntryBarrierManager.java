package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nonnull;

/**
 * 在公共区域的三个农场入口处放置传送触发方块。
 * 农场内部保持不变；这里仅处理主地图侧入口衔接点。
 */
@SuppressWarnings("null")
public class FarmEntryBarrierManager extends SavedData {

    private static final String DATA_NAME = "stardew_farm_entry_barriers";
    private static final String TAG_FARM_ENTRY = "sdv_portal_marker:farm_entry";
    private static final int CURRENT_MAPPING_VERSION = 3;

    private boolean triggersPlaced = false;
    private int mappingVersion = 0;

    public FarmEntryBarrierManager() {}

    public static FarmEntryBarrierManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return new FarmEntryBarrierManager();
        return overworld.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    /**
     * 重置入口触发区放置状态，用于迁移时强制重新放置。
     */
    public void resetForMigration() {
        triggersPlaced = false;
        mappingVersion = 0;
        setDirty();
    }

        /**
         * 确保公共地图侧农场入口触发方块已放置。只执行一次。
         */
    public void ensureBarriersPlaced(ServerLevel stardewLevel) {
        if (triggersPlaced && mappingVersion >= CURRENT_MAPPING_VERSION) return;
        if (!ModDimensions.STARDEW_VALLEY.equals(stardewLevel.dimension())) return;

        StardewCraft.LOGGER.info("[FARM_ENTRY] Placing farm entry trigger blocks...");

        spawnFarmEntryInteractions(stardewLevel,
                new BlockPos(-63, 64, -44), new BlockPos(-63, 66, -42),
                "sdv_portal_target:farm_entry_west");

        spawnFarmEntryInteractions(stardewLevel,
                new BlockPos(-116, 64, -3), new BlockPos(-112, 66, -3),
                "sdv_portal_target:farm_entry_east");

        spawnFarmEntryInteractions(stardewLevel,
                new BlockPos(-116, 64, -58), new BlockPos(-112, 66, -58),
                "sdv_portal_target:farm_entry_south");

        triggersPlaced = true;
        mappingVersion = CURRENT_MAPPING_VERSION;
        setDirty();
        StardewCraft.LOGGER.info("[FARM_ENTRY] Farm entry triggers placed successfully.");
    }

    /**
     * 在指定范围内放置农场入口传送触发方块。
     */
    private void spawnFarmEntryInteractions(ServerLevel level, BlockPos min, BlockPos max, String targetTag) {
        int xBlocks = max.getX() - min.getX() + 1;
        int yBlocks = max.getY() - min.getY() + 1;
        int zBlocks = max.getZ() - min.getZ() + 1;
        InteriorSubspaceManager.placePortalTriggerArea(
            level, min, yBlocks, xBlocks, zBlocks,
            TAG_FARM_ENTRY, targetTag
        );
    }

    // ── NBT ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putBoolean("TriggersPlaced", triggersPlaced);
        tag.putInt("MappingVersion", mappingVersion);
        return tag;
    }

    private static FarmEntryBarrierManager load(CompoundTag tag, HolderLookup.Provider provider) {
        FarmEntryBarrierManager manager = new FarmEntryBarrierManager();
        manager.triggersPlaced = tag.getBoolean("TriggersPlaced") || tag.getBoolean("BarriersPlaced");
        manager.mappingVersion = tag.getInt("MappingVersion");
        return manager;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<FarmEntryBarrierManager> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(FarmEntryBarrierManager::new, FarmEntryBarrierManager::load);
    }
}
