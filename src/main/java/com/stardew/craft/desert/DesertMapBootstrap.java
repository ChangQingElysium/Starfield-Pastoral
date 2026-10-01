package com.stardew.craft.desert;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.mining.StructureLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.stardew.craft.desert.DesertConstants.*;

/**
 * 沙漠 schem 的放置引导。
 * <p>
 * 采用与 InteriorSubspaceManager 类似的分批 chunk 加载 + 放置策略，
 * 避免阻塞服务端 watchdog。
 * <ul>
 *   <li>Phase 1: force-load 沙漠区域所有 chunk</li>
 *   <li>Phase 2: 等待 chunk 全部就绪</li>
 *   <li>Phase 3: 使用 {@link StructureLoader} 放置 schem</li>
 *   <li>Phase 4: 放置传送方块，逐步释放 chunk</li>
 * </ul>
 */
public final class DesertMapBootstrap {

    private DesertMapBootstrap() {}

    // 沙漠已经内嵌在 pregen 主地图里，版本跟 pregen 地图版本统一。
    private static final int DESERT_VERSION = StardewValleyPrebuiltRegionInstaller.CURRENT_PREGEN_VERSION;
    private static final int MAX_CHUNK_WAIT_TICKS = 200;

    // ── 分批状态 ──
    private static boolean placementInProgress;
    private static int phase; // 1=force-load, 2=wait, 3=place, 4=portals+release
    private static int waitTicks;
    private static final Set<ChunkPos> forcedChunks = new HashSet<>();
    private static final List<ChunkPos> chunksToRelease = new ArrayList<>();
    private static boolean gradualReleaseInProgress;
    private static int releaseTickCounter;
    private static final int RELEASE_DELAY_TICKS = 50;
    private static final int RELEASE_TICK_INTERVAL = 3;
    private static final int CHUNKS_RELEASE_PER_BATCH = 4;

    /**
     * 在 ServerStartedEvent 时调用。
     */
    public static void ensureLoaded(ServerLevel level, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;

        DesertSavedData data = DesertSavedData.get(level);
        if (data.version == DESERT_VERSION && data.placed) {
            placeDesertPortals(level);
            return;
        }
        if (placementInProgress) return;

        placeDesertPortals(level);
        data.version = DESERT_VERSION;
        data.placed = true;
        data.setDirty();

        StardewCraft.LOGGER.info("[DESERT] Embedded pregen desert initialized. reason={}, version={}", reason, DESERT_VERSION);
    }

    /**
     * 每 tick 调用，驱动放置流程。
     */
    public static void tick(ServerLevel level) {
        // ── 逐步释放 chunk ──
        if (gradualReleaseInProgress) {
            releaseTickCounter++;
            if (releaseTickCounter <= RELEASE_DELAY_TICKS) return;
            if ((releaseTickCounter - RELEASE_DELAY_TICKS) % RELEASE_TICK_INTERVAL != 0) return;
            int toRelease = Math.min(CHUNKS_RELEASE_PER_BATCH, chunksToRelease.size());
            for (int i = 0; i < toRelease; i++) {
                ChunkPos cp = chunksToRelease.remove(chunksToRelease.size() - 1);
                level.setChunkForced(cp.x, cp.z, false);
            }
            if (chunksToRelease.isEmpty()) {
                gradualReleaseInProgress = false;
                releaseTickCounter = 0;
                StardewCraft.LOGGER.info("[DESERT] Finished gradual chunk release");
            }
            return;
        }

        if (!placementInProgress) return;

        switch (phase) {
            case 1 -> phaseForceLoad(level);
            case 2 -> phaseWaitChunks(level);
            case 3 -> phasePlace(level);
            case 4 -> phasePortalsAndFinish(level);
        }
    }

    // ── Phase 1: force-load 沙漠区域 chunk ──
    private static void phaseForceLoad(ServerLevel level) {
        BlockPos origin = DESERT_ORIGIN;
        // 计算 schem 包围盒的 chunk 范围
        // schem 大小约 298×82×324
        int minCx = origin.getX() >> 4;
        int maxCx = (origin.getX() + 297) >> 4;
        int minCz = origin.getZ() >> 4;
        int maxCz = (origin.getZ() + 323) >> 4;

        for (int cx = minCx - 1; cx <= maxCx + 1; cx++) {
            for (int cz = minCz - 1; cz <= maxCz + 1; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                forcedChunks.add(cp);
                level.setChunkForced(cp.x, cp.z, true);
            }
        }

        StardewCraft.LOGGER.info("[DESERT] Force-loading {} chunks for desert schem", forcedChunks.size());
        phase = 2;
        waitTicks = 0;
    }

