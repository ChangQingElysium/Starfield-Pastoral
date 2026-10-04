package com.stardew.craft.mastery;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;

import java.util.List;

/**
 * 雕像类 buff 生命周期管理 — SDV `Duration=-2` 语义：持续到次日清晨。
 * 玩家睡觉时调用 {@link #clearAllDailyMasteryBuffs} 把这 12 个 buff 全部移除。
 */
public final class MasteryBuffLifecycle {

    private MasteryBuffLifecycle() {}

    /** 7 个 Statue of Blessings buff + 5 个 Dwarf Statue buff —— 都在每日清晨清零。 */
    private static final List<Holder<MobEffect>> DAILY_MASTERY_BUFFS = List.of(
        ModMobEffects.STATUE_OF_BLESSINGS_0,
        ModMobEffects.STATUE_OF_BLESSINGS_1,
        ModMobEffects.STATUE_OF_BLESSINGS_2,
        ModMobEffects.STATUE_OF_BLESSINGS_3,
        ModMobEffects.STATUE_OF_BLESSINGS_4,
        ModMobEffects.STATUE_OF_BLESSINGS_5,
        ModMobEffects.STATUE_OF_BLESSINGS_6,
        ModMobEffects.DWARF_STATUE_0,
        ModMobEffects.DWARF_STATUE_1,
        ModMobEffects.DWARF_STATUE_2,
        ModMobEffects.DWARF_STATUE_3,
        ModMobEffects.DWARF_STATUE_4
    );

    public static void clearAllDailyMasteryBuffs(ServerPlayer player) {
        if (player == null) return;
        for (Holder<MobEffect> b : DAILY_MASTERY_BUFFS) {
            player.removeEffect(b);
        }
        PrismaticButterflyService.clearFor(player);
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data != null) {
            data.setBlessedByStatueToday(false);
            data.setBlessingOfWatersRemaining(0);
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    /**
     * 睡觉时清除全部 Buff（原版 Farmer.ClearBuffs → BuffManager.Clear：食物、饮料、祝福、减益一并移除），
     * 并清空由食物 / 药水写入的临时属性加成；随后执行每日雕像 Buff 的状态复位。
     */
    public static void clearAllBuffs(ServerPlayer player) {
        if (player == null) return;
        for (var entry : ModMobEffects.MOB_EFFECTS.getEntries()) {
            player.removeEffect(entry);
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data != null) {
            data.clearTempFishingLevelBonus();
            data.clearTempLuckBonus();
            data.clearTempMaxEnergyBonus();
            data.clearTempFarmingLevelBonus();
            data.clearTempForagingLevelBonus();
            data.clearTempMiningLevelBonus();
            data.clearTempAttackBonus();
            data.clearTempDefenseBonus();
            data.clearTempMagneticRadiusBonus();
        }
        clearAllDailyMasteryBuffs(player);
    }

    /** 是否任意 statue_of_blessings_* / dwarf_statue_* 在身上（用于消息提示等）。 */
    public static boolean hasAnyMasteryBuff(ServerPlayer player) {
        if (player == null) return false;
        for (Holder<MobEffect> b : DAILY_MASTERY_BUFFS) {
            if (player.hasEffect(b)) return true;
        }
        return false;
    }
}
