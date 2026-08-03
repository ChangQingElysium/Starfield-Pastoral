package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.mining.StructureLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * 为每位玩家分配独立的 Community Center 和 Greenhouse 室内坐标。
 * <p>
 * 每位玩家获得唯一的 playerIndex，CC/GH 结构分别以固定步幅沿 Z 轴偏移放置。
 * 结构在玩家首次进入时按需加载。
 */
public class PlayerInteriorAllocator extends SavedData {

    private static final String DATA_NAME = "stardew_player_interior_alloc";

    // CC: schem 尺寸 23x8x69，128 步幅留有余量
    static final int CC_Z_STRIDE = 128;
    // Greenhouse: 室内较小，64 步幅
    static final int GH_Z_STRIDE = 64;
    // Farm Cave: schem 9×6×10，32 步幅绰绰有余
    static final int CAVE_Z_STRIDE = 32;

    private final Map<UUID, Integer> playerIndices = new HashMap<>();
    private int nextIndex = 0;

    /** 已放置 CC 结构的玩家集合 */
    private final Set<UUID> ccPlaced = new HashSet<>();
    /** 已放置温室室内结构的玩家集合 */
    private final Set<UUID> ghPlaced = new HashSet<>();
    /** 已放置农场洞穴室内结构的玩家集合 */
    private final Set<UUID> cavePlaced = new HashSet<>();

    // ── 静态访问 ──

