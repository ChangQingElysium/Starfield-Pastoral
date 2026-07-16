package com.stardew.craft.minecart;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 矿车站点实体和轨道的初始化 manager。幂等；版本号不匹配时会清掉旧实体再重放。
 *
 * <p>站点位置（实体坐标）：
 * <ul>
 *   <li>Town: (123, 64, 26) @ STARDEW_VALLEY</li>
 *   <li>Mines: (-7, 66, -12) @ STARDEW_MINING, 前置铺 (-16..-7, 66, -12) 的铁轨</li>
 *   <li>Bus: (-76, 64, -70) @ STARDEW_VALLEY</li>
 *   <li>Quarry: (187, 81, -141) @ STARDEW_VALLEY</li>
 * </ul>
 */
@SuppressWarnings("null")
public class MinecartStationManager extends SavedData {

    private static final String DATA_NAME = "stardew_minecart_stations";

    /** 改站点坐标或铁轨范围后 +1，老存档会清旧实体再重放。 */
    private static final int CURRENT_VERSION = 3;

    /** 模型默认是东西朝向；旋转 90 度后统一为南北朝向。 */
    private static final float STATION_Y_ROT = 90.0F;

    private static final BlockPos TOWN_STATION = new BlockPos(123, 64, 26);
    private static final BlockPos MINES_STATION = new BlockPos(-7, 66, -12);
    private static final BlockPos BUS_STATION = new BlockPos(-76, 64, -70);
    private static final BlockPos QUARRY_STATION = new BlockPos(187, 81, -141);

    private int placedVersion = 0;

    public MinecartStationManager() {}

    public void resetForMigration() {
        placedVersion = 0;
        setDirty();
    }

    public static MinecartStationManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return new MinecartStationManager();
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    /** 同时传入两个维度（星露谷 + 矿井）统一处理。 */
    public void ensurePlaced(MinecraftServer server) {
        if (placedVersion >= CURRENT_VERSION) return;

        ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
        ServerLevel mine = server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (sdv == null || mine == null) return; // 两个维度都要加载完才能放

        StardewCraft.LOGGER.info("[MINECART] Placing stations + rails (oldVersion={}, newVersion={})",
                placedVersion, CURRENT_VERSION);

        // 清除旧实体（版本迁移时防残留）
        removeAllStationsIn(sdv);
        removeAllStationsIn(mine);

        // 仅首次初始化或手动 reset 时铺轨；版本迁移不覆盖玩家之后调整过的站点布局。
        if (placedVersion == 0) {
            placeRails(mine, -16, 66, -12, -7);
        }

        // 四个站点实体
        spawnStation(sdv, TOWN_STATION, "town");
        spawnStation(mine, MINES_STATION, "mines");
        spawnStation(sdv, BUS_STATION, "bus");
        spawnStation(sdv, QUARRY_STATION, "quarry");

        placedVersion = CURRENT_VERSION;
        setDirty();
        StardewCraft.LOGGER.info("[MINECART] Station setup complete.");
    }

    private void spawnStation(ServerLevel level, BlockPos pos, String stationId) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        MinecartStationEntity e = new MinecartStationEntity(level, pos, stationId);
        e.setYRot(STATION_Y_ROT);
        level.addFreshEntity(e);
    }

    private void removeAllStationsIn(ServerLevel level) {
        // 扫一大块区域把现有 MinecartStationEntity 全部 discard — 简单粗暴但重放只发生在版本迁移。
        AABB box = new AABB(-2000, -100, -2000, 2000, 200, 2000);
        List<MinecartStationEntity> existing =
                level.getEntitiesOfClass(MinecartStationEntity.class, box, e -> true);
        for (MinecartStationEntity e : existing) {
            e.discard();
        }
    }

    /** 放一段东西向平铁轨（包含起止两端）。 */
    private void placeRails(ServerLevel level, int x1, int y, int z, int x2) {
        int lo = Math.min(x1, x2), hi = Math.max(x1, x2);
        var state = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
        int placed = 0;
        for (int x = lo; x <= hi; x++) {
            BlockPos pos = new BlockPos(x, y, z);
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            level.setBlock(pos, state, 2);
            placed++;
        }
        StardewCraft.LOGGER.info("[MINECART] Placed {} east-west rails along X=[{},{}] Y={} Z={}",
                placed, lo, hi, y, z);
    }

    // ── NBT ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        tag.putInt("PlacedVersion", placedVersion);
        return tag;
    }

    private static MinecartStationManager load(CompoundTag tag, HolderLookup.Provider provider) {
        MinecartStationManager m = new MinecartStationManager();
        m.placedVersion = tag.getInt("PlacedVersion");
        return m;
    }

    public static SavedData.Factory<MinecartStationManager> factory() {
        return new SavedData.Factory<>(MinecartStationManager::new, MinecartStationManager::load);
    }
}
