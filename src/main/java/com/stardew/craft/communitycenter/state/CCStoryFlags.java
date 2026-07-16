package com.stardew.craft.communitycenter.state;

import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 社区中心故事进度标记。
 * 使用 {@link PlayerStardewData#hasMailFlag} / {@link PlayerStardewData#addMailFlag} 持久化。
 */
public final class CCStoryFlags {

    private CCStoryFlags() {}

    // ── 主线进度 ──
    /** CC 大门已解锁（Lewis 带路后设置） */
    public static final String CC_DOOR_UNLOCKED   = "ccDoorUnlock";
    /** 首次看到 JunimoNote（触发巫师邀请信） */
    public static final String SEEN_JUNIMO_NOTE    = "seenJunimoNote";
    /** 能读懂 Junimo 文字（巫师解锁后设置） */
    public static final String CAN_READ_JUNIMO     = "canReadJunimoText";
    /** 全部区域完成 */
    public static final String CC_IS_COMPLETE      = "ccIsComplete";
    /** 玩家已获得骷髅钥匙（SDV mailReceived "HasSkullKey"）——骷髅矿洞通行证 */
    public static final String HAS_SKULL_KEY       = "HasSkullKey";
    /** SDV specialItems 钱包/去重项：骷髅钥匙 */
    public static final String SKULL_KEY_SPECIAL_ITEM = "stardewcraft:skull_key";

    // ── 巫师邀请信 ──
    /** 巫师邀请信邮件 ID */
    public static final String WIZARD_JUNIMO_NOTE  = "wizardJunimoNote";

    // ── 区域完成标记（与 CommunityCenterProgress.getAreaRewardMailFlag() 一致） ──
    public static final String CC_PANTRY      = "ccPantry";
    public static final String CC_CRAFTS_ROOM = "ccCraftsRoom";
    public static final String CC_FISH_TANK   = "ccFishTank";
    public static final String CC_BOILER_ROOM = "ccBoilerRoom";
    public static final String CC_VAULT       = "ccVault";
    public static final String CC_BULLETIN    = "ccBulletin";

    /** 根据 areaId 获取区域完成标记 */
    public static String areaFlag(int areaId) {
        return switch (areaId) {
            case 0 -> CC_PANTRY;
            case 1 -> CC_CRAFTS_ROOM;
            case 2 -> CC_FISH_TANK;
            case 3 -> CC_BOILER_ROOM;
            case 4 -> CC_VAULT;
            case 5 -> CC_BULLETIN;
            default -> "";
        };
    }

    // ── Joja 线 ──
    /** 已支付 5000g 入会 Joja 超市（SDV mailReceived "JojaMember"） */
    public static final String JOJA_MEMBER          = "JojaMember";
    /** 已见过 Morris 首次问候（SDV mailReceived "JojaGreeting"） */
    public static final String JOJA_GREETING        = "JojaGreeting";
    /** 已购买 Vault（矿车铁轨修复） */
    public static final String JOJA_VAULT           = "jojaVault";
    /** 已购买 Boiler Room（矿车系统） */
    public static final String JOJA_BOILER_ROOM     = "jojaBoilerRoom";
    /** 已购买 Crafts Room（山区断桥） */
    public static final String JOJA_CRAFTS_ROOM     = "jojaCraftsRoom";
    /** 已购买 Pantry（老农场 → 温室） */
    public static final String JOJA_PANTRY          = "jojaPantry";
    /** 已购买 Fish Tank（矿洞入口炸石） */
    public static final String JOJA_FISH_TANK       = "jojaFishTank";
    /** 已购买电影院（ccMovieTheater + ccMovieTheaterJoja） */
    public static final String CC_MOVIE_THEATER     = "ccMovieTheater";
    public static final String CC_MOVIE_THEATER_JOJA = "ccMovieTheaterJoja";

    /** CD form 5 格按钮对应的 joja flag */
    public static String jojaAreaFlag(int buttonIdx) {
        return switch (buttonIdx) {
            case 0 -> JOJA_VAULT;
            case 1 -> JOJA_BOILER_ROOM;
            case 2 -> JOJA_CRAFTS_ROOM;
            case 3 -> JOJA_PANTRY;
            case 4 -> JOJA_FISH_TANK;
            default -> "";
        };
    }

    /** CD form 按钮对应的 CC 区域 mail flag（Joja 购买会同时寄出两封，这里返回 cc* 那一封） */
    public static String jojaButtonToCcFlag(int buttonIdx) {
        return switch (buttonIdx) {
            case 0 -> CC_VAULT;
            case 1 -> CC_BOILER_ROOM;
            case 2 -> CC_CRAFTS_ROOM;
            case 3 -> CC_PANTRY;
            case 4 -> CC_FISH_TANK;
            default -> "";
        };
    }

    public static boolean isJojaMember(ServerPlayer player) {
        return hasFlag(player, JOJA_MEMBER);
    }

    // ── 便捷查询 ──

    public static boolean hasFlag(ServerPlayer player, String flag) {
        return PlayerDataManager.getPlayerData(player).hasMailFlag(flag);
    }

    /**
     * Checks a world-level story unlock backed by any persisted player's progress.
     * Global NPC schedules must not depend on which player happens to be online first.
     */
    public static boolean anyPlayerHasFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return false;
        }
        return PlayerDataManager.get().getAllPlayerData().values().stream()
                .anyMatch(data -> data.hasMailFlag(flag));
    }

    public static void addFlag(ServerPlayer player, String flag) {
        PlayerDataManager.getPlayerData(player).addMailFlag(flag);
    }

    public static boolean canReadJunimoText(ServerPlayer player) {
        return hasFlag(player, CAN_READ_JUNIMO);
    }

    public static boolean hasSeenJunimoNote(ServerPlayer player) {
        return hasFlag(player, SEEN_JUNIMO_NOTE);
    }
}