    public static PlayerInteriorAllocator get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new Factory<>(PlayerInteriorAllocator::new, PlayerInteriorAllocator::load),
            DATA_NAME
        );
    }

    // ── 坐标计算 ──

    /** 获取指定玩家的 CC 室内原点 */
    public BlockPos getCCOrigin(UUID playerUUID) {
        int index = getOrAllocateIndex(playerUUID);
        return InteriorSubspaceManager.CC_ORIGIN.offset(0, 0, index * CC_Z_STRIDE);
    }

    /** 获取指定玩家的温室室内原点 */
    public BlockPos getGreenhouseOrigin(UUID playerUUID) {
        int index = getOrAllocateIndex(playerUUID);
        return InteriorSubspaceManager.GREENHOUSE_INTERIOR_ORIGIN.offset(0, 0, index * GH_Z_STRIDE);
    }

    /** 获取指定玩家的农场洞穴室内原点 */
    public BlockPos getCaveOrigin(UUID playerUUID) {
        int index = getOrAllocateIndex(playerUUID);
        return InteriorSubspaceManager.FARM_CAVE_INTERIOR_ORIGIN.offset(0, 0, index * CAVE_Z_STRIDE);
    }

    private int getOrAllocateIndex(UUID playerUUID) {
        Integer idx = playerIndices.get(playerUUID);
        if (idx != null) return idx;

        idx = nextIndex++;
        playerIndices.put(playerUUID, idx);
        setDirty();
        StardewCraft.LOGGER.info("[INTERIOR-ALLOC] Allocated interior index {} for player {}", idx, playerUUID);
        return idx;
    }

    // ── 结构加载 ──

    /**
     * 确保指定玩家的 CC 结构已加载。首次调用时放置 schem + 生成出口交互实体 + 放置 JunimoNote。
     *
     * @return 该玩家的 CC 原点
     */
    public BlockPos ensureCCLoaded(ServerLevel level, UUID playerUUID) {
        BlockPos origin = getCCOrigin(playerUUID);
        if (!ccPlaced.contains(playerUUID)) {
            boolean ok = StructureLoader.loadAndPlaceWithResult(
                level, InteriorSubspaceManager.CC_RUINS_PATH, origin);
            if (ok) {
                ccPlaced.add(playerUUID);
                setDirty();
                // 放置 JunimoNote
                com.stardew.craft.communitycenter.JunimoNotePlacer.ensureJunimoNotes(level, playerUUID, origin);
                // 强制加载区块
                forceChunksForCC(level, origin, true);
                StardewCraft.LOGGER.info("[INTERIOR-ALLOC] CC structure loaded for player {} at {}", playerUUID, origin);
            } else {
                StardewCraft.LOGGER.error("[INTERIOR-ALLOC] Failed to load CC for player {} at {}", playerUUID, origin);
            }
        }
        // This is deliberately outside the first-placement branch: old saves may already have a
        // CC allocation whose exit was erased by the final refurbished structure restore.
        if (ccPlaced.contains(playerUUID)) {
            InteriorSubspaceManager.ensureCommunityCenterExitPortal(level, origin);
        }
        return origin;
    }

    /**
     * 确保指定玩家的温室室内结构已加载。
     *
     * @return 该玩家的温室原点
     */
    public BlockPos ensureGreenhouseLoaded(ServerLevel level, UUID playerUUID) {
        BlockPos origin = getGreenhouseOrigin(playerUUID);
        if (!ghPlaced.contains(playerUUID)) {
            boolean ok = StructureLoader.loadAndPlaceWithResult(
                level, InteriorSubspaceManager.GREENHOUSE_INTERIOR_PATH, origin);
            if (ok) {
                ghPlaced.add(playerUUID);
                setDirty();
                spawnGHExitPortals(level, origin);
                forceChunksForGH(level, origin, true);
                StardewCraft.LOGGER.info("[INTERIOR-ALLOC] Greenhouse interior loaded for player {} at {}", playerUUID, origin);
            } else {
                StardewCraft.LOGGER.error("[INTERIOR-ALLOC] Failed to load greenhouse for player {} at {}", playerUUID, origin);
            }
        }
        return origin;
    }

    /**
     * 确保指定玩家的农场洞穴室内结构已加载。
     *
     * @return 该玩家的洞穴 origin（= schem min corner）
     */
    public BlockPos ensureCaveLoaded(ServerLevel level, UUID playerUUID) {
        BlockPos origin = getCaveOrigin(playerUUID);
        if (!cavePlaced.contains(playerUUID)) {
            boolean ok = StructureLoader.loadAndPlaceWithResult(
                level, InteriorSubspaceManager.FARM_CAVE_PATH, origin);
            if (ok) {
                cavePlaced.add(playerUUID);
                setDirty();
                spawnCaveExitPortals(level, origin);
                forceChunksForCave(level, origin, true);
                StardewCraft.LOGGER.info("[INTERIOR-ALLOC] Farm cave interior loaded for player {} at {}", playerUUID, origin);
            } else {
                StardewCraft.LOGGER.error("[INTERIOR-ALLOC] Failed to load farm cave for player {} at {}", playerUUID, origin);
            }
        }
        return origin;
    }

    /**
     * 布局版本变更后重新加载所有已分配的 per-player 结构。
     * 由 {@link InteriorSubspaceManager#ensureLoaded} 在静态结构加载后调用。
     */
    public void reloadAllPlaced(ServerLevel level) {        var ccData = com.stardew.craft.communitycenter.state.CommunityCenterSavedData.get();
        for (UUID uuid : new ArrayList<>(ccPlaced)) {
            BlockPos origin = getCCOrigin(uuid);
            StructureLoader.loadAndPlaceWithResult(level, InteriorSubspaceManager.CC_RUINS_PATH, origin);
            // 重新应用该玩家已完成区域的 refurbished 方块
            for (int areaId = 0; areaId <= 5; areaId++) {
                if (ccData.isAreaComplete(uuid, areaId)) {
                    com.stardew.craft.communitycenter.restore.AreaRestoreHandler.restoreArea(level, areaId, origin);
                }
            }
            // 如果全部完成，覆盖放置整体 refurbished 版
            if (ccData.areAllAreasComplete(uuid)) {
                com.stardew.craft.communitycenter.restore.AreaRestoreHandler.restoreAllRemaining(level, origin);
            }
            InteriorSubspaceManager.ensureCommunityCenterExitPortal(level, origin);
            com.stardew.craft.communitycenter.JunimoNotePlacer.ensureJunimoNotes(level, uuid, origin);
            forceChunksForCC(level, origin, true);
        }
        for (UUID uuid : new ArrayList<>(ghPlaced)) {
            BlockPos origin = getGreenhouseOrigin(uuid);
            StructureLoader.loadAndPlaceWithResult(level, InteriorSubspaceManager.GREENHOUSE_INTERIOR_PATH, origin);
            spawnGHExitPortals(level, origin);
            forceChunksForGH(level, origin, true);
        }
        for (UUID uuid : new ArrayList<>(cavePlaced)) {
            BlockPos origin = getCaveOrigin(uuid);
            StructureLoader.loadAndPlaceWithResult(level, InteriorSubspaceManager.FARM_CAVE_PATH, origin);
            spawnCaveExitPortals(level, origin);
            forceChunksForCave(level, origin, true);
        }
        StardewCraft.LOGGER.info("[INTERIOR-ALLOC] Reloaded {} CC + {} GH per-player structures",
            ccPlaced.size(), ghPlaced.size());
    }

    // ── 坐标查询 ──

    /** 判断世界坐标是否在任意玩家的 CC 室内区域内 */
    public boolean isInsideAnyCC(BlockPos worldPos) {
        return findCCOwner(worldPos) != null;
    }

    /** 查找世界坐标所属的 CC 玩家 UUID，不在任何 CC 内返回 null */
    @Nullable
    public UUID findCCOwner(BlockPos worldPos) {
        BlockPos ccBase = InteriorSubspaceManager.CC_ORIGIN;
        int rx = worldPos.getX() - ccBase.getX();
        int ry = worldPos.getY() - ccBase.getY();
        int rz = worldPos.getZ() - ccBase.getZ();
        // X/Y 范围检查 (CC schem: 23x8)
        if (rx < 0 || rx > 22 || ry < 0 || ry > 7 || rz < 0) return null;
        // 由 Z 偏移反推 playerIndex
        int candidateIndex = rz / CC_Z_STRIDE;
        int localZ = rz - candidateIndex * CC_Z_STRIDE;
        if (localZ < 0 || localZ > 68) return null;
        // 反查 UUID
        for (var entry : playerIndices.entrySet()) {
            if (entry.getValue() == candidateIndex && ccPlaced.contains(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static final int GH_SLOT_X = 64;
    private static final int GH_SLOT_Y = 64;

    /** 判断世界坐标是否在任意玩家的温室室内区域内 */
    public boolean isInsideAnyGreenhouse(BlockPos worldPos) {
        return findGreenhouseOwner(worldPos) != null;
    }

    /** 查找世界坐标所属的温室玩家 UUID */
    @Nullable
    public UUID findGreenhouseOwner(BlockPos worldPos) {
        BlockPos ghBase = InteriorSubspaceManager.GREENHOUSE_INTERIOR_ORIGIN;
        int rx = worldPos.getX() - ghBase.getX();
        int ry = worldPos.getY() - ghBase.getY();
        int rz = worldPos.getZ() - ghBase.getZ();
        if (rx < 0 || rx >= GH_SLOT_X || ry < 0 || ry >= GH_SLOT_Y || rz < 0) return null;
        int candidateIndex = rz / GH_Z_STRIDE;
        int localZ = rz - candidateIndex * GH_Z_STRIDE;
        if (localZ < 0 || localZ >= GH_Z_STRIDE) return null;
        for (var entry : playerIndices.entrySet()) {
            if (entry.getValue() == candidateIndex && ghPlaced.contains(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 根据世界坐标反推出该格子所在的温室槽位 origin。
     * 仅做几何换算，不要求该槽位已经绑定到某个玩家。
     */
    @Nullable
    public BlockPos findGreenhouseOrigin(BlockPos worldPos) {
        BlockPos ghBase = InteriorSubspaceManager.GREENHOUSE_INTERIOR_ORIGIN;
        int rx = worldPos.getX() - ghBase.getX();
        int ry = worldPos.getY() - ghBase.getY();
        int rz = worldPos.getZ() - ghBase.getZ();
        if (rx < 0 || rx >= GH_SLOT_X || ry < 0 || ry >= GH_SLOT_Y || rz < 0) {
            return null;
        }
        int candidateIndex = rz / GH_Z_STRIDE;
        int localZ = rz - candidateIndex * GH_Z_STRIDE;
        if (localZ < 0 || localZ >= GH_Z_STRIDE) {
            return null;
        }
        return ghBase.offset(0, 0, candidateIndex * GH_Z_STRIDE);
    }

    /** CC 是否已为指定玩家放置 */
    public boolean isCCPlaced(UUID playerUUID) {
        return ccPlaced.contains(playerUUID);
    }

    /** 温室是否已为指定玩家放置 */
    public boolean isGHPlaced(UUID playerUUID) {
        return ghPlaced.contains(playerUUID);
    }

    /** 洞穴是否已为指定玩家放置 */
    public boolean isCavePlaced(UUID playerUUID) {
        return cavePlaced.contains(playerUUID);
    }

    public Set<UUID> getPlayersWithCC() {
        return Collections.unmodifiableSet(ccPlaced);
    }

    public Set<UUID> getPlayersWithGreenhouse() {
        return Collections.unmodifiableSet(ghPlaced);
    }

    public Set<UUID> getPlayersWithCave() {
        return Collections.unmodifiableSet(cavePlaced);
    }

    public void setAllGreenhouseChunksForced(ServerLevel level, boolean force) {
        for (UUID uuid : ghPlaced) {
            forceChunksForGH(level, getGreenhouseOrigin(uuid), force);
        }
    }

    /** 查找世界坐标所属的洞穴玩家 UUID，不在任何洞穴内返回 null */
    @Nullable
    public UUID findCaveOwner(BlockPos worldPos) {
        BlockPos base = InteriorSubspaceManager.FARM_CAVE_INTERIOR_ORIGIN;
        int rx = worldPos.getX() - base.getX();
        int ry = worldPos.getY() - base.getY();
        int rz = worldPos.getZ() - base.getZ();
        if (rx < 0 || rx >= InteriorSubspaceManager.FARM_CAVE_SCHEM_W
                || ry < 0 || ry >= InteriorSubspaceManager.FARM_CAVE_SCHEM_H
                || rz < 0) return null;
        int candidateIndex = rz / CAVE_Z_STRIDE;
        int localZ = rz - candidateIndex * CAVE_Z_STRIDE;
        if (localZ < 0 || localZ >= InteriorSubspaceManager.FARM_CAVE_SCHEM_L) return null;
        for (var entry : playerIndices.entrySet()) {
            if (entry.getValue() == candidateIndex && cavePlaced.contains(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ── 交互实体生成 ──

    private void spawnGHExitPortals(ServerLevel level, BlockPos origin) {
        BlockPos exitPortal = origin.offset(InteriorSubspaceManager.GREENHOUSE_INDOOR_EXIT_PORTAL_OFFSET);
        InteriorSubspaceManager.spawnInteractionArea(
            level, exitPortal, 2, 1, 1,
            "sdv_portal_marker:greenhouse_inside",
            "sdv_portal_target:greenhouse_exit"
        );
    }

    private void spawnCaveExitPortals(ServerLevel level, BlockPos origin) {
        BlockPos exitPortal = origin.offset(InteriorSubspaceManager.FARM_CAVE_INDOOR_EXIT_PORTAL_OFFSET);
        // 1×1×2：1 格长 × 1 格宽 × 2 格高（参数为 height, x, z）
        InteriorSubspaceManager.spawnInteractionArea(
            level, exitPortal, 2, 1, 1,
            InteriorSubspaceManager.TAG_PORTAL_MARKER_FARM_CAVE_INSIDE,
            "sdv_portal_target:farm_cave_exit"
        );
    }

    // ── 区块强制加载 ──

    // CC schem 尺寸 23x8x69 → 需要 min(0,0)~max(22,68) 的 chunk 范围
    private static final int CC_SCHEM_X = 23;
    private static final int CC_SCHEM_Z = 69;
    // GH 内部 schem 尺寸约 7x5x12
    private static final int GH_SCHEM_X = 7;
    private static final int GH_SCHEM_Z = 12;

    private void forceChunksForCC(ServerLevel level, BlockPos origin, boolean force) {
        forceChunksForRegion(level, origin, CC_SCHEM_X, CC_SCHEM_Z, force);
    }

    private void forceChunksForGH(ServerLevel level, BlockPos origin, boolean force) {
        forceChunksForRegion(level, origin, GH_SCHEM_X, GH_SCHEM_Z, force);
    }

    private void forceChunksForCave(ServerLevel level, BlockPos origin, boolean force) {
        forceChunksForRegion(level, origin,
            InteriorSubspaceManager.FARM_CAVE_SCHEM_W,
            InteriorSubspaceManager.FARM_CAVE_SCHEM_L,
            force);
    }

    private void forceChunksForRegion(ServerLevel level, BlockPos origin, int sizeX, int sizeZ, boolean force) {
        int minCX = origin.getX() >> 4;
        int maxCX = (origin.getX() + sizeX - 1) >> 4;
        int minCZ = origin.getZ() >> 4;
        int maxCZ = (origin.getZ() + sizeZ - 1) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                level.setChunkForced(cx, cz, force);
                if (force) level.getChunk(cx, cz);
            }
        }
    }

    // ── 持久化 ──

    public static PlayerInteriorAllocator load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerInteriorAllocator alloc = new PlayerInteriorAllocator();
        alloc.nextIndex = tag.getInt("nextIndex");

        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            int index = entry.getInt("index");
            alloc.playerIndices.put(uuid, index);
            if (entry.getBoolean("ccPlaced")) alloc.ccPlaced.add(uuid);
            if (entry.getBoolean("ghPlaced")) alloc.ghPlaced.add(uuid);
            if (entry.getBoolean("cavePlaced")) alloc.cavePlaced.add(uuid);
        }
        return alloc;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt("nextIndex", nextIndex);

        ListTag players = new ListTag();
        for (var entry : playerIndices.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("uuid", entry.getKey());
            entryTag.putInt("index", entry.getValue());
            entryTag.putBoolean("ccPlaced", ccPlaced.contains(entry.getKey()));
            entryTag.putBoolean("ghPlaced", ghPlaced.contains(entry.getKey()));
            entryTag.putBoolean("cavePlaced", cavePlaced.contains(entry.getKey()));
            players.add(entryTag);
        }
        tag.put("players", players);
        return tag;
    }
}
