package com.stardew.craft.desert;

import net.minecraft.core.BlockPos;

/**
 * 沙漠区域的所有固定坐标常量。
 */
public final class DesertConstants {

    private DesertConstants() {}

    // ── pregen 主地图内嵌沙漠使用世界绝对坐标；保留 origin=0 兼容 worldPos 调用 ──
    public static final BlockPos DESERT_ORIGIN = BlockPos.ZERO;

    // ── 玩家到达点（公交站到达）──
    public static final BlockPos ARRIVAL_OFFSET = new BlockPos(-225, 64, -177);

    // ── 骷髅矿出口到达点 ──
    public static final BlockPos SKULL_CAVERN_EXIT_OFFSET = new BlockPos(-246, 64, -213);

    // ── 矿井入口传送方块 ──
    public static final BlockPos MINE_PORTAL_OFFSET = new BlockPos(-247, 64, -215);
    public static final int MINE_PORTAL_X = 2;
    public static final int MINE_PORTAL_H = 2;
    public static final int MINE_PORTAL_Z = 1;

    // ── Oasis 入口传送方块 ──
    public static final BlockPos OASIS_PORTAL_OFFSET = new BlockPos(-251, 64, -142);
    public static final int OASIS_PORTAL_X = 1;
    public static final int OASIS_PORTAL_H = 2;
    public static final int OASIS_PORTAL_Z = 1;

    // ── 公交站牌传送区域（主世界一侧） ──
    public static final BlockPos BUS_PORTAL_BASE = new BlockPos(-66, 64, -60);
    public static final int BUS_PORTAL_X = 2;
    public static final int BUS_PORTAL_H = 2;
    public static final int BUS_PORTAL_Z = 2;
    public static final String TAG_BUS_PORTAL_MARKER = "sdv_portal_marker:desert_bus";
    public static final String TAG_BUS_PORTAL_TARGET = "sdv_portal_target:desert_bus";

    // ── 返程公交站牌传送区域（沙漠一侧） ──
    public static final BlockPos BUS_RETURN_PORTAL_BASE = new BlockPos(-225, 65, -179);
    public static final int BUS_RETURN_PORTAL_X = 1;
    public static final int BUS_RETURN_PORTAL_H = 2;
    public static final int BUS_RETURN_PORTAL_Z = 1;
    public static final String TAG_BUS_RETURN_PORTAL_MARKER = "sdv_portal_marker:desert_bus_return";
    public static final String TAG_BUS_RETURN_PORTAL_TARGET = "sdv_portal_target:desert_bus_return";
    /** 返程到达点。 */
    public static final BlockPos TOWN_RETURN_ARRIVAL = new BlockPos(-60, 64, -61);
    public static final float TOWN_RETURN_YAW = 0.0F;

    // ── 公交票价 ──
    public static final int BUS_TICKET_PRICE = 500;

    // ── Oasis Interior ──
    public static final String OASIS_INTERIOR_PATH = "data/stardewcraft/structures/interior/oasis_interior.schem";
    public static final BlockPos OASIS_INTERIOR_ORIGIN = BlockPos.ZERO;
    public static final BlockPos OASIS_INDOOR_SPAWN_OFFSET = new BlockPos(-252, 30, -147);
    public static final BlockPos OASIS_INDOOR_EXIT_PORTAL_OFFSET = new BlockPos(-253, 30, -146);
    public static final int OASIS_EXIT_PORTAL_X = 3;
    public static final int OASIS_EXIT_PORTAL_H = 3;
    public static final int OASIS_EXIT_PORTAL_Z = 1;

    // ── 门户标识 ──
    public static final String TAG_MINE_PORTAL_MARKER = "sdv_portal_marker:desert_mine_entrance";
    public static final String TAG_MINE_PORTAL_TARGET = "sdv_portal_target:desert_mine_enter";
    public static final String TAG_OASIS_PORTAL_MARKER_OUTSIDE = "sdv_portal_marker:oasis_outside";
    public static final String TAG_OASIS_PORTAL_TARGET_ENTER = "sdv_portal_target:oasis_enter";
    public static final String TAG_OASIS_PORTAL_MARKER_INSIDE = "sdv_portal_marker:oasis_inside";
    public static final String TAG_OASIS_PORTAL_TARGET_EXIT = "sdv_portal_target:oasis_exit";

    // ── 沙漠 schem 资源路径 ──
    public static final String DESERT_SCHEM_PATH = "data/stardewcraft/structures/desert.schem";

    /** 坐标已是世界绝对坐标；该方法保留给现有调用点。 */
    public static BlockPos worldPos(BlockPos offset) {
        return DESERT_ORIGIN.offset(offset);
    }

    // ── pregen 主地图内嵌沙漠包围盒（世界坐标，含两端，供运行时区域判定使用） ──
    public static final int DESERT_BBOX_MIN_X = -310;
    public static final int DESERT_BBOX_MAX_X = -158;
    public static final int DESERT_BBOX_MIN_Z = -241;
    public static final int DESERT_BBOX_MAX_Z = -113;

    /** 判断给定 XZ 坐标是否在沙漠包围盒内（不考虑 Y）。 */
    public static boolean isInDesertRegion(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                           BlockPos pos) {
        return com.stardew.craft.core.ModDimensions.STARDEW_VALLEY.equals(dimension) && isInDesertRegion(pos);
    }

    /** Retained solely to recognize and remove old saved bus triggers. */
    public static boolean isLegacyBusTarget(String target) {
        return "desert_bus".equals(target) || "desert_bus_return".equals(target)
                || TAG_BUS_PORTAL_TARGET.equals(target) || TAG_BUS_RETURN_PORTAL_TARGET.equals(target);
    }

    public static boolean isInDesertRegion(int x, int z) {
        return x >= DESERT_BBOX_MIN_X && x <= DESERT_BBOX_MAX_X
                && z >= DESERT_BBOX_MIN_Z && z <= DESERT_BBOX_MAX_Z;
    }

    /** 判断给定 BlockPos 是否在沙漠 schem 包围盒内（不考虑 Y）。 */
    public static boolean isInDesertRegion(BlockPos pos) {
        return isInDesertRegion(pos.getX(), pos.getZ());
    }
}
