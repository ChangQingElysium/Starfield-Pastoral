package com.stardew.craft.mining;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 梯子生成概率计算器
 * 
 * 按照星露谷原版公式计算梯子出现概率：
 * p = 0.02 + 1/max(1, stonesLeft) + luckLevel/100 + dailyLuck/5
 * 
 * 额外加成：
 * - 如果本层敌人数为0：+0.04
 * - 特殊buff：×1.25
 * - 如果stonesLeft==0：必出
 */
public class LadderProbabilityCalculator {
    
    /**
     * 计算梯子生成概率
     * 
     * @param stonesLeft 本层剩余石头数
     * @param luckLevel 玩家幸运等级（暂未实现，默认0）
     * @param dailyLuck 每日幸运值（-0.1 到 0.1）
     * @param enemyCount 本层敌人数量
     * @param hasDwarfBuff 是否有矮人雕像buff
     * @return 生成概率（0.0-1.0）
     */
    public static double calculateProbability(
        int stonesLeft,
        int luckLevel,
        double dailyLuck,
        int enemyCount,
        boolean hasDwarfBuff
    ) {
        // 如果没有石头了，必出
        if (stonesLeft <= 0) {
            return 1.0;
        }
        
        // SDV MineShaft base chance: 0.02 + remaining-stone and luck modifiers.
        double baseProbability = 0.02D;
        baseProbability += 1.0 / Math.max(1, stonesLeft);
        baseProbability += luckLevel / 100.0;
        baseProbability += dailyLuck / 5.0;
        
        // 如果本层没有敌人了，额外+0.04
        if (enemyCount == 0) {
            baseProbability += 0.04;
        }
        
        // SDV MineShaft.cs:3630 — dwarfStatue_1: chanceForLadderDown *= 1.25
        if (hasDwarfBuff) {
            baseProbability *= 1.25;
        }
        
        // 确保概率在0-1之间
        return Math.max(0.0, Math.min(1.0, baseProbability));
    }
    
    /**
     * 判断是否生成梯子
     * 
     * @param stonesLeft 本层剩余石头数
     * @param player 玩家（用于获取幸运值）
     * @param floorData 楼层数据
     * @param random 随机源
     * @return 是否应该生成梯子
     */
    public static boolean shouldGenerateLadder(
        int stonesLeft,
        ServerPlayer player,
        MineFloorData floorData,
        RandomSource random
    ) {
        // 如果本层已经有梯子了，不再生成
        if (floorData.hasLadderFound()) {
            return false;
        }
        
        // 获取玩家数据
        int luckLevel = PlayerStardewDataAPI.getLuckBuffLevel(player);
        double dailyLuck = getDailyLuck(player);
        boolean hasDwarfBuff = player.hasEffect(ModMobEffects.DWARF_STATUE_1);
        
        // 计算概率
        double probability = calculateProbability(
            stonesLeft,
            luckLevel,
            dailyLuck,
            floorData.getEnemyCount(),
            hasDwarfBuff
        );
        
        // 随机判断
        return random.nextDouble() < probability;
    }
    
    /**
     * 获取玩家的每日幸运值
     * TODO: 从PlayerStardewDataAPI获取
     */
    private static double getDailyLuck(ServerPlayer player) {
        return PlayerStardewDataAPI.getDailyLuck(player);
    }
}
