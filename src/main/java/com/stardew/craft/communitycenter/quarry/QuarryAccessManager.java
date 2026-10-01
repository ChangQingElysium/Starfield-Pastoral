package com.stardew.craft.communitycenter.quarry;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/**
 * 采石场访问门：工艺室献祭 (ccCraftsRoom) 对应的小镇→采石场通行逻辑。
 * 这里仅放置已迁移的传送触发区；地图屏障已内置，不运行旧墙体放置。
 */
@SuppressWarnings("null")
public class QuarryAccessManager extends SavedData {

    private static final String DATA_NAME = "stardew_quarry_access";
    private static final String TAG_QUARRY = "sdv_portal_marker:quarry_access";

    /**
    * 放置逻辑版本号。坐标有改动时把这个数 +1，老存档下次加载会自动
     * 清除旧放置标记并重新放置（ensurePlaced 会检测 placedVersion < CURRENT_VERSION）。
     */
    private static final int CURRENT_VERSION = 4;

    // ── 采石场区域（与 QuarrySpawnService 保持一致） ──
    public static final int QUARRY_MIN_X = 155;
    public static final int QUARRY_MAX_X = 194;
    public static final int QUARRY_MIN_Z = -140;
    public static final int QUARRY_MAX_Z = -101;

    /** 判断坐标是否在采石场矩形内（忽略 Y，整个立方柱都算采石场，保证挖深也能操作）。 */
    public static boolean isInQuarryArea(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return x >= QUARRY_MIN_X && x <= QUARRY_MAX_X
                && z >= QUARRY_MIN_Z && z <= QUARRY_MAX_Z;
    }

    // ── 镇子侧触发方块（3 格高，覆盖玩家身高） ──
    private static final int TOWN_TRIGGER_X = 137;
    private static final int TOWN_TRIGGER_Y_MIN = 81;
    private static final int TOWN_TRIGGER_Y_MAX = 83;
    private static final int TOWN_TRIGGER_Z_MIN = -119;
    private static final int TOWN_TRIGGER_Z_MAX = -115;

    // ── 采石场侧返程触发方块 ──
    private static final int QUARRY_TRIGGER_X = 139;
    private static final int QUARRY_TRIGGER_Y_MIN = 81;
    private static final int QUARRY_TRIGGER_Y_MAX = 83;
    private static final int QUARRY_TRIGGER_Z_MIN = -119;
    private static final int QUARRY_TRIGGER_Z_MAX = -115;

    // ── 传送目标 ──
    /** 镇子 → 采石场：落在采石场侧（墙东），面朝东走入采石场。 */
    public static final double ENTRY_DEST_X = 140 + 0.5;
    public static final double ENTRY_DEST_Y = 81.0;
    public static final double ENTRY_DEST_Z = -117 + 0.5;
    public static final float ENTRY_DEST_YAW = -90.0F;
    public static final float ENTRY_DEST_PITCH = 0.0F;

    /** 采石场 → 镇子：落在镇子侧（墙西），面朝西走回小镇。 */
    public static final double EXIT_DEST_X = 136 + 0.5;
    public static final double EXIT_DEST_Y = 83.0;
    public static final double EXIT_DEST_Z = -117 + 0.5;
    public static final float EXIT_DEST_YAW = 90.0F;
    public static final float EXIT_DEST_PITCH = 0.0F;

    /** 上次已应用的放置版本。0 = 从未放置。 */
    private int placedVersion = 0;

    public QuarryAccessManager() {}

    public static QuarryAccessManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return new QuarryAccessManager();
        return overworld.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    public void resetForMigration() {
        placedVersion = 0;
        setDirty();
    }

    /**
    * 确保两侧传送触发方块已放置。基于版本号幂等：
     * 如果 placedVersion < CURRENT_VERSION，会重新运行放置逻辑（覆盖旧坐标），
     * 让老存档在坐标被改动后能自动升级。
     */
    public void ensurePlaced(ServerLevel stardewLevel) {
        if (placedVersion >= CURRENT_VERSION) return;
        if (!ModDimensions.STARDEW_VALLEY.equals(stardewLevel.dimension())) return;

        StardewCraft.LOGGER.info("[QUARRY] Placing quarry access triggers (oldVersion={}, newVersion={})",
                placedVersion, CURRENT_VERSION);

        placeTrigger(stardewLevel,
                TOWN_TRIGGER_X, TOWN_TRIGGER_Y_MIN, TOWN_TRIGGER_Z_MIN,
                TOWN_TRIGGER_X, TOWN_TRIGGER_Y_MAX, TOWN_TRIGGER_Z_MAX,
                "sdv_portal_target:quarry_entrance");
        placeTrigger(stardewLevel,
                QUARRY_TRIGGER_X, QUARRY_TRIGGER_Y_MIN, QUARRY_TRIGGER_Z_MIN,
                QUARRY_TRIGGER_X, QUARRY_TRIGGER_Y_MAX, QUARRY_TRIGGER_Z_MAX,
                "sdv_portal_target:quarry_exit");

        placedVersion = CURRENT_VERSION;
        setDirty();
        StardewCraft.LOGGER.info("[QUARRY] Quarry access setup complete.");
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
                TAG_QUARRY, targetTag);
    }

    // ── NBT ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("PlacedVersion", placedVersion);
        return tag;
    }

    private static QuarryAccessManager load(CompoundTag tag, HolderLookup.Provider provider) {
        QuarryAccessManager manager = new QuarryAccessManager();
        if (tag.contains("PlacedVersion")) {
            manager.placedVersion = tag.getInt("PlacedVersion");
        } else if (tag.contains("Placed")) {
            // 旧存档兼容：老 Placed=true 视为版本 1（即当前实现的首个版本）
            manager.placedVersion = tag.getBoolean("Placed") ? 1 : 0;
        }
        return manager;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<QuarryAccessManager> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(QuarryAccessManager::new, QuarryAccessManager::load);
    }
}
