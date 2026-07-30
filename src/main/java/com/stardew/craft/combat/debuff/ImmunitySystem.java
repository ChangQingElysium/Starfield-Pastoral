package com.stardew.craft.combat.debuff;

import java.util.Random;

/**
 * 免疫系统
 * 
 * 星露谷物语免疫机制：
 * - 数值 immunity 决定能否直接抵抗一次 debuff 施加
 * - 原版判定等价于 random.nextInt(11) < immunity
 * - sturdy 类效果单独负责把负面状态持续时间减半
 */
public class ImmunitySystem {
    
    private static final Random RANDOM = new Random();
    
    private static final int IMMUNITY_ROLL_BOUND = 11;
    
    /**
     * 计算是否抵抗debuff
     * 
     * @param immunity 玩家的免疫值
     * @param debuffType debuff类型
     * @return true如果成功抵抗
     */
    public static boolean tryResistDebuff(int immunity, DebuffType debuffType) {
        // 某些debuff无法通过免疫减少（如冻结、眩晕）
        if (!debuffType.canReduce()) {
            return false;
        }

        return tryResistEffect(immunity);
    }

    /**
     * 按 SDV 风格判定一次负面状态是否被免疫抵抗。
     */
    public static boolean tryResistEffect(int immunity) {
        if (immunity <= 0) {
            return false;
        }
        if (immunity >= IMMUNITY_ROLL_BOUND) {
            return true;
        }
        return resistsWithRoll(immunity, RANDOM.nextInt(IMMUNITY_ROLL_BOUND));
    }

    /**
     * Deterministic form of the Stardew immunity roll.
     *
     * @param immunity immunity value
     * @param roll an integer roll from 0 through 10
     */
    public static boolean resistsWithRoll(int immunity, int roll) {
        int clampedImmunity = Math.min(Math.max(immunity, 0), IMMUNITY_ROLL_BOUND);
        int clampedRoll = Math.min(Math.max(roll, 0), IMMUNITY_ROLL_BOUND - 1);
        return clampedRoll < clampedImmunity;
    }
    
    /**
     * 计算抵抗概率
     * 
     * @param immunity 免疫值
     * @return 抵抗概率 (0.0 - 1.0)
     */
    public static float calculateResistChance(int immunity) {
        int clampedImmunity = Math.min(Math.max(immunity, 0), IMMUNITY_ROLL_BOUND);
        return clampedImmunity / (float) IMMUNITY_ROLL_BOUND;
    }
    
    /**
     * 计算debuff持续时间（考虑免疫减少）
     * 
     * @param baseDuration 基础持续时间（毫秒）
     * @param immunity 免疫值
     * @param debuffType debuff类型
     * @return 实际持续时间
     */
    public static int calculateDuration(int baseDuration, int immunity, DebuffType debuffType) {
        // 某些debuff持续时间不受免疫影响
        if (!debuffType.canReduce()) {
            return baseDuration;
        }

        return baseDuration;
    }

    /**
     * sturdy 类效果：只负责把负面持续时间减半。
     */
    public static int adjustDurationTicks(int baseDurationTicks, boolean halveDuration) {
        if (!halveDuration || baseDurationTicks <= 1) {
            return baseDurationTicks;
        }
        return Math.max(1, baseDurationTicks / 2);
    }
    
    /**
     * 计算持续时间减少比例
     * 
     * @param immunity 免疫值
     * @return 减少比例 (0.0 - 1.0)
     */
    public static float calculateDurationReduction(int immunity) {
        return 0.0f;
    }
    
    /**
     * 获取最大免疫值
     */
    public static int getMaxImmunity() {
        return IMMUNITY_ROLL_BOUND;
    }
}
