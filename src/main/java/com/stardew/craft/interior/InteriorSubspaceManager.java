package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller;
import com.stardew.craft.mining.StructureLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 室内亚空间管理器：
 * - 在星露谷维度内划定固定远坐标区域
 * - 维护固定结构清单（路径 + 固定坐标）
 * - 按版本执行一次性装载（可升级）
 */
@SuppressWarnings("null")
public final class InteriorSubspaceManager {

    private InteriorSubspaceManager() {
    }

    // ── Portal 自修复注册表 ──
    public record PortalPlacement(ResourceKey<Level> dimension, BlockPos basePos,
                                   int heightBlocks, int xBlocks, int zBlocks,
                                   String markerTag, String targetTag, boolean solidOnly) {}

    private static final Map<Long, PortalPlacement> PORTAL_REGISTRY = new ConcurrentHashMap<>();
    private static final double REPAIR_CHECK_RANGE = 12.0;

    private static long portalKey(ResourceKey<Level> dim, BlockPos pos) {
        return ((long) dim.location().hashCode() << 32) | ((long) pos.hashCode() & 0xFFFFFFFFL);
    }

    /**
     * 验证并修复玩家附近的传送触发方块。
     * 如果注册表中的 portal 位置方块缺失，自动重新放置。
     */
    public static void verifyAndRepairNearby(ServerLevel level, ServerPlayer player) {
        ResourceKey<Level> dim = level.dimension();
        BlockPos playerPos = player.blockPosition();
        registerCurrentFarmExitPortals(player);
        for (PortalPlacement p : PORTAL_REGISTRY.values()) {
            if (!p.dimension.equals(dim)) continue;
            if (!isNearPortalArea(playerPos, p, REPAIR_CHECK_RANGE)) continue;
            if (!isPortalAreaValid(level, p)) {
                StardewCraft.LOGGER.warn("[PORTAL-REPAIR] Missing or invalid portal trigger '{}' at {} — re-placing",
                        p.markerTag, p.basePos);
                placePortalTriggerAreaInternal(level, p.basePos, p.heightBlocks, p.xBlocks, p.zBlocks,
                        p.markerTag, p.targetTag, p.solidOnly);
            }
        }
    }

    /**
     * 玩家农场出口不属于固定公共地图入口，服务器重启后需要在玩家回到农场时重新加入自修复注册表。
     * 只登记当前玩家所在的农场，避免为所有离线农场强制加载区块。
     */
    private static void registerCurrentFarmExitPortals(ServerPlayer player) {
        com.stardew.craft.farm.FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get()
                .getFarmForPlayer(player.getUUID());
        if (farm == null || !farm.isInitialized() || !farm.contains(player.blockPosition())) {
            return;
        }
        com.stardew.craft.farm.FarmType.FarmLayout layout = farm.getFarmType().getLayout();
        if (layout == null) {
            return;
        }

        registerExitRegion(player.serverLevel(), farm.getOrigin(), layout.entrySouth(),
                "sdv_portal_target:farm_exit_south", "sdv_portal_marker:farm_exit");
        registerExitRegion(player.serverLevel(), farm.getOrigin(), layout.entryEast(),
                "sdv_portal_target:farm_exit_east", "sdv_portal_marker:farm_exit");
        registerExitRegion(player.serverLevel(), farm.getOrigin(), layout.entryWest(),
                "sdv_portal_target:farm_exit_west", "sdv_portal_marker:farm_exit");
    }

