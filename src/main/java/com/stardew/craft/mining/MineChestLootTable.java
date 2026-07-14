package com.stardew.craft.mining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * 矿井宝箱奖励表 — 定义每个特殊层数的一次性奖励物品。
 * 对照 SDV MineShaft.addLevelChests()。
 */
public final class MineChestLootTable {

    private MineChestLootTable() {}

    /** 该层是否应生成宝箱 */
    public static boolean isChestFloor(int floor) {
        return MineChestRewardData.isRewardFloor(floor) || isSkullCavernTreasureFloor(floor);
    }

    /** 该层是否为骷髅矿井宝藏室（奖励来自 {@link SkullCavernTreasurePool}，而非静态表）。 */
    public static boolean isSkullCavernTreasureFloor(int floor) {
        return floor == 220 || floor == 320 || floor == 420;
    }

    /** 该层的骷髅矿井宝箱数量（SDV：220→1，320→2，420→3）。 */
    public static int getSkullCavernChestCount(int floor) {
        return switch (floor) {
            case 220 -> 1;
            case 320 -> 2;
            case 420 -> 3;
            default -> 0;
        };
    }

    /**
     * 获取指定层数的奖励物品。
     * @return 奖励 ItemStack，非宝箱层或骷髅矿井宝藏室返回 null
     *         （骷髅矿井宝藏室请调用 {@link SkullCavernTreasurePool#roll}）
     */
    @Nullable
    @SuppressWarnings("null")
    public static ItemStack getRewardForFloor(
            ServerLevel level,
            @Nullable ServerPlayer player,
            int floor,
            Random random
    ) {
        return MineChestRewardData.resolve(level, player, floor, random).orElse(null);
    }

    /** 物品放在箱子第二行正中间 (slot index 13 = row 1, col 4) */
    public static final int REWARD_SLOT = 13;
}
