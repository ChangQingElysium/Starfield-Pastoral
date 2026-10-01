package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主世界巫师塔结构后处理：
 * 1. 玩家首次接近时，一次性将结构周围暴露泥土转为草方块
 * 2. 在入口放置交互实体（传送门触发器）
 *
 * 所有操作在 PlayerTickEvent 中完成（而非 ChunkLoad），
 * 确保所有相关区块都已加载，避免级联加载导致卡死。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class WizardTowerStructureHandler {

    private static final ResourceKey<Structure> WIZARD_TOWER_STRUCTURE =
        ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation(StardewCraft.MODID, "wizard_tower_overworld"));

    private static final int PORTAL_HEIGHT = 2;

    private static final String MARKER_TAG = "sdv_portal_marker:wizard_tower_overworld";
    private static final String TARGET_TAG = "sdv_portal_target:wizard_tower_overworld_enter";

    /** 已完成地形融合的结构（按包围盒中心 key），避免重复处理 */
    private static final Set<Long> processedTerrainKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** 已放置传送方块的结构 key */
    private static final Set<Long> portalPlacedKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 每 100 tick（~5 秒）检测一次
    private static final int CHECK_INTERVAL = 100;

    private WizardTowerStructureHandler() {}

    /**
     * 每次 server 启动清空 in-memory 缓存，确保即使 JVM 未重启（例如单人游戏切换存档），
     * 法师塔传送方块也会在玩家靠近时重新校验并补放，避免残留 key 遮蔽实际丢失的方块。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        processedTerrainKeys.clear();
        portalPlacedKeys.clear();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (!Level.OVERWORLD.equals(player.level().dimension())) return;

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        StructureStart start = findNearbyWizardTower(level, playerPos);
        if (start == null || !start.isValid()) return;

        BoundingBox bb = start.getBoundingBox();

        // ── 地形融合（仅首次） ──
        long terrainKey = packBBKey(bb);
        if (processedTerrainKeys.add(terrainKey)) {
            convertExposedDirtToGrass(level, bb);
        }

        // ── 传送方块放置（仅一次） ──
        long portalKey = packBBKey(bb);
        if (portalPlacedKeys.contains(portalKey)) return;

        // 检查是否已有传送方块（老存档恢复后）
        if (hasPortalBlockInBounds(level, bb)) {
            portalPlacedKeys.add(portalKey);
            return;
        }

        BlockPos doorPos = findLowestDarkOakDoor(level, bb);
        if (doorPos == null) {
            StardewCraft.LOGGER.warn("[WIZARD_STRUCT] No dark oak door found in structure at {}", bb.getCenter());
            return;
        }

        InteriorSubspaceManager.placePortalTriggerArea(
            level, doorPos, PORTAL_HEIGHT, 1, 1,
            MARKER_TAG, TARGET_TAG
        );
        portalPlacedKeys.add(portalKey);
        StardewCraft.LOGGER.info("[WIZARD_STRUCT] Placed portal trigger blocks at door pos {}", doorPos);
    }

    // ====================== 地形融合 ======================

    /**
     * 将结构区域（外扩 3 格）内暴露泥土转为草方块。
     * 仅处理已加载区块内的方块，跳过未加载区域（不触发加载）。
     */
    private static void convertExposedDirtToGrass(ServerLevel level, BoundingBox bb) {
        int converted = 0;
        for (int x = bb.minX() - 3; x <= bb.maxX() + 3; x++) {
            for (int z = bb.minZ() - 3; z <= bb.maxZ() + 3; z++) {
                if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                for (int y = bb.maxY(); y >= bb.minY() - 2; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.DIRT)) {
                        BlockState above = level.getBlockState(pos.above());
                        if (above.isAir() || !above.canOcclude()) {
                            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                            converted++;
                        }
                    }
                }
            }
        }
        if (converted > 0) {
            StardewCraft.LOGGER.info("[WIZARD_STRUCT] Converted {} exposed dirt → grass around structure", converted);
        }
    }

    // ====================== 工具方法 ======================

    private static boolean hasPortalBlockInBounds(ServerLevel level, BoundingBox bb) {
        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                for (int y = bb.minY(); y <= bb.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos findLowestDarkOakDoor(ServerLevel level, BoundingBox bb) {
        BlockPos lowestDoor = null;
        int lowestY = Integer.MAX_VALUE;

        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                for (int y = bb.minY(); y <= bb.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.DARK_OAK_DOOR)
                        && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && y < lowestY) {
                        lowestY = y;
                        lowestDoor = pos;
                    }
                }
            }
        }
        return lowestDoor;
    }

    private static StructureStart findNearbyWizardTower(ServerLevel level, BlockPos playerPos) {
        ChunkPos center = new ChunkPos(playerPos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                ChunkAccess chunk = level.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;
                Map<Structure, StructureStart> starts = chunk.getAllStarts();
                for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                    StructureStart ss = entry.getValue();
                    if (!ss.isValid()) continue;
                    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                    var key = registry.getKey(entry.getKey());
                    if (key != null && key.equals(WIZARD_TOWER_STRUCTURE.location())) {
                        return ss;
                    }
                }
            }
        }
        return null;
    }

    /** 将包围盒中心编码为 long key */
    private static long packBBKey(BoundingBox bb) {
        int cx = (bb.minX() + bb.maxX()) / 2;
        int cz = (bb.minZ() + bb.maxZ()) / 2;
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