    private static void registerExitRegion(ServerLevel level, BlockPos origin,
                                           com.stardew.craft.farm.FarmType.EntryData entry,
                                           String targetTag, String markerTag) {
        BlockPos min = origin.offset(entry.exitMin());
        BlockPos max = origin.offset(entry.exitMax());
        BlockPos base = new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()));
        PortalPlacement placement = new PortalPlacement(
                level.dimension(),
                base,
                Math.abs(max.getY() - min.getY()) + 1,
                Math.abs(max.getX() - min.getX()) + 1,
                Math.abs(max.getZ() - min.getZ()) + 1,
                markerTag,
                targetTag,
                false);
        PORTAL_REGISTRY.putIfAbsent(portalKey(level.dimension(), base), placement);
    }

    private static boolean isNearPortalArea(BlockPos playerPos, PortalPlacement placement, double range) {
        int maxX = placement.basePos.getX() + placement.xBlocks - 1;
        int maxY = placement.basePos.getY() + placement.heightBlocks - 1;
        int maxZ = placement.basePos.getZ() + placement.zBlocks - 1;
        int closestX = Math.max(placement.basePos.getX(), Math.min(playerPos.getX(), maxX));
        int closestY = Math.max(placement.basePos.getY(), Math.min(playerPos.getY(), maxY));
        int closestZ = Math.max(placement.basePos.getZ(), Math.min(playerPos.getZ(), maxZ));
        return playerPos.closerThan(new BlockPos(closestX, closestY, closestZ), range);
    }

    private static boolean isPortalAreaValid(ServerLevel level, PortalPlacement placement) {
        String expectedTargetId = placement.targetTag;
        if (expectedTargetId.startsWith("sdv_portal_target:")) {
            expectedTargetId = expectedTargetId.substring("sdv_portal_target:".length());
        }

        boolean foundPortal = false;
        for (int dx = 0; dx < placement.xBlocks; dx++) {
            for (int dz = 0; dz < placement.zBlocks; dz++) {
                for (int dy = 0; dy < placement.heightBlocks; dy++) {
                    BlockPos pos = placement.basePos.offset(dx, dy, dz);
                    boolean isPortal = level.getBlockState(pos)
                            .is(com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get());
                    if (placement.solidOnly && !isPortal) {
                        continue;
                    }
                    if (!isPortal) {
                        return false;
                    }
                    foundPortal = true;
                    if (!(level.getBlockEntity(pos) instanceof
                            com.stardew.craft.blockentity.PortalTriggerBlockEntity be)
                            || !expectedTargetId.equals(be.getTargetId())) {
                        return false;
                    }
                }
            }
        }
        return foundPortal;
    }

    /**
     * 清除注册表（服务器关闭时调用）。
     */
    public static void clearPortalRegistry() {
        PORTAL_REGISTRY.clear();
    }

    // 用户要求：室内亚空间区域必须在第一象限远坐标，且 X>10000、Z>10000。
    public static final int REGION_MIN_X = 10001;
    public static final int REGION_MIN_Z = 10001;
    public static final int REGION_MAX_X = 19000;
    public static final int REGION_MAX_Z = 19000;

    // 固定室内已经内嵌在 pregen 主地图下方，布局版本跟 pregen 地图版本统一。
    private static final int LAYOUT_VERSION = StardewValleyPrebuiltRegionInstaller.CURRENT_PREGEN_VERSION;
    private static final int PORTAL_TRIGGER_VERSION = 4;
    private static final boolean FIXED_INTERIORS_EMBEDDED_IN_PREGEN = true;

    private static final String PIERRE_HOUSE_STRUCTURE_PATH = "data/stardewcraft/structures/interior/pierre_house.schem";
    private static final BlockPos PIERRE_HOUSE_ORIGIN = BlockPos.ZERO;
    private static final BlockPos PIERRE_INDOOR_SPAWN_OFFSET = new BlockPos(21, 36, -12);
    private static final BlockPos PIERRE_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(20, 36, -11);
    private static final BlockPos PIERRE_OUTDOOR_ENTRY_POS = new BlockPos(27, 64, -6);
    private static final BlockPos PIERRE_OUTDOOR_INTERACTION_BASE = new BlockPos(27, 65, -8);

    private static final String MUSEUM_STRUCTURE_PATH = "data/stardewcraft/structures/interior/museum.schem";
    private static final BlockPos MUSEUM_ORIGIN = BlockPos.ZERO;
    private static final BlockPos MUSEUM_INDOOR_SPAWN_OFFSET = new BlockPos(112, 38, 46);
    private static final BlockPos MUSEUM_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(111, 38, 47);
    private static final BlockPos MUSEUM_OUTDOOR_ENTRY_POS = new BlockPos(124, 64, 42);
    private static final BlockPos MUSEUM_OUTDOOR_EXIT_POS = new BlockPos(124, 64, 43);

    private static final String BLACKSMITH_STRUCTURE_PATH = "data/stardewcraft/structures/interior/blacksmith.schem";
    private static final BlockPos BLACKSMITH_ORIGIN = BlockPos.ZERO;
    private static final BlockPos BLACKSMITH_INDOOR_SPAWN_OFFSET = new BlockPos(107, 46, 30);
    private static final BlockPos BLACKSMITH_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(106, 46, 31);
    private static final BlockPos BLACKSMITH_OUTDOOR_ENTRY_POS = new BlockPos(108, 64, 29);
    private static final BlockPos BLACKSMITH_OUTDOOR_EXIT_POS = new BlockPos(108, 64, 30);

    private static final String TAG_PORTAL_MARKER_OUTSIDE = "sdv_portal_marker:pierre_house_outside";
    private static final String TAG_PORTAL_MARKER_INSIDE = "sdv_portal_marker:pierre_house_inside";
    private static final String TAG_PORTAL_MARKER_MUSEUM_OUTSIDE = "sdv_portal_marker:museum_outside";
    private static final String TAG_PORTAL_MARKER_MUSEUM_INSIDE = "sdv_portal_marker:museum_inside";
    private static final String TAG_PORTAL_MARKER_BLACKSMITH_OUTSIDE = "sdv_portal_marker:blacksmith_outside";
    private static final String TAG_PORTAL_MARKER_BLACKSMITH_INSIDE = "sdv_portal_marker:blacksmith_inside";

    private static final String SALOON_STRUCTURE_PATH = "data/stardewcraft/structures/interior/saloon.schem";
    private static final BlockPos SALOON_ORIGIN = BlockPos.ZERO;
    private static final BlockPos SALOON_INDOOR_SPAWN_OFFSET = new BlockPos(26, 36, 18);
    private static final BlockPos SALOON_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(25, 36, 19);
    private static final BlockPos SALOON_OUTDOOR_ENTRY_POS = new BlockPos(29, 66, 14);
    private static final BlockPos SALOON_OUTDOOR_EXIT_POS = new BlockPos(30, 64, 16);

    private static final String TAG_PORTAL_MARKER_SALOON_OUTSIDE = "sdv_portal_marker:saloon_outside";
    private static final String TAG_PORTAL_MARKER_SALOON_INSIDE = "sdv_portal_marker:saloon_inside";

    private static final String MAYOR_HOUSE_STRUCTURE_PATH = "data/stardewcraft/structures/interior/mayor_house.schem";
    private static final BlockPos MAYOR_HOUSE_ORIGIN = BlockPos.ZERO;
    private static final BlockPos MAYOR_HOUSE_INDOOR_SPAWN_OFFSET = new BlockPos(54, 50, 29);
    private static final BlockPos MAYOR_HOUSE_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(53, 50, 30);
    private static final BlockPos MAYOR_HOUSE_OUTDOOR_ENTRY_POS = new BlockPos(50, 66, 34);
    private static final BlockPos MAYOR_HOUSE_OUTDOOR_EXIT_POS = new BlockPos(50, 64, 37);

    private static final String TAG_PORTAL_MARKER_MAYOR_HOUSE_OUTSIDE = "sdv_portal_marker:mayor_house_outside";
    private static final String TAG_PORTAL_MARKER_MAYOR_HOUSE_INSIDE = "sdv_portal_marker:mayor_house_inside";

    private static final String CLINIC_STRUCTURE_PATH = "data/stardewcraft/structures/interior/clinic.schem";
    private static final BlockPos CLINIC_ORIGIN = BlockPos.ZERO;
    private static final BlockPos CLINIC_INDOOR_SPAWN_OFFSET = new BlockPos(9, 43, -11);
    private static final BlockPos CLINIC_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(8, 43, -10);
    private static final BlockPos CLINIC_OUTDOOR_ENTRY_POS = new BlockPos(13, 65, -8);
    private static final BlockPos CLINIC_OUTDOOR_EXIT_POS = new BlockPos(13, 64, -6);

    private static final String TAG_PORTAL_MARKER_CLINIC_OUTSIDE = "sdv_portal_marker:clinic_outside";
    private static final String TAG_PORTAL_MARKER_CLINIC_INSIDE = "sdv_portal_marker:clinic_inside";

    private static final String RIVER_ROAD_1_STRUCTURE_PATH = "data/stardewcraft/structures/interior/1_river_road.schem";
    private static final BlockPos RIVER_ROAD_1_ORIGIN = BlockPos.ZERO;
    private static final BlockPos RIVER_ROAD_1_INDOOR_SPAWN_OFFSET = new BlockPos(48, 22, 4);
    private static final BlockPos RIVER_ROAD_1_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(47, 22, 5);
    private static final BlockPos RIVER_ROAD_1_OUTDOOR_ENTRY_POS = new BlockPos(48, 64, 1);
    private static final BlockPos RIVER_ROAD_1_OUTDOOR_EXIT_POS = new BlockPos(48, 64, 2);

    private static final String TAG_PORTAL_MARKER_RIVER_ROAD_1_OUTSIDE = "sdv_portal_marker:1_river_road_outside";
    private static final String TAG_PORTAL_MARKER_RIVER_ROAD_1_INSIDE = "sdv_portal_marker:1_river_road_inside";

    private static final String CARPENTER_SHOP_STRUCTURE_PATH = "data/stardewcraft/structures/interior/carpenter_shop.schem";
    private static final BlockPos CARPENTER_SHOP_ORIGIN = BlockPos.ZERO;
    private static final BlockPos CARPENTER_SHOP_INDOOR_SPAWN_OFFSET = new BlockPos(30, 51, -117);
    private static final BlockPos CARPENTER_SHOP_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(29, 51, -116);
    private static final BlockPos CARPENTER_SHOP_OUTDOOR_ENTRY_POS = new BlockPos(28, 85, -115);
    private static final BlockPos CARPENTER_SHOP_OUTDOOR_EXIT_POS = new BlockPos(29, 85, -113);

    private static final String TAG_PORTAL_MARKER_CARPENTER_SHOP_OUTSIDE = "sdv_portal_marker:carpenter_shop_outside";
    private static final String TAG_PORTAL_MARKER_CARPENTER_SHOP_INSIDE = "sdv_portal_marker:carpenter_shop_inside";

    private static final String WILLOW_LANE_1_STRUCTURE_PATH = "data/stardewcraft/structures/interior/1_willow_lane.schem";
    private static final BlockPos WILLOW_LANE_1_ORIGIN = BlockPos.ZERO;
    private static final BlockPos WILLOW_LANE_1_INDOOR_SPAWN_OFFSET = new BlockPos(-28, 38, 43);
    private static final BlockPos WILLOW_LANE_1_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-29, 38, 44);
    private static final BlockPos WILLOW_LANE_1_OUTDOOR_ENTRY_POS = new BlockPos(-29, 65, 39);
    private static final BlockPos WILLOW_LANE_1_OUTDOOR_EXIT_POS = new BlockPos(-28, 64, 42);

    private static final String TAG_PORTAL_MARKER_WILLOW_LANE_1_OUTSIDE = "sdv_portal_marker:1_willow_lane_outside";
    private static final String TAG_PORTAL_MARKER_WILLOW_LANE_1_INSIDE = "sdv_portal_marker:1_willow_lane_inside";

    private static final String WILLOW_LANE_2_STRUCTURE_PATH = "data/stardewcraft/structures/interior/2_willow_lane.schem";
    private static final BlockPos WILLOW_LANE_2_ORIGIN = BlockPos.ZERO;
    private static final BlockPos WILLOW_LANE_2_INDOOR_SPAWN_OFFSET = new BlockPos(-10, 24, 40);
    private static final BlockPos WILLOW_LANE_2_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-11, 24, 41);
    private static final BlockPos WILLOW_LANE_2_OUTDOOR_ENTRY_POS = new BlockPos(-11, 64, 39);
    private static final BlockPos WILLOW_LANE_2_OUTDOOR_EXIT_POS = new BlockPos(-10, 64, 40);

    private static final String TAG_PORTAL_MARKER_WILLOW_LANE_2_OUTSIDE = "sdv_portal_marker:2_willow_lane_outside";
    private static final String TAG_PORTAL_MARKER_WILLOW_LANE_2_INSIDE = "sdv_portal_marker:2_willow_lane_inside";

    private static final String MARNIE_RANCH_STRUCTURE_PATH = "data/stardewcraft/structures/interior/marnie_ranch.schem";
    private static final BlockPos MARNIE_RANCH_ORIGIN = BlockPos.ZERO;
    private static final BlockPos MARNIE_RANCH_INDOOR_SPAWN_OFFSET = new BlockPos(-86, 34, 25);
    private static final BlockPos MARNIE_RANCH_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-87, 34, 26);
    private static final BlockPos MARNIE_RANCH_OUTDOOR_ENTRY_POS = new BlockPos(-92, 64, 21);
    private static final BlockPos MARNIE_RANCH_OUTDOOR_EXIT_POS = new BlockPos(-92, 64, 23);

    private static final String TAG_PORTAL_MARKER_MARNIE_RANCH_OUTSIDE = "sdv_portal_marker:marnie_ranch_outside";
    private static final String TAG_PORTAL_MARKER_MARNIE_RANCH_INSIDE = "sdv_portal_marker:marnie_ranch_inside";

    private static final String LEAH_COTTAGE_STRUCTURE_PATH = "data/stardewcraft/structures/interior/leah_cottage.schem";
    private static final BlockPos LEAH_COTTAGE_ORIGIN = BlockPos.ZERO;
    private static final BlockPos LEAH_COTTAGE_INDOOR_SPAWN_OFFSET = new BlockPos(-83, 38, 51);
    private static final BlockPos LEAH_COTTAGE_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-84, 38, 52);
    private static final BlockPos LEAH_COTTAGE_OUTDOOR_ENTRY_POS = new BlockPos(-83, 64, 52);
    private static final BlockPos LEAH_COTTAGE_OUTDOOR_EXIT_POS = new BlockPos(-83, 64, 53);

    private static final String TAG_PORTAL_MARKER_LEAH_COTTAGE_OUTSIDE = "sdv_portal_marker:leah_cottage_outside";
    private static final String TAG_PORTAL_MARKER_LEAH_COTTAGE_INSIDE = "sdv_portal_marker:leah_cottage_inside";

    private static final String ADVENTURER_GUILD_STRUCTURE_PATH = "data/stardewcraft/structures/interior/adventurer_guild.schem";
    private static final BlockPos ADVENTURER_GUILD_ORIGIN = BlockPos.ZERO;
    private static final BlockPos ADVENTURER_GUILD_INDOOR_SPAWN_OFFSET = new BlockPos(106, 60, -143);
    private static final BlockPos ADVENTURER_GUILD_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(105, 60, -142);
    private static final BlockPos ADVENTURER_GUILD_OUTDOOR_ENTRY_POS = new BlockPos(106, 81, -142);
    private static final BlockPos ADVENTURER_GUILD_OUTDOOR_EXIT_POS = new BlockPos(106, 81, -141);

    private static final String TAG_PORTAL_MARKER_ADVENTURER_GUILD_OUTSIDE = "sdv_portal_marker:adventurer_guild_outside";
    private static final String TAG_PORTAL_MARKER_ADVENTURER_GUILD_INSIDE = "sdv_portal_marker:adventurer_guild_inside";

    private static final String FISH_SHOP_STRUCTURE_PATH = "data/stardewcraft/structures/interior/fish_shop.schem";
    private static final BlockPos FISH_SHOP_ORIGIN = BlockPos.ZERO;
    private static final BlockPos FISH_SHOP_INDOOR_SPAWN_OFFSET = new BlockPos(65, 31, 147);
    private static final BlockPos FISH_SHOP_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(64, 31, 148);
    private static final BlockPos FISH_SHOP_OUTDOOR_ENTRY_POS = new BlockPos(64, 60, 150);
    private static final BlockPos FISH_SHOP_OUTDOOR_EXIT_POS = new BlockPos(64, 60, 151);

    private static final String TAG_PORTAL_MARKER_FISH_SHOP_OUTSIDE = "sdv_portal_marker:fish_shop_outside";
    private static final String TAG_PORTAL_MARKER_FISH_SHOP_INSIDE = "sdv_portal_marker:fish_shop_inside";

    private static final String ELLIOTT_CABIN_STRUCTURE_PATH = "data/stardewcraft/structures/interior/elliott_cabin.schem";
    private static final BlockPos ELLIOTT_CABIN_ORIGIN = BlockPos.ZERO;
    private static final BlockPos ELLIOTT_CABIN_INDOOR_SPAWN_OFFSET = new BlockPos(75, 45, 102);
    private static final BlockPos ELLIOTT_CABIN_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(74, 45, 103);
    private static final BlockPos ELLIOTT_CABIN_OUTDOOR_ENTRY_POS = new BlockPos(80, 60, 101);
    private static final BlockPos ELLIOTT_CABIN_OUTDOOR_EXIT_POS = new BlockPos(80, 60, 102);

    private static final String TAG_PORTAL_MARKER_ELLIOTT_CABIN_OUTSIDE = "sdv_portal_marker:elliott_cabin_outside";
    private static final String TAG_PORTAL_MARKER_ELLIOTT_CABIN_INSIDE = "sdv_portal_marker:elliott_cabin_inside";

    private static final String WIZARD_TOWER_STRUCTURE_PATH = "data/stardewcraft/structures/interior/wizard_tower.schem";
    private static final BlockPos WIZARD_TOWER_ORIGIN = BlockPos.ZERO;
    private static final BlockPos WIZARD_TOWER_INDOOR_SPAWN_OFFSET = new BlockPos(-178, 34, 63);
    private static final BlockPos WIZARD_TOWER_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-179, 34, 64);
    private static final BlockPos WIZARD_TOWER_OUTDOOR_ENTRY_POS = new BlockPos(-179, 69, 50);
    private static final BlockPos WIZARD_TOWER_OUTDOOR_EXIT_POS = new BlockPos(-179, 69, 51);

    private static final String TAG_PORTAL_MARKER_WIZARD_TOWER_OUTSIDE = "sdv_portal_marker:wizard_tower_outside";
    private static final String TAG_PORTAL_MARKER_WIZARD_TOWER_INSIDE = "sdv_portal_marker:wizard_tower_inside";
    private static final String TAG_PORTAL_MARKER_WIZARD_TOWER_RETURN_OVERWORLD = "sdv_portal_marker:wizard_tower_return_overworld";
    private static final BlockPos WIZARD_TOWER_RETURN_OVERWORLD_BASE = new BlockPos(-175, 34, 54);

    // ---- 绿洲（沙漠 Oasis / Sandy's shop） ----
    private static final String OASIS_STRUCTURE_PATH = "data/stardewcraft/structures/interior/oasis_interior.schem";
    public static final BlockPos OASIS_ORIGIN = BlockPos.ZERO;
    private static final BlockPos OASIS_INDOOR_SPAWN_OFFSET = new BlockPos(-252, 30, -147);
    private static final BlockPos OASIS_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-253, 30, -146);
    /** Oasis 出门后传送到沙漠室外（Oasis 入口旁，Z-1） */
    public static final BlockPos OASIS_OUTDOOR_EXIT_POS = new BlockPos(-251, 64, -141);
    private static final String TAG_PORTAL_MARKER_OASIS_INSIDE = "sdv_portal_marker:oasis_inside";

    // ---- Joja 超市（JojaMart） ----
    private static final String JOJA_MART_STRUCTURE_PATH = "data/stardewcraft/structures/interior/joja_mart.schem";
    public static final BlockPos JOJA_MART_ORIGIN = BlockPos.ZERO;
    /** 进店后玩家落点：origin +(3,1,13)，面朝东（-X 方向是门，看向店内东侧） */
    private static final BlockPos JOJA_MART_INDOOR_SPAWN_OFFSET = new BlockPos(108, 45, -19);
    /** 室内出口触发方块基点：origin +(1,1,13)，2 高 x 1 宽 x 2 深（Z+13~14） */
    private static final BlockPos JOJA_MART_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(108, 45, -17);
    /** 出店后玩家落点：镇上 Joja 门外，面朝北（-Z） */
    public static final BlockPos JOJA_MART_OUTDOOR_EXIT_POS = new BlockPos(109, 65, -15);
    /** 室外入口触发方块基点。 */
    private static final BlockPos JOJA_MART_OUTDOOR_ENTRY_BASE = new BlockPos(108, 65, -17);
    private static final String TAG_PORTAL_MARKER_JOJA_MART_OUTSIDE = "sdv_portal_marker:joja_mart_outside";
    private static final String TAG_PORTAL_MARKER_JOJA_MART_INSIDE  = "sdv_portal_marker:joja_mart_inside";

    // ---- 拖车（Pam / Penny）----
    private static final BlockPos TRAILER_INDOOR_SPAWN_OFFSET = new BlockPos(72, 35, 4);
    private static final BlockPos TRAILER_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(71, 35, 5);
    private static final BlockPos TRAILER_OUTDOOR_ENTRY_POS = new BlockPos(72, 64, 9);
    private static final BlockPos TRAILER_OUTDOOR_EXIT_POS = new BlockPos(73, 64, 9);
    private static final String TAG_PORTAL_MARKER_TRAILER_OUTSIDE = "sdv_portal_marker:trailer_outside";
    private static final String TAG_PORTAL_MARKER_TRAILER_INSIDE = "sdv_portal_marker:trailer_inside";

    // ---- 社区中心 ----
    static final String CC_RUINS_PATH = "data/stardewcraft/structures/interior/community_center_ruins.schem";
    @SuppressWarnings("unused")
    static final String CC_REFURBISHED_PATH = "data/stardewcraft/structures/interior/community_center_refurbished.schem";
    public static final BlockPos CC_ORIGIN = new BlockPos(18816, 69, 18816);
    /** schem 的 pos1（建造世界绝对坐标），用于偏移换算：relative = absolute - SCHEM_POS1 */
    public static final BlockPos CC_SCHEM_POS1 = new BlockPos(-146, 101, -1333);
    public static final BlockPos CC_INDOOR_SPAWN_OFFSET = new BlockPos(16, 1, 37);   // 玩家室内出生点 → 18832,70,18853
    public static final BlockPos CC_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(17, 1, 37); // 室内出口交互实体基点 → (18833,70,18853)~(18833,71,18854)
    /** CC 出门后传送目标（全体共享） */
    public static final BlockPos CC_OUTDOOR_EXIT_POS = new BlockPos(54, 66, -48);
    private static final BlockPos CC_OUTDOOR_INTERACTION_BASE = new BlockPos(54, 67, -49); // 室外入口交互实体基点

    private static final String TAG_PORTAL_MARKER_CC_OUTSIDE = "sdv_portal_marker:cc_outside";

    // ---- 温室 ----
    static final String GREENHOUSE_INTERIOR_PATH = "data/stardewcraft/structures/greenhouse/green_house_interior.schem";
    public static final BlockPos GREENHOUSE_INTERIOR_ORIGIN = new BlockPos(18816, 70, 19392);
    public static final BlockPos GREENHOUSE_INDOOR_SPAWN_OFFSET = new BlockPos(2, 1, 10);
    static final BlockPos GREENHOUSE_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(1, 1, 10);

    private static final String TAG_PORTAL_MARKER_GREENHOUSE_OUTSIDE = "sdv_portal_marker:greenhouse_outside";

    // ---- 农场洞穴 ----
    public static final String FARM_CAVE_PATH = "data/stardewcraft/structures/farm/cave.schem";
    /** 每玩家农场洞穴室内基础 origin（实际 origin = 此值 + index * CAVE_Z_STRIDE） */
    public static final BlockPos FARM_CAVE_INTERIOR_ORIGIN = new BlockPos(18944, 70, 19392);
    /** schem 9×6×10，min corner 映射到 origin；玩家入口 spawn 局部 (2,1,6) 朝东 */
    public static final BlockPos FARM_CAVE_INDOOR_SPAWN_OFFSET = new BlockPos(2, 1, 6);
    /** 室内出洞传送触发方块基点 (1,1,6)，高 2 格 */
    static final BlockPos FARM_CAVE_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(1, 1, 6);
    /** cave schem 外接尺寸 (width, height, length) */
    public static final int FARM_CAVE_SCHEM_W = 9;
    public static final int FARM_CAVE_SCHEM_H = 6;
    public static final int FARM_CAVE_SCHEM_L = 10;

    private static final String TAG_PORTAL_MARKER_FARM_CAVE_OUTSIDE = "sdv_portal_marker:farm_cave_outside";
    static final String TAG_PORTAL_MARKER_FARM_CAVE_INSIDE = "sdv_portal_marker:farm_cave_inside";

    // ---- 矿井入口（室外） ----
    private static final String TAG_PORTAL_MARKER_MINE_OUTSIDE = "sdv_portal_marker:mine_entrance";
    private static final BlockPos MINE_OUTDOOR_ENTRY_POS = new BlockPos(83, 81, -146);

    private static final String DATA_NAME = "stardew_interior_subspace_layout";

    private static final List<FixedStructure> FIXED_STRUCTURES = new ArrayList<>();
    private static final int STRUCTURE_FORCE_RADIUS_CHUNKS = 3;
    private static boolean interiorChunksForced;

    static {
        // 用户指定：室内坐标都放在 X>10000, Z>10000，且只需保证互不重叠。
        register("pierre_house", PIERRE_HOUSE_STRUCTURE_PATH, PIERRE_HOUSE_ORIGIN.getX(), PIERRE_HOUSE_ORIGIN.getY(), PIERRE_HOUSE_ORIGIN.getZ());
        register("museum", MUSEUM_STRUCTURE_PATH, MUSEUM_ORIGIN.getX(), MUSEUM_ORIGIN.getY(), MUSEUM_ORIGIN.getZ());
        register("blacksmith", BLACKSMITH_STRUCTURE_PATH, BLACKSMITH_ORIGIN.getX(), BLACKSMITH_ORIGIN.getY(), BLACKSMITH_ORIGIN.getZ());
        register("saloon", SALOON_STRUCTURE_PATH, SALOON_ORIGIN.getX(), SALOON_ORIGIN.getY(), SALOON_ORIGIN.getZ());
        register("mayor_house", MAYOR_HOUSE_STRUCTURE_PATH, MAYOR_HOUSE_ORIGIN.getX(), MAYOR_HOUSE_ORIGIN.getY(), MAYOR_HOUSE_ORIGIN.getZ());
        register("clinic", CLINIC_STRUCTURE_PATH, CLINIC_ORIGIN.getX(), CLINIC_ORIGIN.getY(), CLINIC_ORIGIN.getZ());
        register("1_river_road", RIVER_ROAD_1_STRUCTURE_PATH, RIVER_ROAD_1_ORIGIN.getX(), RIVER_ROAD_1_ORIGIN.getY(), RIVER_ROAD_1_ORIGIN.getZ());
        register("carpenter_shop", CARPENTER_SHOP_STRUCTURE_PATH, CARPENTER_SHOP_ORIGIN.getX(), CARPENTER_SHOP_ORIGIN.getY(), CARPENTER_SHOP_ORIGIN.getZ());
        register("1_willow_lane", WILLOW_LANE_1_STRUCTURE_PATH, WILLOW_LANE_1_ORIGIN.getX(), WILLOW_LANE_1_ORIGIN.getY(), WILLOW_LANE_1_ORIGIN.getZ());
        register("2_willow_lane", WILLOW_LANE_2_STRUCTURE_PATH, WILLOW_LANE_2_ORIGIN.getX(), WILLOW_LANE_2_ORIGIN.getY(), WILLOW_LANE_2_ORIGIN.getZ());
        register("marnie_ranch", MARNIE_RANCH_STRUCTURE_PATH, MARNIE_RANCH_ORIGIN.getX(), MARNIE_RANCH_ORIGIN.getY(), MARNIE_RANCH_ORIGIN.getZ());
        register("leah_cottage", LEAH_COTTAGE_STRUCTURE_PATH, LEAH_COTTAGE_ORIGIN.getX(), LEAH_COTTAGE_ORIGIN.getY(), LEAH_COTTAGE_ORIGIN.getZ());
        register("adventurer_guild", ADVENTURER_GUILD_STRUCTURE_PATH, ADVENTURER_GUILD_ORIGIN.getX(), ADVENTURER_GUILD_ORIGIN.getY(), ADVENTURER_GUILD_ORIGIN.getZ());
        register("fish_shop", FISH_SHOP_STRUCTURE_PATH, FISH_SHOP_ORIGIN.getX(), FISH_SHOP_ORIGIN.getY(), FISH_SHOP_ORIGIN.getZ());
        register("elliott_cabin", ELLIOTT_CABIN_STRUCTURE_PATH, ELLIOTT_CABIN_ORIGIN.getX(), ELLIOTT_CABIN_ORIGIN.getY(), ELLIOTT_CABIN_ORIGIN.getZ());
        register("wizard_tower", WIZARD_TOWER_STRUCTURE_PATH, WIZARD_TOWER_ORIGIN.getX(), WIZARD_TOWER_ORIGIN.getY(), WIZARD_TOWER_ORIGIN.getZ());
        register("oasis", OASIS_STRUCTURE_PATH, OASIS_ORIGIN.getX(), OASIS_ORIGIN.getY(), OASIS_ORIGIN.getZ());
        register("joja_mart", JOJA_MART_STRUCTURE_PATH, JOJA_MART_ORIGIN.getX(), JOJA_MART_ORIGIN.getY(), JOJA_MART_ORIGIN.getZ());
        // CC 和温室不再注册为 FIXED_STRUCTURES — 由 PlayerInteriorAllocator 按玩家独立加载

        BlockPos indoorSpawn = PIERRE_HOUSE_ORIGIN.offset(PIERRE_INDOOR_SPAWN_OFFSET);
        BlockPos indoorExitPortal = PIERRE_HOUSE_ORIGIN.offset(PIERRE_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos museumIndoorSpawn = MUSEUM_ORIGIN.offset(MUSEUM_INDOOR_SPAWN_OFFSET);
        BlockPos museumIndoorExitPortal = MUSEUM_ORIGIN.offset(MUSEUM_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos blacksmithIndoorSpawn = BLACKSMITH_ORIGIN.offset(BLACKSMITH_INDOOR_SPAWN_OFFSET);
        BlockPos blacksmithIndoorExitPortal = BLACKSMITH_ORIGIN.offset(BLACKSMITH_INDOOR_EXIT_PORTAL_OFFSET);

        // 进室内：传送到 X+6,Y+1,Z+6，朝向正东。
        InteriorPortalRegistry.register(
            "pierre_house_enter",
            new InteriorPortalRegistry.PortalTarget(
                indoorSpawn.getX() + 0.5D,
                indoorSpawn.getY(),
                indoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出室内：回到固定室外落点。
        InteriorPortalRegistry.register(
            "pierre_house_exit",
            new InteriorPortalRegistry.PortalTarget(
                PIERRE_OUTDOOR_ENTRY_POS.getX(),
                PIERRE_OUTDOOR_ENTRY_POS.getY(),
                PIERRE_OUTDOOR_ENTRY_POS.getZ(),
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        // 进博物馆：传送到 X+10,Y+1,Z+5，朝向正东。
        InteriorPortalRegistry.register(
            "museum_enter",
            new InteriorPortalRegistry.PortalTarget(
                museumIndoorSpawn.getX() + 0.5D,
                museumIndoorSpawn.getY(),
                museumIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出博物馆：回到室外落点。
        InteriorPortalRegistry.register(
            "museum_exit",
            new InteriorPortalRegistry.PortalTarget(
                MUSEUM_OUTDOOR_EXIT_POS.getX(),
                MUSEUM_OUTDOOR_EXIT_POS.getY(),
                MUSEUM_OUTDOOR_EXIT_POS.getZ(),
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        // 进铁匠铺：传送到 X+3,Y+1,Z+8，朝向正东。
        InteriorPortalRegistry.register(
            "blacksmith_enter",
            new InteriorPortalRegistry.PortalTarget(
                blacksmithIndoorSpawn.getX() + 0.5D,
                blacksmithIndoorSpawn.getY(),
                blacksmithIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出铁匠铺：回到室外落点。
        InteriorPortalRegistry.register(
            "blacksmith_exit",
            new InteriorPortalRegistry.PortalTarget(
                BLACKSMITH_OUTDOOR_EXIT_POS.getX(),
                BLACKSMITH_OUTDOOR_EXIT_POS.getY(),
                BLACKSMITH_OUTDOOR_EXIT_POS.getZ(),
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        BlockPos saloonIndoorSpawn = SALOON_ORIGIN.offset(SALOON_INDOOR_SPAWN_OFFSET);
        BlockPos saloonIndoorExitPortal = SALOON_ORIGIN.offset(SALOON_INDOOR_EXIT_PORTAL_OFFSET);

        // 进酒馆：传送到 X+2,Y+1,Z+17，朝向正东。
        InteriorPortalRegistry.register(
            "saloon_enter",
            new InteriorPortalRegistry.PortalTarget(
                saloonIndoorSpawn.getX() + 0.5D,
                saloonIndoorSpawn.getY(),
                saloonIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出酒馆：回到室外落点。
        InteriorPortalRegistry.register(
            "saloon_exit",
            new InteriorPortalRegistry.PortalTarget(
                SALOON_OUTDOOR_EXIT_POS.getX() + 0.5D,
                SALOON_OUTDOOR_EXIT_POS.getY(),
                SALOON_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        // 室内出口交互体固定在结构相对坐标。
        StardewCraft.LOGGER.info("[INTERIOR] Pierre indoor exit interaction anchor = {}", indoorExitPortal);
        StardewCraft.LOGGER.info("[INTERIOR] Museum indoor exit interaction anchor = {}", museumIndoorExitPortal);
        StardewCraft.LOGGER.info("[INTERIOR] Blacksmith indoor exit interaction anchor = {}", blacksmithIndoorExitPortal);
        StardewCraft.LOGGER.info("[INTERIOR] Saloon indoor exit interaction anchor = {}", saloonIndoorExitPortal);

        BlockPos mayorHouseIndoorSpawn = MAYOR_HOUSE_ORIGIN.offset(MAYOR_HOUSE_INDOOR_SPAWN_OFFSET);
        BlockPos mayorHouseIndoorExitPortal = MAYOR_HOUSE_ORIGIN.offset(MAYOR_HOUSE_INDOOR_EXIT_PORTAL_OFFSET);

        // 进市长家：传送到 X+2,Y+1,Z+5，朝向正东。
        InteriorPortalRegistry.register(
            "mayor_house_enter",
            new InteriorPortalRegistry.PortalTarget(
                mayorHouseIndoorSpawn.getX() + 0.5D,
                mayorHouseIndoorSpawn.getY(),
                mayorHouseIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出市长家：回到室外落点。
        InteriorPortalRegistry.register(
            "mayor_house_exit",
            new InteriorPortalRegistry.PortalTarget(
                MAYOR_HOUSE_OUTDOOR_EXIT_POS.getX() + 0.5D,
                MAYOR_HOUSE_OUTDOOR_EXIT_POS.getY(),
                MAYOR_HOUSE_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] Mayor house indoor exit interaction anchor = {}", mayorHouseIndoorExitPortal);

        BlockPos clinicIndoorSpawn = CLINIC_ORIGIN.offset(CLINIC_INDOOR_SPAWN_OFFSET);
        BlockPos clinicIndoorExitPortal = CLINIC_ORIGIN.offset(CLINIC_INDOOR_EXIT_PORTAL_OFFSET);

        // 进诊所：传送到 X+2,Y+1,Z+11，朝向正东。
        InteriorPortalRegistry.register(
            "clinic_enter",
            new InteriorPortalRegistry.PortalTarget(
                clinicIndoorSpawn.getX() + 0.5D,
                clinicIndoorSpawn.getY(),
                clinicIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出诊所：回到室外落点。
        InteriorPortalRegistry.register(
            "clinic_exit",
            new InteriorPortalRegistry.PortalTarget(
                CLINIC_OUTDOOR_EXIT_POS.getX() + 0.5D,
                CLINIC_OUTDOOR_EXIT_POS.getY(),
                CLINIC_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] Clinic indoor exit interaction anchor = {}", clinicIndoorExitPortal);

        BlockPos riverRoad1IndoorSpawn = RIVER_ROAD_1_ORIGIN.offset(RIVER_ROAD_1_INDOOR_SPAWN_OFFSET);
        BlockPos riverRoad1IndoorExitPortal = RIVER_ROAD_1_ORIGIN.offset(RIVER_ROAD_1_INDOOR_EXIT_PORTAL_OFFSET);

        // 进1号河路：传送到 X+3,Y+1,Z+12，朝向正东。
        InteriorPortalRegistry.register(
            "1_river_road_enter",
            new InteriorPortalRegistry.PortalTarget(
                riverRoad1IndoorSpawn.getX() + 0.5D,
                riverRoad1IndoorSpawn.getY(),
                riverRoad1IndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出1号河路：回到室外落点。
        InteriorPortalRegistry.register(
            "1_river_road_exit",
            new InteriorPortalRegistry.PortalTarget(
                RIVER_ROAD_1_OUTDOOR_EXIT_POS.getX() + 0.5D,
                RIVER_ROAD_1_OUTDOOR_EXIT_POS.getY(),
                RIVER_ROAD_1_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] 1_river_road indoor exit interaction anchor = {}", riverRoad1IndoorExitPortal);

        BlockPos carpenterShopIndoorSpawn = CARPENTER_SHOP_ORIGIN.offset(CARPENTER_SHOP_INDOOR_SPAWN_OFFSET);
        BlockPos carpenterShopIndoorExitPortal = CARPENTER_SHOP_ORIGIN.offset(CARPENTER_SHOP_INDOOR_EXIT_PORTAL_OFFSET);

        // 进木匠铺：传送到 X+13,Y+5,Z+7，朝向正东。
        InteriorPortalRegistry.register(
            "carpenter_shop_enter",
            new InteriorPortalRegistry.PortalTarget(
                carpenterShopIndoorSpawn.getX() + 0.5D,
                carpenterShopIndoorSpawn.getY(),
                carpenterShopIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出木匠铺：回到室外落点。
        InteriorPortalRegistry.register(
            "carpenter_shop_exit",
            new InteriorPortalRegistry.PortalTarget(
                CARPENTER_SHOP_OUTDOOR_EXIT_POS.getX() + 0.5D,
                CARPENTER_SHOP_OUTDOOR_EXIT_POS.getY(),
                CARPENTER_SHOP_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] Carpenter shop indoor exit interaction anchor = {}", carpenterShopIndoorExitPortal);

        BlockPos willowLane1IndoorSpawn = WILLOW_LANE_1_ORIGIN.offset(WILLOW_LANE_1_INDOOR_SPAWN_OFFSET);
        BlockPos willowLane1IndoorExitPortal = WILLOW_LANE_1_ORIGIN.offset(WILLOW_LANE_1_INDOOR_EXIT_PORTAL_OFFSET);

        // 进1号柳巷：传送到 X+2,Y+1,Z+5，朝向正东。
        InteriorPortalRegistry.register(
            "1_willow_lane_enter",
            new InteriorPortalRegistry.PortalTarget(
                willowLane1IndoorSpawn.getX() + 0.5D,
                willowLane1IndoorSpawn.getY(),
                willowLane1IndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出1号柳巷：回到室外落点。
        InteriorPortalRegistry.register(
            "1_willow_lane_exit",
            new InteriorPortalRegistry.PortalTarget(
                WILLOW_LANE_1_OUTDOOR_EXIT_POS.getX() + 0.5D,
                WILLOW_LANE_1_OUTDOOR_EXIT_POS.getY(),
                WILLOW_LANE_1_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] 1_willow_lane indoor exit interaction anchor = {}", willowLane1IndoorExitPortal);

        BlockPos willowLane2IndoorSpawn = WILLOW_LANE_2_ORIGIN.offset(WILLOW_LANE_2_INDOOR_SPAWN_OFFSET);
        BlockPos willowLane2IndoorExitPortal = WILLOW_LANE_2_ORIGIN.offset(WILLOW_LANE_2_INDOOR_EXIT_PORTAL_OFFSET);

        // 进2号柳巷：传送到 X+4,Y+1,Z+2，朝向正东。
        InteriorPortalRegistry.register(
            "2_willow_lane_enter",
            new InteriorPortalRegistry.PortalTarget(
                willowLane2IndoorSpawn.getX() + 0.5D,
                willowLane2IndoorSpawn.getY(),
                willowLane2IndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出2号柳巷：回到室外落点。
        InteriorPortalRegistry.register(
            "2_willow_lane_exit",
            new InteriorPortalRegistry.PortalTarget(
                WILLOW_LANE_2_OUTDOOR_EXIT_POS.getX() + 0.5D,
                WILLOW_LANE_2_OUTDOOR_EXIT_POS.getY(),
                WILLOW_LANE_2_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] 2_willow_lane indoor exit interaction anchor = {}", willowLane2IndoorExitPortal);

        BlockPos marnieRanchIndoorSpawn = MARNIE_RANCH_ORIGIN.offset(MARNIE_RANCH_INDOOR_SPAWN_OFFSET);
        BlockPos marnieRanchIndoorExitPortal = MARNIE_RANCH_ORIGIN.offset(MARNIE_RANCH_INDOOR_EXIT_PORTAL_OFFSET);

        // 进马尼牧场：传送到 Z+15,X+2,Y+1，面朝正东。
        InteriorPortalRegistry.register(
            "marnie_ranch_enter",
            new InteriorPortalRegistry.PortalTarget(
                marnieRanchIndoorSpawn.getX() + 0.5D,
                marnieRanchIndoorSpawn.getY(),
                marnieRanchIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出马尼牧场：回到室外落点。
        InteriorPortalRegistry.register(
            "marnie_ranch_exit",
            new InteriorPortalRegistry.PortalTarget(
                MARNIE_RANCH_OUTDOOR_EXIT_POS.getX() + 0.5D,
                MARNIE_RANCH_OUTDOOR_EXIT_POS.getY(),
                MARNIE_RANCH_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] marnie_ranch indoor exit interaction anchor = {}", marnieRanchIndoorExitPortal);

        BlockPos leahCottageIndoorSpawn = LEAH_COTTAGE_ORIGIN.offset(LEAH_COTTAGE_INDOOR_SPAWN_OFFSET);
        BlockPos leahCottageIndoorExitPortal = LEAH_COTTAGE_ORIGIN.offset(LEAH_COTTAGE_INDOOR_EXIT_PORTAL_OFFSET);

        // 进莉亚小屋：传送到 X+6,Y+1,Z+7，面朝正东。
        InteriorPortalRegistry.register(
            "leah_cottage_enter",
            new InteriorPortalRegistry.PortalTarget(
                leahCottageIndoorSpawn.getX() + 0.5D,
                leahCottageIndoorSpawn.getY(),
                leahCottageIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出莉亚小屋：回到室外落点。
        InteriorPortalRegistry.register(
            "leah_cottage_exit",
            new InteriorPortalRegistry.PortalTarget(
                LEAH_COTTAGE_OUTDOOR_EXIT_POS.getX() + 0.5D,
                LEAH_COTTAGE_OUTDOOR_EXIT_POS.getY(),
                LEAH_COTTAGE_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] leah_cottage indoor exit interaction anchor = {}", leahCottageIndoorExitPortal);

        BlockPos adventurerGuildIndoorSpawn = ADVENTURER_GUILD_ORIGIN.offset(ADVENTURER_GUILD_INDOOR_SPAWN_OFFSET);
        BlockPos adventurerGuildIndoorExitPortal = ADVENTURER_GUILD_ORIGIN.offset(ADVENTURER_GUILD_INDOOR_EXIT_PORTAL_OFFSET);

        // 进冒险家公会：传送到 X+2,Y+1,Z+6，面朝正东。
        InteriorPortalRegistry.register(
            "adventurer_guild_enter",
            new InteriorPortalRegistry.PortalTarget(
                adventurerGuildIndoorSpawn.getX() + 0.5D,
                adventurerGuildIndoorSpawn.getY(),
                adventurerGuildIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出冒险家公会：回到室外落点。
        InteriorPortalRegistry.register(
            "adventurer_guild_exit",
            new InteriorPortalRegistry.PortalTarget(
                ADVENTURER_GUILD_OUTDOOR_EXIT_POS.getX() + 0.5D,
                ADVENTURER_GUILD_OUTDOOR_EXIT_POS.getY(),
                ADVENTURER_GUILD_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] adventurer_guild indoor exit interaction anchor = {}", adventurerGuildIndoorExitPortal);

        BlockPos fishShopIndoorSpawn = FISH_SHOP_ORIGIN.offset(FISH_SHOP_INDOOR_SPAWN_OFFSET);
        BlockPos fishShopIndoorExitPortal = FISH_SHOP_ORIGIN.offset(FISH_SHOP_INDOOR_EXIT_PORTAL_OFFSET);

        // 进鱼店：传送到 X+2,Y+1,Z+6，面朝正东。
        InteriorPortalRegistry.register(
            "fish_shop_enter",
            new InteriorPortalRegistry.PortalTarget(
                fishShopIndoorSpawn.getX() + 0.5D,
                fishShopIndoorSpawn.getY(),
                fishShopIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出鱼店：回到室外落点。
        InteriorPortalRegistry.register(
            "fish_shop_exit",
            new InteriorPortalRegistry.PortalTarget(
                FISH_SHOP_OUTDOOR_EXIT_POS.getX() + 0.5D,
                FISH_SHOP_OUTDOOR_EXIT_POS.getY(),
                FISH_SHOP_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] fish_shop indoor exit interaction anchor = {}", fishShopIndoorExitPortal);

        BlockPos elliottCabinIndoorSpawn = ELLIOTT_CABIN_ORIGIN.offset(ELLIOTT_CABIN_INDOOR_SPAWN_OFFSET);
        BlockPos elliottCabinIndoorExitPortal = ELLIOTT_CABIN_ORIGIN.offset(ELLIOTT_CABIN_INDOOR_EXIT_PORTAL_OFFSET);

        // 进艾利欧特小屋：传送到 X+2,Y+1,Z+3，面朝正东。
        InteriorPortalRegistry.register(
            "elliott_cabin_enter",
            new InteriorPortalRegistry.PortalTarget(
                elliottCabinIndoorSpawn.getX() + 0.5D,
                elliottCabinIndoorSpawn.getY(),
                elliottCabinIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出艾利欧特小屋：回到室外落点。
        InteriorPortalRegistry.register(
            "elliott_cabin_exit",
            new InteriorPortalRegistry.PortalTarget(
                ELLIOTT_CABIN_OUTDOOR_EXIT_POS.getX() + 0.5D,
                ELLIOTT_CABIN_OUTDOOR_EXIT_POS.getY(),
                ELLIOTT_CABIN_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] elliott_cabin indoor exit interaction anchor = {}", elliottCabinIndoorExitPortal);

        BlockPos wizardTowerIndoorSpawn = WIZARD_TOWER_ORIGIN.offset(WIZARD_TOWER_INDOOR_SPAWN_OFFSET);
        BlockPos wizardTowerIndoorExitPortal = WIZARD_TOWER_ORIGIN.offset(WIZARD_TOWER_INDOOR_EXIT_PORTAL_OFFSET);

        // 进巫师塔：传送到 X+2,Y+1,Z+9，面朝正东。
        InteriorPortalRegistry.register(
            "wizard_tower_enter",
            new InteriorPortalRegistry.PortalTarget(
                wizardTowerIndoorSpawn.getX() + 0.5D,
                wizardTowerIndoorSpawn.getY(),
                wizardTowerIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出巫师塔：回到室外落点。
        InteriorPortalRegistry.register(
            "wizard_tower_exit",
            new InteriorPortalRegistry.PortalTarget(
                WIZARD_TOWER_OUTDOOR_EXIT_POS.getX() + 0.5D,
                WIZARD_TOWER_OUTDOOR_EXIT_POS.getY(),
                WIZARD_TOWER_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] wizard_tower indoor exit interaction anchor = {}", wizardTowerIndoorExitPortal);

        // ── 绿洲（Oasis） ──
        BlockPos oasisIndoorSpawn = OASIS_ORIGIN.offset(OASIS_INDOOR_SPAWN_OFFSET);
        BlockPos oasisIndoorExitPortal = OASIS_ORIGIN.offset(OASIS_INDOOR_EXIT_PORTAL_OFFSET);

        // 进绿洲：传送到室内入口，面朝北。
        InteriorPortalRegistry.register(
            "oasis_enter",
            new InteriorPortalRegistry.PortalTarget(
                oasisIndoorSpawn.getX() + 0.5D,
                oasisIndoorSpawn.getY(),
                oasisIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出绿洲：回到沙漠 Oasis 入口外侧。
        InteriorPortalRegistry.register(
            "oasis_exit",
            new InteriorPortalRegistry.PortalTarget(
                OASIS_OUTDOOR_EXIT_POS.getX() + 0.5D,
                OASIS_OUTDOOR_EXIT_POS.getY(),
                OASIS_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] oasis indoor exit interaction anchor = {}", oasisIndoorExitPortal);

        // ── Joja 超市（JojaMart） ──
        BlockPos jojaMartIndoorSpawn = JOJA_MART_ORIGIN.offset(JOJA_MART_INDOOR_SPAWN_OFFSET);
        BlockPos jojaMartIndoorExitPortal = JOJA_MART_ORIGIN.offset(JOJA_MART_INDOOR_EXIT_PORTAL_OFFSET);

        // 进 Joja：传送到 origin+(3,1,13)，面朝东。
        InteriorPortalRegistry.register(
            "joja_mart_enter",
            new InteriorPortalRegistry.PortalTarget(
                jojaMartIndoorSpawn.getX() + 0.5D,
                jojaMartIndoorSpawn.getY(),
                jojaMartIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        // 出 Joja：回到室外落点。
        InteriorPortalRegistry.register(
            "joja_mart_exit",
            new InteriorPortalRegistry.PortalTarget(
                JOJA_MART_OUTDOOR_EXIT_POS.getX() + 0.5D,
                JOJA_MART_OUTDOOR_EXIT_POS.getY(),
                JOJA_MART_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] joja_mart indoor exit interaction anchor = {}", jojaMartIndoorExitPortal);

        // ── 拖车（Pam / Penny） ──
        BlockPos trailerIndoorSpawn = TRAILER_INDOOR_SPAWN_OFFSET;
        BlockPos trailerIndoorExitPortal = TRAILER_INDOOR_EXIT_PORTAL_OFFSET;

        InteriorPortalRegistry.register(
            "trailer_enter",
            new InteriorPortalRegistry.PortalTarget(
                trailerIndoorSpawn.getX() + 0.5D,
                trailerIndoorSpawn.getY(),
                trailerIndoorSpawn.getZ() + 0.5D,
                180.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.ENTRANCE
            )
        );

        InteriorPortalRegistry.register(
            "trailer_exit",
            new InteriorPortalRegistry.PortalTarget(
                TRAILER_OUTDOOR_EXIT_POS.getX() + 0.5D,
                TRAILER_OUTDOOR_EXIT_POS.getY(),
                TRAILER_OUTDOOR_EXIT_POS.getZ() + 0.5D,
                0.0F,
                0.0F,
                InteriorPortalRegistry.PortalMode.EXIT
            )
        );

        StardewCraft.LOGGER.info("[INTERIOR] trailer indoor exit interaction anchor = {}", trailerIndoorExitPortal);

        // CC 和温室门户不再静态注册 — 由 InteriorPortalInteractionEvents 动态解析
    }

    public static void register(String id, String structurePath, int x, int y, int z) {
        if (FIXED_INTERIORS_EMBEDDED_IN_PREGEN) {
            return;
        }
        FIXED_STRUCTURES.add(new FixedStructure(id, structurePath, new BlockPos(x, y, z)));
    }

    public static List<FixedStructure> allStructures() {
        return Collections.unmodifiableList(FIXED_STRUCTURES);
    }

    public static boolean isInteriorRegion(ServerLevel level, BlockPos pos) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return false;
        }

        return pos.getX() >= REGION_MIN_X
            && pos.getX() <= REGION_MAX_X
            && pos.getZ() >= REGION_MIN_Z
            && pos.getZ() <= REGION_MAX_Z;
    }

    public static boolean isLayoutInitialized(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return false;
        }
        InteriorSubspaceSavedData data = InteriorSubspaceSavedData.get(level);
        return data.layoutVersion == LAYOUT_VERSION && data.initialized;
    }

    // ──────────────── 分批放置状态 ────────────────
    /** 是否正在分批放置中 */
    private static boolean batchPlacementInProgress = false;
    /** 下一个要放置的结构索引 */
    private static int batchPlacementIndex = 0;
    /** 当前结构的 chunk 是否已全部加载 */
    private static boolean currentChunksReady = false;
    /** 当前结构已强制加载的 chunk 集合（仅用于 Phase 2 检查） */
    private static final Set<ChunkPos> currentForcedChunks = new HashSet<>();
    /** 整个批量过程中所有强制加载过的 chunk（放置结束后逐步释放） */
    private static final Set<ChunkPos> allBatchForcedChunks = new HashSet<>();
    /** 等待 chunk 加载的 tick 计数 */
    private static int chunkWaitTicks = 0;
    /** chunk 加载超时上限（200 tick = 10 秒） */
    private static final int MAX_CHUNK_WAIT_TICKS = 200;
    /** 批量完成后是否正在逐步释放 chunk */
    private static boolean gradualReleaseInProgress = false;
    /** 逐步释放用的列表 */
    private static final List<ChunkPos> chunksToRelease = new ArrayList<>();
    /** 每批释放的 chunk 数量 */
    private static final int CHUNKS_RELEASE_PER_BATCH = 2;
    /** 每隔多少 tick 释放一批（给 IO 线程喘息时间） */
    private static final int RELEASE_TICK_INTERVAL = 5;
    /** 结构放置完成后延迟多少 tick 再开始释放（等 IO 线程把结构数据写完） */
    private static final int RELEASE_DELAY_TICKS = 100;
    /** 释放 tick 计数器 */
    private static int releaseTickCounter = 0;

    /**
     * 每 tick 调用一次（由 InteriorSubspaceLifecycleEvents.onLevelTick 调用）。
     * 分阶段工作：
     * Phase 1: 非阻塞 force-load 当前结构所需的 chunk
     * Phase 2: 等待 chunk 全部就绪
     * Phase 3: 放置结构（不释放 chunk，避免 unload 队列压力）
     * Phase 4: 所有结构完成后，等待 IO 冷却，再逐步释放 chunk
     */
    public static void tickBatchPlacement(ServerLevel level) {
        // ── Phase 4: 逐步释放 chunk ──
        if (gradualReleaseInProgress) {
            releaseTickCounter++;
            // 延迟阶段：等 IO 线程把结构数据写完再开始释放
            if (releaseTickCounter <= RELEASE_DELAY_TICKS) {
                return;
            }
            // 每隔 RELEASE_TICK_INTERVAL tick 才释放一批
            if ((releaseTickCounter - RELEASE_DELAY_TICKS) % RELEASE_TICK_INTERVAL != 0) {
                return;
            }
            int toRelease = Math.min(CHUNKS_RELEASE_PER_BATCH, chunksToRelease.size());
            for (int i = 0; i < toRelease; i++) {
                ChunkPos cp = chunksToRelease.remove(chunksToRelease.size() - 1);
                level.setChunkForced(cp.x, cp.z, false);
            }
            if (chunksToRelease.isEmpty()) {
                gradualReleaseInProgress = false;
                releaseTickCounter = 0;
                StardewCraft.LOGGER.info("[INTERIOR] Finished gradual chunk release");
            }
            return;
        }

        if (!batchPlacementInProgress) {
            return;
        }
        if (batchPlacementIndex >= FIXED_STRUCTURES.size()) {
            // 所有建筑放置完成，执行后续初始化
            batchPlacementInProgress = false;
            batchPlacementIndex = 0;
            resetChunkPreloadState();

            ensurePortalInteractions(level);
            migrateFarmAndGreenhousePortals(level);
            PlayerInteriorAllocator.get(level).reloadAllPlaced(level);
            restoreMuseumExhibitStands(level);

            InteriorSubspaceSavedData data = InteriorSubspaceSavedData.get(level);
            data.layoutVersion = LAYOUT_VERSION;
            data.portalTriggerVersion = PORTAL_TRIGGER_VERSION;
            data.initialized = true;
            data.setDirty();

            StardewCraft.LOGGER.info("[INTERIOR] Interior subspace load complete (batched). version={}", LAYOUT_VERSION);

            // 启动逐步释放
            if (!allBatchForcedChunks.isEmpty()) {
                chunksToRelease.addAll(allBatchForcedChunks);
                allBatchForcedChunks.clear();
                gradualReleaseInProgress = true;
                StardewCraft.LOGGER.info("[INTERIOR] Starting gradual chunk release: {} chunks", chunksToRelease.size());
            }
            return;
        }

        FixedStructure structure = FIXED_STRUCTURES.get(batchPlacementIndex);

        // ── Phase 1: 非阻塞地 force-load 当前结构所需的 chunk ──
        if (currentForcedChunks.isEmpty()) {
            BlockPos origin = structure.origin();
            int cxCenter = origin.getX() >> 4;
            int czCenter = origin.getZ() >> 4;
            // 结构最大 ~73 格 ≈ 5 chunk，向正方向多加载几个 chunk
            for (int dx = -1; dx <= 5; dx++) {
                for (int dz = -1; dz <= 5; dz++) {
                    ChunkPos cp = new ChunkPos(cxCenter + dx, czCenter + dz);
                    currentForcedChunks.add(cp);
                    if (allBatchForcedChunks.add(cp)) {
                        // 只对新 chunk 设置 force（避免重复）
                        level.setChunkForced(cp.x, cp.z, true);
                    }
                }
            }
            chunkWaitTicks = 0;
            currentChunksReady = false;
            return; // 等下个 tick 检查
        }

        // ── Phase 2: 等待所有 chunk 加载完成（非阻塞检查） ──
        if (!currentChunksReady) {
            chunkWaitTicks++;
            boolean allLoaded = true;
            for (ChunkPos cp : currentForcedChunks) {
                if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                    allLoaded = false;
                    break;
                }
            }
            if (!allLoaded) {
                if (chunkWaitTicks < MAX_CHUNK_WAIT_TICKS) {
                    return; // 继续等待
                }
                // 超时仍未加载 → 中止批量放置，启动逐步释放
                StardewCraft.LOGGER.error("[INTERIOR] Chunk preload timeout for {} after {} ticks, aborting batch",
                        structure.id(), chunkWaitTicks);
                batchPlacementInProgress = false;
                batchPlacementIndex = 0;
                resetChunkPreloadState();
                if (!allBatchForcedChunks.isEmpty()) {
                    chunksToRelease.addAll(allBatchForcedChunks);
                    allBatchForcedChunks.clear();
                    gradualReleaseInProgress = true;
                }
                return;
            }
            currentChunksReady = true;
            // 不 return，本 tick 直接放置
        }

        // ── Phase 3: chunk 已就绪，放置结构（不释放 chunk） ──
        boolean placed = StructureLoader.loadAndPlaceWithResult(level, structure.structurePath(), structure.origin());

        // 清理当前结构状态，但不释放 chunk（留到最后逐步释放）
        currentForcedChunks.clear();
        currentChunksReady = false;

        if (!placed) {
            StardewCraft.LOGGER.error("[INTERIOR] Failed to place structure {} at {}, will retry next load",
                    structure.id(), structure.origin());
            batchPlacementInProgress = false;
            batchPlacementIndex = 0;
            resetChunkPreloadState();
            if (!allBatchForcedChunks.isEmpty()) {
                chunksToRelease.addAll(allBatchForcedChunks);
                allBatchForcedChunks.clear();
                gradualReleaseInProgress = true;
            }
            return;
        }
        batchPlacementIndex++;
    }

    private static void resetChunkPreloadState() {
        currentForcedChunks.clear();
        currentChunksReady = false;
        chunkWaitTicks = 0;
    }

    /** 分批放置（包括逐步释放 chunk）是否正在进行中 */
    public static boolean isBatchPlacementInProgress() {
        return batchPlacementInProgress || gradualReleaseInProgress;
    }

    public static void ensureLoaded(ServerLevel level, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        InteriorSubspaceSavedData data = InteriorSubspaceSavedData.get(level);
        if (data.layoutVersion == LAYOUT_VERSION && data.initialized) {
            if (data.portalTriggerVersion < PORTAL_TRIGGER_VERSION) {
                ensurePortalInteractions(level);
                data.portalTriggerVersion = PORTAL_TRIGGER_VERSION;
                data.setDirty();
                StardewCraft.LOGGER.info("[INTERIOR] Portal triggers upgraded. reason={}, portalVersion={}",
                    reason, PORTAL_TRIGGER_VERSION);
            }
            return;
        }

        if (FIXED_INTERIORS_EMBEDDED_IN_PREGEN) {
            ensurePortalInteractions(level);
            migrateFarmAndGreenhousePortals(level);
            restoreMuseumExhibitStands(level);
            data.layoutVersion = LAYOUT_VERSION;
            data.portalTriggerVersion = PORTAL_TRIGGER_VERSION;
            data.initialized = true;
            data.setDirty();
            StardewCraft.LOGGER.info("[INTERIOR] Embedded pregen interior layout initialized. reason={}, version={}",
                reason, LAYOUT_VERSION);
            return;
        }

        // 如果已经在分批放置中，不要重复启动
        if (batchPlacementInProgress) {
            return;
        }

        StardewCraft.LOGGER.info("[INTERIOR] Starting batched interior structure placement. reason={}, version={}, count={}",
            reason, LAYOUT_VERSION, FIXED_STRUCTURES.size());

        batchPlacementInProgress = true;
        batchPlacementIndex = 0;
    }

    /**
     * 在已初始化的存档上重新放置所有已知传送触发方块（小镇入口/出口、矿洞入口、社区中心、
     * 法师塔室内/室外返回等）。幂等，用于 pregen region 覆盖或存档加载后的兜底补放。
     *
     * <p>若布局尚未初始化或正在批量放置，直接跳过——此时 tickBatchPlacement 末尾会自动调用
     * {@link #ensurePortalInteractions(ServerLevel)}，无需外部再次触发。</p>
     */
    public static void replaceAllPortalsIfReady(ServerLevel level, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        InteriorSubspaceSavedData data = InteriorSubspaceSavedData.get(level);
        if (!data.initialized || batchPlacementInProgress) {
            return;
        }
        StardewCraft.LOGGER.info("[INTERIOR] Re-placing all portal triggers. reason={}", reason);
        ensurePortalInteractions(level);
        data.portalTriggerVersion = PORTAL_TRIGGER_VERSION;
        data.setDirty();
        // Farm/greenhouse portals are persisted in the world and only need
        // re-placement on layout version upgrade (handled by batch flow).
        // Skipping them on routine server start avoids loading dozens of
        // distant farm chunks synchronously, which can exceed the 60-second
        // watchdog timeout on servers with many farms.
    }

    public static void forceReload(ServerLevel level, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        InteriorSubspaceSavedData data = InteriorSubspaceSavedData.get(level);
        data.initialized = false;
        data.layoutVersion = 0;
        data.portalTriggerVersion = 0;
        data.setDirty();
        ensureLoaded(level, reason);
    }

    public static void setInteriorChunksForced(ServerLevel level, boolean force, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        if (interiorChunksForced == force) {
            return;
        }

        for (FixedStructure structure : FIXED_STRUCTURES) {
            int centerChunkX = structure.origin().getX() >> 4;
            int centerChunkZ = structure.origin().getZ() >> 4;
            for (int dz = -STRUCTURE_FORCE_RADIUS_CHUNKS; dz <= STRUCTURE_FORCE_RADIUS_CHUNKS; dz++) {
                for (int dx = -STRUCTURE_FORCE_RADIUS_CHUNKS; dx <= STRUCTURE_FORCE_RADIUS_CHUNKS; dx++) {
                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;
                    level.setChunkForced(chunkX, chunkZ, force);
                }
            }
        }
        PlayerInteriorAllocator.get(level).setAllGreenhouseChunksForced(level, force);

        interiorChunksForced = force;
        StardewCraft.LOGGER.info("[INTERIOR] Interior structure chunk forcing toggled: {} (reason={})", force, reason);
    }

    /**
     * 布局重建后的博物馆展示柜恢复。
     * 数据现在是 per-player 的，展示柜内容通过 MuseumStandSyncPacket 按玩家同步，
     * 因此布局重建时无需在服务端恢复 BE 的 displayItem。
     * 仅在有遗留捐赠模式时强制结束（展柜已被重建）。
     */
    private static void restoreMuseumExhibitStands(ServerLevel level) {
        com.stardew.craft.museum.MuseumDonationData data = com.stardew.craft.museum.MuseumDonationData.get(level);
        // Force-end any active donation sessions since the stands have been rebuilt
        for (String uuidStr : data.getAllPlayerUUIDs()) {
            try {
                java.util.UUID playerId = java.util.UUID.fromString(uuidStr);
                if (data.isDonationModeActive(playerId)) {
                    data.forceEndDonationMode(playerId);
                    StardewCraft.LOGGER.info("[INTERIOR] Force-ended donation mode for {} after layout rebuild", uuidStr);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public record FixedStructure(
        String id,
        String structurePath,
        BlockPos origin
    ) {}

    private static void ensurePortalInteractions(ServerLevel level) {
        BlockPos indoorExitPortal = PIERRE_HOUSE_ORIGIN.offset(PIERRE_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos museumIndoorExitPortal = MUSEUM_ORIGIN.offset(MUSEUM_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos blacksmithIndoorExitPortal = BLACKSMITH_ORIGIN.offset(BLACKSMITH_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos saloonIndoorExitPortal = SALOON_ORIGIN.offset(SALOON_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos mayorHouseIndoorExitPortal = MAYOR_HOUSE_ORIGIN.offset(MAYOR_HOUSE_INDOOR_EXIT_PORTAL_OFFSET);
        BlockPos clinicIndoorExitPortal = CLINIC_ORIGIN.offset(CLINIC_INDOOR_EXIT_PORTAL_OFFSET);

        removePortalTriggerIfPresent(level, PIERRE_OUTDOOR_INTERACTION_BASE.above(2));

        // 室外入口：固定点，1 宽 x 2 高。
        placePortalTriggerArea(level, PIERRE_OUTDOOR_INTERACTION_BASE, 2, 1, 1,
            TAG_PORTAL_MARKER_OUTSIDE, "sdv_portal_target:pierre_house_enter");

        // 室内出口：结构相对点，2 高 x 1 宽。
        placePortalTriggerArea(level, indoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_INSIDE, "sdv_portal_target:pierre_house_exit");

        // 博物馆室外入口：2 高 x 2 宽。
        placePortalTriggerArea(level, MUSEUM_OUTDOOR_ENTRY_POS, 2, 1, 1,
            TAG_PORTAL_MARKER_MUSEUM_OUTSIDE, "sdv_portal_target:museum_enter");

        // 博物馆室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, museumIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_MUSEUM_INSIDE, "sdv_portal_target:museum_exit");

        // 铁匠铺室外入口：2 高 x 1 宽。
        placePortalTriggerArea(level, BLACKSMITH_OUTDOOR_ENTRY_POS, 2, 1, 1,
            TAG_PORTAL_MARKER_BLACKSMITH_OUTSIDE, "sdv_portal_target:blacksmith_enter");

        // 铁匠铺室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, blacksmithIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_BLACKSMITH_INSIDE, "sdv_portal_target:blacksmith_exit");

        // 酒馆室外入口：2 高 x 2 宽。
        placePortalTriggerArea(level, SALOON_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_SALOON_OUTSIDE, "sdv_portal_target:saloon_enter");

        // 酒馆室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, saloonIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_SALOON_INSIDE, "sdv_portal_target:saloon_exit");

        // 市长家室外入口：2 高 x 2 宽。
        placePortalTriggerArea(level, MAYOR_HOUSE_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_MAYOR_HOUSE_OUTSIDE, "sdv_portal_target:mayor_house_enter");

        // 市长家室内出口：2 高 x 1 宽 x 2 深。
        placePortalTriggerArea(level, mayorHouseIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_MAYOR_HOUSE_INSIDE, "sdv_portal_target:mayor_house_exit");

        // 诊所室外入口：2 高 x 3 宽。
        placePortalTriggerArea(level, CLINIC_OUTDOOR_ENTRY_POS, 2, 1, 1,
            TAG_PORTAL_MARKER_CLINIC_OUTSIDE, "sdv_portal_target:clinic_enter");

        // 诊所室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, clinicIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_CLINIC_INSIDE, "sdv_portal_target:clinic_exit");

        BlockPos riverRoad1IndoorExitPortal = RIVER_ROAD_1_ORIGIN.offset(RIVER_ROAD_1_INDOOR_EXIT_PORTAL_OFFSET);

        // 1号河路室外入口：2 高 x 1 宽。
        placePortalTriggerArea(level, RIVER_ROAD_1_OUTDOOR_ENTRY_POS, 2, 1, 1,
            TAG_PORTAL_MARKER_RIVER_ROAD_1_OUTSIDE, "sdv_portal_target:1_river_road_enter");

        // 1号河路室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, riverRoad1IndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_RIVER_ROAD_1_INSIDE, "sdv_portal_target:1_river_road_exit");

        BlockPos carpenterShopIndoorExitPortal = CARPENTER_SHOP_ORIGIN.offset(CARPENTER_SHOP_INDOOR_EXIT_PORTAL_OFFSET);

        // 木匠铺室外入口：2 高 x 2 宽。
        placePortalTriggerArea(level, CARPENTER_SHOP_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_CARPENTER_SHOP_OUTSIDE, "sdv_portal_target:carpenter_shop_enter");

        // 木匠铺室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, carpenterShopIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_CARPENTER_SHOP_INSIDE, "sdv_portal_target:carpenter_shop_exit");

        BlockPos willowLane1IndoorExitPortal = WILLOW_LANE_1_ORIGIN.offset(WILLOW_LANE_1_INDOOR_EXIT_PORTAL_OFFSET);

        // 1号柳巷室外入口：2 高 x 2 宽。
        placePortalTriggerArea(level, WILLOW_LANE_1_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_WILLOW_LANE_1_OUTSIDE, "sdv_portal_target:1_willow_lane_enter");

        // 1号柳巷室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, willowLane1IndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_WILLOW_LANE_1_INSIDE, "sdv_portal_target:1_willow_lane_exit");

        BlockPos willowLane2IndoorExitPortal = WILLOW_LANE_2_ORIGIN.offset(WILLOW_LANE_2_INDOOR_EXIT_PORTAL_OFFSET);

        // 2号柳巷室外入口：2 高 x 2 宽。
        placePortalTriggerArea(level, WILLOW_LANE_2_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_WILLOW_LANE_2_OUTSIDE, "sdv_portal_target:2_willow_lane_enter");

        // 2号柳巷室内出口：2 高 x 1 宽。
        placePortalTriggerArea(level, willowLane2IndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_WILLOW_LANE_2_INSIDE, "sdv_portal_target:2_willow_lane_exit");

        BlockPos marnieRanchIndoorExitPortal = MARNIE_RANCH_ORIGIN.offset(MARNIE_RANCH_INDOOR_EXIT_PORTAL_OFFSET);

        // 马尼牧场室外入口：2高 x 2宽 x 1深。
        placePortalTriggerArea(level, MARNIE_RANCH_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_MARNIE_RANCH_OUTSIDE, "sdv_portal_target:marnie_ranch_enter");

        // 马尼牧场室内出口：2高 x 1宽。
        placePortalTriggerArea(level, marnieRanchIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_MARNIE_RANCH_INSIDE, "sdv_portal_target:marnie_ranch_exit");

        BlockPos leahCottageIndoorExitPortal = LEAH_COTTAGE_ORIGIN.offset(LEAH_COTTAGE_INDOOR_EXIT_PORTAL_OFFSET);

        // 莉亚小屋室外入口：2高 x 2宽 x 1深。
        placePortalTriggerArea(level, LEAH_COTTAGE_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_LEAH_COTTAGE_OUTSIDE, "sdv_portal_target:leah_cottage_enter");

        // 莉亚小屋室内出口：2高 x 1宽。
        placePortalTriggerArea(level, leahCottageIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_LEAH_COTTAGE_INSIDE, "sdv_portal_target:leah_cottage_exit");

        BlockPos adventurerGuildIndoorExitPortal = ADVENTURER_GUILD_ORIGIN.offset(ADVENTURER_GUILD_INDOOR_EXIT_PORTAL_OFFSET);

        // 冒险家公会室外入口：2高 x 3宽 x 1深。
        placePortalTriggerArea(level, ADVENTURER_GUILD_OUTDOOR_ENTRY_POS, 2, 1, 1,
            TAG_PORTAL_MARKER_ADVENTURER_GUILD_OUTSIDE, "sdv_portal_target:adventurer_guild_enter");

        // 冒险家公会室内出口：2高 x 1宽。
        placePortalTriggerArea(level, adventurerGuildIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_ADVENTURER_GUILD_INSIDE, "sdv_portal_target:adventurer_guild_exit");

        BlockPos fishShopIndoorExitPortal = FISH_SHOP_ORIGIN.offset(FISH_SHOP_INDOOR_EXIT_PORTAL_OFFSET);

        // 鱼店室外入口：2高 x 2宽 x 1深。
        placePortalTriggerArea(level, FISH_SHOP_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_FISH_SHOP_OUTSIDE, "sdv_portal_target:fish_shop_enter");

        // 鱼店室内出口：2高 x 1宽。
        placePortalTriggerArea(level, fishShopIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_FISH_SHOP_INSIDE, "sdv_portal_target:fish_shop_exit");

        BlockPos elliottCabinIndoorExitPortal = ELLIOTT_CABIN_ORIGIN.offset(ELLIOTT_CABIN_INDOOR_EXIT_PORTAL_OFFSET);

        // 艾利欧特小屋室外入口：2高 x 3宽 x 1深。
        placePortalTriggerArea(level, ELLIOTT_CABIN_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_ELLIOTT_CABIN_OUTSIDE, "sdv_portal_target:elliott_cabin_enter");

        // 艾利欧特小屋室内出口：2高 x 1宽。
        placePortalTriggerArea(level, elliottCabinIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_ELLIOTT_CABIN_INSIDE, "sdv_portal_target:elliott_cabin_exit");

        BlockPos wizardTowerIndoorExitPortal = WIZARD_TOWER_ORIGIN.offset(WIZARD_TOWER_INDOOR_EXIT_PORTAL_OFFSET);

        // 巫师塔室外入口：2高 x 1宽。
        placePortalTriggerArea(level, WIZARD_TOWER_OUTDOOR_ENTRY_POS, 2, 1, 1,
            TAG_PORTAL_MARKER_WIZARD_TOWER_OUTSIDE, "sdv_portal_target:wizard_tower_enter");

        // 巫师塔室内出口：2高 x 1宽。
        placePortalTriggerArea(level, wizardTowerIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_WIZARD_TOWER_INSIDE, "sdv_portal_target:wizard_tower_exit");

        // 巫师塔内部"回到主世界"：2高 x 3宽 x 3深。
        placePortalTriggerArea(level, WIZARD_TOWER_RETURN_OVERWORLD_BASE, 3, 3, 3,
            TAG_PORTAL_MARKER_WIZARD_TOWER_RETURN_OVERWORLD, "sdv_portal_target:wizard_tower_return_overworld");

        // 绿洲室内出口：2高 x 1宽 x 1深（室外入口由 DesertMapBootstrap 放置）
        BlockPos oasisIndoorExitPortal = OASIS_ORIGIN.offset(OASIS_INDOOR_EXIT_PORTAL_OFFSET);
        placePortalTriggerArea(level, oasisIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_OASIS_INSIDE, "sdv_portal_target:oasis_exit");

        // Joja 超市室外入口。
        placePortalTriggerArea(level, JOJA_MART_OUTDOOR_ENTRY_BASE, 2, 2, 1,
            TAG_PORTAL_MARKER_JOJA_MART_OUTSIDE, "sdv_portal_target:joja_mart_enter");

        // Joja 超市室内出口：2高 x 1宽 x 2深，Z+13~14
        BlockPos jojaMartIndoorExitPortal = JOJA_MART_ORIGIN.offset(JOJA_MART_INDOOR_EXIT_PORTAL_OFFSET);
        placePortalTriggerArea(level, jojaMartIndoorExitPortal, 3, 2, 1,
            TAG_PORTAL_MARKER_JOJA_MART_INSIDE, "sdv_portal_target:joja_mart_exit");

        placePortalTriggerArea(level, TRAILER_OUTDOOR_ENTRY_POS, 2, 2, 1,
            TAG_PORTAL_MARKER_TRAILER_OUTSIDE, "sdv_portal_target:trailer_enter");

        BlockPos trailerIndoorExitPortal = TRAILER_INDOOR_EXIT_PORTAL_OFFSET;
        placePortalTriggerArea(level, trailerIndoorExitPortal, 3, 3, 1,
            TAG_PORTAL_MARKER_TRAILER_INSIDE, "sdv_portal_target:trailer_exit");

        // 沙漠公交站传送区域：在空气位置放置触发方块（默认行为，跳过已有非空气方块）
        placePortalTriggerArea(level,
            com.stardew.craft.desert.DesertConstants.BUS_PORTAL_BASE,
            com.stardew.craft.desert.DesertConstants.BUS_PORTAL_H,
            com.stardew.craft.desert.DesertConstants.BUS_PORTAL_X,
            com.stardew.craft.desert.DesertConstants.BUS_PORTAL_Z,
            com.stardew.craft.desert.DesertConstants.TAG_BUS_PORTAL_MARKER,
            com.stardew.craft.desert.DesertConstants.TAG_BUS_PORTAL_TARGET);

        // 沙漠返程公交站：沙漠侧 2x2x1 区域，在空气位置放置触发方块
        placePortalTriggerArea(level,
            com.stardew.craft.desert.DesertConstants.BUS_RETURN_PORTAL_BASE,
            com.stardew.craft.desert.DesertConstants.BUS_RETURN_PORTAL_H,
            com.stardew.craft.desert.DesertConstants.BUS_RETURN_PORTAL_X,
            com.stardew.craft.desert.DesertConstants.BUS_RETURN_PORTAL_Z,
            com.stardew.craft.desert.DesertConstants.TAG_BUS_RETURN_PORTAL_MARKER,
            com.stardew.craft.desert.DesertConstants.TAG_BUS_RETURN_PORTAL_TARGET);

        // 矿井室外入口：3高 x 2宽 x 1深。
        placePortalTriggerArea(level, MINE_OUTDOOR_ENTRY_POS, 3, 2, 1,
            TAG_PORTAL_MARKER_MINE_OUTSIDE, "sdv_portal_target:mine_entrance");

        // 社区中心室外入口：2高 x 1宽 x 1深。
        placePortalTriggerArea(level, CC_OUTDOOR_INTERACTION_BASE, 2, 1, 1,
            TAG_PORTAL_MARKER_CC_OUTSIDE, "sdv_portal_target:community_center_enter");

        // CC 室内出口由 PlayerInteriorAllocator 在每位玩家的 CC 实例中动态放置

        // 温室室内出口由 PlayerInteriorAllocator 在每位玩家的温室实例中动态放置

        // 温室室外入口：仅在温室修复后才放置（由 GreenhouseManager.repair() 调用 spawnGreenhouseOutdoorPortal）
    }

    /**
    * 版本升级时为老存档的农场、温室、农场入口补放 PortalTriggerBlock。
     * <p>
     * 老存档中这些区域使用 Interaction 实体，升级后实体被
     * {@link com.stardew.craft.event.InteriorSubspaceLifecycleEvents#onEntityJoinLevel} 拦截取消加载，
     * 此方法负责在对应位置补放方块，确保传送门可用。
     */
    private static void migrateFarmAndGreenhousePortals(ServerLevel level) {
        // ── 1. 农场入口触发区：重置放置状态让其重新放置 ──
        com.stardew.craft.farm.FarmEntryBarrierManager barrierMgr =
                com.stardew.craft.farm.FarmEntryBarrierManager.get(level);
        barrierMgr.resetForMigration();
        barrierMgr.ensureBarriersPlaced(level);

        // ── 2. 所有已初始化农场的出口传送门 ──
        com.stardew.craft.farm.FarmInstanceRegistry registry =
                com.stardew.craft.farm.FarmInstanceRegistry.get();
        int farmPortals = 0;
        for (com.stardew.craft.farm.FarmInstance farm : registry.getAllFarms()) {
            if (!farm.isInitialized()) continue;
            com.stardew.craft.farm.FarmType.FarmLayout layout = farm.getFarmType().getLayout();
            if (layout == null) continue;

            BlockPos origin = farm.getOrigin();

            // 南出口
            placeExitRegion(level, origin, layout.entrySouth(),
                    "sdv_portal_target:farm_exit_south", "sdv_portal_marker:farm_exit");
            // 东出口
            placeExitRegion(level, origin, layout.entryEast(),
                    "sdv_portal_target:farm_exit_east", "sdv_portal_marker:farm_exit");
            // 西出口
            placeExitRegion(level, origin, layout.entryWest(),
                    "sdv_portal_target:farm_exit_west", "sdv_portal_marker:farm_exit");
            farmPortals++;
        }

        // ── 3. 已修复温室的室外入口传送门 ──
        com.stardew.craft.greenhouse.GreenhouseManager ghMgr =
                com.stardew.craft.greenhouse.GreenhouseManager.get(level);
        int ghPortals = 0;
        for (com.stardew.craft.farm.FarmInstance farm : registry.getAllFarms()) {
            if (!farm.isInitialized()) continue;
            if (!ghMgr.isRepairedForPlayer(farm.getOwnerUUID())) continue;

            // 温室入口位置 = 农场温室位置 + (8,0,0)（CW90 旋转后的门口偏移）
            BlockPos ghPos = farm.getGreenhousePos();
            BlockPos portalPos = ghPos.offset(8, 0, 0);
            spawnGreenhouseOutdoorPortalAt(level, portalPos);
            ghPortals++;
        }

        StardewCraft.LOGGER.info("[PORTAL_MIGRATION] Migrated portals: {} farm exit sets, {} greenhouse outdoor portals",
                farmPortals, ghPortals);
    }

    /**
     * 为单个农场出口方向放置 PortalTriggerBlock 区域。
     * 逻辑与 FarmInstanceInitializer.spawnExitEntityRegion 一致。
     */
    private static void placeExitRegion(ServerLevel level, BlockPos origin,
                                         com.stardew.craft.farm.FarmType.EntryData entry,
                                         String targetTag, String markerTag) {
        BlockPos min = origin.offset(entry.exitMin());
        BlockPos max = origin.offset(entry.exitMax());

        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());

        int xBlocks = maxX - minX + 1;
        int yBlocks = maxY - minY + 1;
        int zBlocks = maxZ - minZ + 1;

        placePortalTriggerArea(level, new BlockPos(minX, minY, minZ),
                yBlocks, xBlocks, zBlocks, markerTag, targetTag);
    }

    /**
     * 由 PlayerInteriorAllocator 等调用：在指定位置放置 PortalTriggerBlock 区域。
     */
    static void spawnInteractionArea(ServerLevel level, BlockPos basePos,
                                     int heightBlocks, int xBlocks, int zBlocks,
                                     String markerTag, String targetTag) {
        placePortalTriggerArea(level, basePos, heightBlocks, xBlocks, zBlocks, markerTag, targetTag);
    }

    /**
     * 在指定区域放置 PortalTriggerBlock（替代旧版 Interaction 实体）。
     * <p>
     * 如果目标区块尚未加载，会先 force-load 并延迟到下一 tick 重试，
     * 放置完成后自动释放 force-load，确保不阻塞主线程。
     * <p>
     * targetTag 格式："sdv_portal_target:xxx" — 自动提取 "xxx" 作为 BlockEntity 的 targetId。
     */
    public static void placePortalTriggerArea(ServerLevel level,
                                               BlockPos basePos,
                                               int heightBlocks,
                                               int xBlocks,
                                               int zBlocks,
                                               String markerTag,
                                               String targetTag) {
        placePortalTriggerAreaInternal(level, basePos, heightBlocks, xBlocks, zBlocks, markerTag, targetTag, false);
    }

    /**
     * 仅替换 <b>非空气</b> 方块位置的 PortalTriggerBlock（专用于沙漠公交站）。
     * <p>此区域内的空气方块保持不变，只有已存在的实体方块（站牌、地砖等）被替换成触发方块，
     * 玩家只会在踩到/撞到这些非空气方块时触发，而不会把整个体积都填成触发方块。
     */
    public static void placePortalTriggerAreaSolidOnly(ServerLevel level,
                                                      BlockPos basePos,
                                                      int heightBlocks,
                                                      int xBlocks,
                                                      int zBlocks,
                                                      String markerTag,
                                                      String targetTag) {
        placePortalTriggerAreaInternal(level, basePos, heightBlocks, xBlocks, zBlocks, markerTag, targetTag, true);
    }

    private static void removePortalTriggerIfPresent(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get())) {
            level.removeBlock(pos, false);
        }
    }

    private static void placePortalTriggerAreaInternal(ServerLevel level,
                                               BlockPos basePos,
                                               int heightBlocks,
                                               int xBlocks,
                                               int zBlocks,
                                               String markerTag,
                                               String targetTag,
                                               boolean solidOnly) {
        // ── 注册到自修复注册表 ──
        PORTAL_REGISTRY.put(portalKey(level.dimension(), basePos),
                new PortalPlacement(level.dimension(), basePos, heightBlocks, xBlocks, zBlocks, markerTag, targetTag, solidOnly));

        // ── 区块预加载检查 ──
        java.util.Set<Long> neededChunks = new java.util.HashSet<>();
        for (int dx = 0; dx < xBlocks; dx++) {
            for (int dz = 0; dz < zBlocks; dz++) {
                BlockPos pos = basePos.offset(dx, 0, dz);
                neededChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            }
        }

        boolean allLoaded = true;
        for (long chunkKey : neededChunks) {
            int cx = ChunkPos.getX(chunkKey);
            int cz = ChunkPos.getZ(chunkKey);
            if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                allLoaded = false;
                level.setChunkForced(cx, cz, true);
            }
        }
        if (!allLoaded) {
            // 区块尚未加载，延迟到下一 tick 重试
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 1,
                () -> placePortalTriggerAreaInternal(level, basePos, heightBlocks, xBlocks, zBlocks, markerTag, targetTag, solidOnly)
            ));
            StardewCraft.LOGGER.info("[INTERIOR] Deferred portal trigger '{}' at {} — waiting for chunks",
                    markerTag, basePos);
            return;
        }

        // 释放 force-load（方块是持久化数据，放完即可释放）
        for (long chunkKey : neededChunks) {
            int cx = ChunkPos.getX(chunkKey);
            int cz = ChunkPos.getZ(chunkKey);
            level.setChunkForced(cx, cz, false);
        }

        // ── 放置方块 ──
        String targetId = targetTag;
        if (targetTag.startsWith("sdv_portal_target:")) {
            targetId = targetTag.substring("sdv_portal_target:".length());
        }

        int placed = 0;
        for (int dx = 0; dx < xBlocks; dx++) {
            for (int dz = 0; dz < zBlocks; dz++) {
                for (int dy = 0; dy < heightBlocks; dy++) {
                    BlockPos pos = basePos.offset(dx, dy, dz);
                    // solidOnly 模式下，只替换非空气方块（沙漠公交站）
                    if (solidOnly && level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos,
                            com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_ALL);
                    if (level.getBlockEntity(pos) instanceof
                            com.stardew.craft.blockentity.PortalTriggerBlockEntity be) {
                        be.configure(targetId, markerTag);
                    }
                    placed++;
                }
            }
        }
        StardewCraft.LOGGER.info("[INTERIOR] Portal trigger area '{}': base={}, x={} z={} h={}, placed={}, solidOnly={}",
                markerTag, basePos, xBlocks, zBlocks, heightBlocks, placed, solidOnly);
    }

    /**
     * 在温室修复后，在农场维度放置室外入口交互实体。
     * 由 GreenhouseManager.repair() 调用。
     */
    public static void spawnGreenhouseOutdoorPortal(ServerLevel level) {
        spawnGreenhouseOutdoorPortalAt(level, com.stardew.craft.greenhouse.GreenhouseManager.OUTDOOR_INTERACTION_BASE);
    }

    /**
     * 在指定位置放置温室室外入口传送触发方块（用于多人农场每玩家温室）。
     * @param pos 温室外观的入口传送触发方块位置
     */
    public static void spawnGreenhouseOutdoorPortalAt(ServerLevel level, BlockPos pos) {
        placePortalTriggerArea(
            level,
            pos,
            2,       // heightBlocks
            1,       // xBlocks
            1,       // zBlocks
            TAG_PORTAL_MARKER_GREENHOUSE_OUTSIDE,
            "sdv_portal_target:greenhouse_enter"
        );
        StardewCraft.LOGGER.info("[INTERIOR] Greenhouse outdoor portal placed at {}", pos);
    }

    /**
     * 在农场指定立方体区域放置「进洞」传送触发方块。由 {@link com.stardew.craft.farm.FarmInstanceInitializer} 调用。
     * 坐标为绝对世界坐标（min/max 均包含）。
     */
    public static void spawnFarmCaveOutdoorPortalArea(ServerLevel level, BlockPos min, BlockPos max) {
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        int xBlocks = maxX - minX + 1;
        int yBlocks = maxY - minY + 1;
        int zBlocks = maxZ - minZ + 1;
        placePortalTriggerArea(
            level,
            new BlockPos(minX, minY, minZ),
            yBlocks, xBlocks, zBlocks,
            TAG_PORTAL_MARKER_FARM_CAVE_OUTSIDE,
            "sdv_portal_target:farm_cave_enter"
        );
        StardewCraft.LOGGER.info("[INTERIOR] Farm cave outdoor portal placed at {}~{}", min, max);
    }

    private static final class InteriorSubspaceSavedData extends SavedData {
        private int layoutVersion = 0;
        private int portalTriggerVersion = 0;
        private boolean initialized = false;

        static InteriorSubspaceSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                    InteriorSubspaceSavedData::new,
                    InteriorSubspaceSavedData::load
                ),
                DATA_NAME
            );
        }

        static InteriorSubspaceSavedData load(net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider provider) {
            InteriorSubspaceSavedData data = new InteriorSubspaceSavedData();
            data.layoutVersion = tag.getInt("layoutVersion");
            data.portalTriggerVersion = tag.getInt("portalTriggerVersion");
            data.initialized = tag.getBoolean("initialized");
            return data;
        }

        @Override
        public @Nonnull net.minecraft.nbt.CompoundTag save(@Nonnull net.minecraft.nbt.CompoundTag tag,
                                                           @Nonnull HolderLookup.Provider provider) {
            tag.putInt("layoutVersion", layoutVersion);
            tag.putInt("portalTriggerVersion", portalTriggerVersion);
            tag.putBoolean("initialized", initialized);
            return tag;
        }
    }
}
