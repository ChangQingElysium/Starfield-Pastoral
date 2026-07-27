package com.stardew.craft.farm;

import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import net.minecraft.core.BlockPos;

/**
 * 农场实例的网格分配器。
 * 在 Stardew Valley 维度的远坐标区域（Z >= 20001）为每个玩家分配独立的农场槽位。
 * 每个槽位 512×512 格，最大容纳 20×∞ 个农场。
 */
public final class FarmInstanceAllocator {

    private FarmInstanceAllocator() {}

    /** 农场实例区域起始坐标 */
    public static final int FARM_REGION_START = 20001;
    /** 每个农场槽位大小 */
    public static final int FARM_SLOT_SIZE = 512;
    /** 每行农场数量 */
    public static final int FARMS_PER_ROW = 20;

    /**
     * 根据槽位序号和农场类型计算农场原点坐标。
     * 原点 = schem 放置位置（min corner），Y 由农场类型决定。
     *
     * @param slotIndex 从 0 开始的分配序号
     * @param farmType  农场类型（决定 Y 坐标）
     * @return 农场原点坐标（schematic 放置的 min corner）
     */
    public static BlockPos getFarmOrigin(int slotIndex, FarmType farmType) {
        return getFarmOrigin(slotIndex,
                com.stardew.craft.api.v1.farm.StardewFarmLayouts.find(
                        com.stardew.craft.api.v1.internal.farm
                                .StardewFarmLayoutRegistry.builtinId(farmType))
                        .orElseThrow());
    }

    public static BlockPos getFarmOrigin(
            int slotIndex,
            StardewFarmLayout layout
    ) {
        int col = slotIndex % FARMS_PER_ROW;
        int row = slotIndex / FARMS_PER_ROW;
        int x = FARM_REGION_START + col * FARM_SLOT_SIZE;
        int z = FARM_REGION_START + row * FARM_SLOT_SIZE;
        int y = layout.originY();
        return new BlockPos(x, y, z);
    }

    /**
     * 向后兼容：无类型时使用默认 Y=0。
     */
    public static BlockPos getFarmOrigin(int slotIndex) {
        return getFarmOrigin(slotIndex, FarmType.STANDARD);
    }

    /**
     * 根据世界坐标反查槽位序号。O(1) 计算。
     * @return 槽位序号，如果不在农场区域则返回 -1
     */
    public static int getSlotIndexAt(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        if (x < FARM_REGION_START || z < FARM_REGION_START) return -1;

        int col = (x - FARM_REGION_START) / FARM_SLOT_SIZE;
        int row = (z - FARM_REGION_START) / FARM_SLOT_SIZE;
        if (col < 0 || col >= FARMS_PER_ROW) return -1;

        return row * FARMS_PER_ROW + col;
    }

    /**
     * 判断某坐标是否在任何可能的农场实例区域内。
     */
    public static boolean isInFarmInstanceRegion(BlockPos pos) {
        return pos.getX() >= FARM_REGION_START && pos.getZ() >= FARM_REGION_START;
    }
}
