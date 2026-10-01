package com.stardew.craft.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * 模组自定义 GameRules。
 * 用 /gamerule 命令设置，与原版 gamerule 体验一致。
 */
public final class ModGameRules {
    public static final int DEFAULT_MAX_FARMERS_PER_FARM = 4;

    /** Allows building and interacting in public areas while preserving private farm permissions. */
    public static final GameRules.Key<GameRules.BooleanValue> RULE_STARDEW_ALLOW_PUBLIC_BUILDING =
            GameRules.register("stardewAllowPublicBuilding", GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(false, (server, value) -> {
                        if (server != null) {
                            com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                                    new com.stardew.craft.network.payload.PublicBuildingRuleSyncPayload(value.get()));
                        }
                    }));

    /**
     * 星露谷维度中需要多少百分比的玩家睡觉才能过夜。
     * 默认 100（全员制），设为 0 则一人即可过夜。
     * 与原版 playersSleepingPercentage 类似。
     */
    public static final GameRules.Key<GameRules.IntegerValue> RULE_STARDEW_SLEEPING_PERCENTAGE =
            GameRules.register("stardewSleepingPercentage",
                    GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(100));

    /**
     * 挂机超过多少秒的玩家在睡眠投票中被视为 AFK（不计入分母）。
     * 默认 300 秒（5 分钟）。设为 0 则禁用 AFK 检测。
     * "挂机"定义：没有移动、旋转视角、放置/破坏方块、交互、攻击。
     */
    public static final GameRules.Key<GameRules.IntegerValue> RULE_STARDEW_AFK_TIMEOUT =
            GameRules.register("stardewAfkTimeout",
                    GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(300));

    /**
     * 玩家离线期间季节变化后，上线时作物获得的宽限天数。
     * 宽限期内，过季作物不会枯死，仍可正常生长和收获。
     * 默认 3 天。设为 0 则禁用宽限（过季立即枯死）。
     */
    public static final GameRules.Key<GameRules.IntegerValue> RULE_CROP_GRACE_PERIOD_DAYS =
            GameRules.register("stardewCropGracePeriodDays",
                    GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(3));

    /**
     * 每个农场最多允许多少名玩家（owner + members）。
     * 默认 4，对齐星露谷常规多人上限。小于 1 的设置会按 1 处理。
     */
    public static final GameRules.Key<GameRules.IntegerValue> RULE_STARDEW_MAX_FARMERS_PER_FARM =
            GameRules.register("stardewMaxFarmersPerFarm",
                    GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(DEFAULT_MAX_FARMERS_PER_FARM));

    private ModGameRules() {}

    public static int getMaxFarmersPerFarm(MinecraftServer server) {
        if (server == null) {
            return DEFAULT_MAX_FARMERS_PER_FARM;
        }
        return Math.max(1, server.getGameRules().getInt(RULE_STARDEW_MAX_FARMERS_PER_FARM));
    }

    /** 在 mod 初始化时调用，触发静态字段注册 */
    public static void init() {
        // 静态字段在类加载时注册
    }
}
