package com.stardew.craft.core;

import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceAllocator;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 农场区域动态判定工具。
 * 支持多农场实例的坐标判断和归属查询。
 * 玩家农场位于 Z>=20001 的远坐标网格中。
 */
public final class FarmAreaResolver {

    private FarmAreaResolver() {}

    // ════════════════════════════════════════════════════════
    //  核心：判断坐标是否在任何玩家农场内
    // ════════════════════════════════════════════════════════

    /**
     * 快速判断：坐标是否在任何玩家的农场区域内。
     * 用于放置/破坏保护、睡觉限制等通用判断。
     */
    public static boolean isInAnyFarm(Level level, BlockPos pos) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) return false;

        if (!FarmInstanceAllocator.isInFarmInstanceRegion(pos)) return false;

        // 通过网格数学确认该槽位确实有人拥有
        return getOwnerAt(pos) != null;
    }

    /**
     * 判断坐标是否在某个特定玩家的农场内。
     */
    public static boolean isInPlayerFarm(UUID playerUUID, BlockPos pos) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarmForPlayer(playerUUID);
        return farm != null && farm.contains(pos);
    }

    /**
     * 坐标反查归属玩家 — O(1) 网格计算，不需要遍历所有农场。
     * @return 该坐标所在农场的拥有者 UUID，不在任何农场则返回 null
     */
    @Nullable
    public static UUID getOwnerAt(BlockPos pos) {
        if (!FarmInstanceAllocator.isInFarmInstanceRegion(pos)) return null;
        int slotIndex = FarmInstanceAllocator.getSlotIndexAt(pos);
        if (slotIndex < 0) return null;
        return FarmInstanceRegistry.get().getOwnerBySlot(slotIndex);
    }

    // ════════════════════════════════════════════════════════
    //  通用区域判定
    // ════════════════════════════════════════════════════════

    /**
     * 判断位置是否在已登记的玩家农场实例中。
     */
    public static boolean isInFarmArea(Level level, BlockPos pos) {
        return isInAnyFarm(level, pos);
    }

    /**
     * 在星露谷维度但不在任何农场区域内。
     */
    public static boolean isInStardewButNotFarm(Level level, BlockPos pos) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) return false;
        return !isInAnyFarm(level, pos);
    }

    /**
     * 获取坐标对应的农场实例（如果有的话）。
     */
    @Nullable
    public static FarmInstance getFarmAt(BlockPos pos) {
        UUID owner = getOwnerAt(pos);
        if (owner == null) return null;
        return FarmInstanceRegistry.get().getFarm(owner);
    }
}