    // ── Phase 2: 等待 chunk 加载完成 ──
    private static void phaseWaitChunks(ServerLevel level) {
        waitTicks++;
        for (ChunkPos cp : forcedChunks) {
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                if (waitTicks >= MAX_CHUNK_WAIT_TICKS) {
                    StardewCraft.LOGGER.error("[DESERT] Chunk preload timeout after {} ticks, aborting", waitTicks);
                    abort(level);
                }
                return;
            }
        }
        phase = 3;
    }

    // ── Phase 3: 放置 schem ──
    private static void phasePlace(ServerLevel level) {
        StardewCraft.LOGGER.info("[DESERT] Placing desert.schem at {}", DESERT_ORIGIN);
        boolean success = StructureLoader.loadAndPlaceWithResult(level, DESERT_SCHEM_PATH, DESERT_ORIGIN);
        if (!success) {
            StardewCraft.LOGGER.error("[DESERT] Failed to place desert.schem!");
            abort(level);
            return;
        }
        StardewCraft.LOGGER.info("[DESERT] Desert schem placed successfully");
        phase = 4;
    }

    // ── Phase 4: 放置门户方块 + 标记完成 + 释放 chunk ──
    private static void phasePortalsAndFinish(ServerLevel level) {
        placeDesertPortals(level);

        DesertSavedData data = DesertSavedData.get(level);
        data.version = DESERT_VERSION;
        data.placed = true;
        data.setDirty();

        placementInProgress = false;
        phase = 0;

        StardewCraft.LOGGER.info("[DESERT] Desert placement complete, version={}", DESERT_VERSION);

        // 启动逐步释放
        if (!forcedChunks.isEmpty()) {
            chunksToRelease.addAll(forcedChunks);
            forcedChunks.clear();
            gradualReleaseInProgress = true;
            releaseTickCounter = 0;
            StardewCraft.LOGGER.info("[DESERT] Starting gradual chunk release: {} chunks", chunksToRelease.size());
        }
    }

    /**
     * 放置沙漠区域的传送门户方块。
     */
    public static void placeDesertPortals(ServerLevel level) {
        BlockPos minePortalWorld = worldPos(MINE_PORTAL_OFFSET);
        BlockPos oasisPortalWorld = worldPos(OASIS_PORTAL_OFFSET);

        // 矿井入口（占位，提示文字）
        InteriorSubspaceManager.placePortalTriggerArea(level,
                minePortalWorld,
                MINE_PORTAL_H, MINE_PORTAL_X, MINE_PORTAL_Z,
                TAG_MINE_PORTAL_MARKER, TAG_MINE_PORTAL_TARGET);

        // Oasis 室外入口
        InteriorSubspaceManager.placePortalTriggerArea(level,
                oasisPortalWorld,
                OASIS_PORTAL_H, OASIS_PORTAL_X, OASIS_PORTAL_Z,
                TAG_OASIS_PORTAL_MARKER_OUTSIDE, TAG_OASIS_PORTAL_TARGET_ENTER);

        // Oasis 室内出口
        BlockPos oasisIndoorExitPortal = OASIS_INTERIOR_ORIGIN.offset(OASIS_INDOOR_EXIT_PORTAL_OFFSET);
        InteriorSubspaceManager.placePortalTriggerArea(level,
                oasisIndoorExitPortal,
                OASIS_EXIT_PORTAL_H, OASIS_EXIT_PORTAL_X, OASIS_EXIT_PORTAL_Z,
                TAG_OASIS_PORTAL_MARKER_INSIDE, TAG_OASIS_PORTAL_TARGET_EXIT);

        StardewCraft.LOGGER.info("[DESERT] Portal triggers placed: mine_entrance={}, oasis_outside={}, oasis_inside={}",
                minePortalWorld, oasisPortalWorld, oasisIndoorExitPortal);
    }

    /**
     * 强制重建沙漠（/stardew 命令可调用）。
     */
    public static void forceReload(ServerLevel level, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;
        DesertSavedData data = DesertSavedData.get(level);
        data.placed = false;
        data.version = 0;
        data.setDirty();
        ensureLoaded(level, reason);
    }

    public static boolean isPlacementInProgress() {
        return placementInProgress || gradualReleaseInProgress;
    }

    private static void abort(ServerLevel level) {
        placementInProgress = false;
        phase = 0;
        if (!forcedChunks.isEmpty()) {
            chunksToRelease.addAll(forcedChunks);
            forcedChunks.clear();
            gradualReleaseInProgress = true;
            releaseTickCounter = 0;
        }
    }

    // ────────────────── SavedData ──────────────────

    static final class DesertSavedData extends SavedData {
        private static final String DATA_NAME = "stardew_desert_map_state";
        int version;
        boolean placed;

        static DesertSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                    com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(DesertSavedData::new, DesertSavedData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(DesertSavedData::new, DesertSavedData::load)),
                    DATA_NAME
            );
        }

        static DesertSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            DesertSavedData data = new DesertSavedData();
            data.version = tag.getInt("version");
            data.placed = tag.getBoolean("placed");
            return data;
        }

        @Override
        public @Nonnull CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
            tag.putInt("version", version);
            tag.putBoolean("placed", placed);
            return tag;
        }
    }
}
