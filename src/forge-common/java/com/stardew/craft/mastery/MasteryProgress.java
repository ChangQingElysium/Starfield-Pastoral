package com.stardew.craft.mastery;

/**
 * Mastery 等级与经验公式 — SDV parity。
 * 阈值来源：StardewValley.Menus.MasteryTrackerMenu.cs:441-467 (getMasteryExpNeededForLevel / getCurrentMasteryLevel)。
 */
public final class MasteryProgress {

    public static final int MAX_LEVEL = 5;

    public static final long[] EXP_FOR_LEVEL = {
        0L,
        10_000L,
        25_000L,
        45_000L,
        70_000L,
        100_000L
    };

    private MasteryProgress() {}

    public static long expForLevel(int level) {
        if (level <= 0) return 0L;
        if (level > MAX_LEVEL) return Long.MAX_VALUE;
        return EXP_FOR_LEVEL[level];
    }

    public static int currentLevel(long masteryExp) {
        int lvl = 0;
        for (int i = 1; i <= MAX_LEVEL; i++) {
            if (masteryExp >= EXP_FOR_LEVEL[i]) {
                lvl = i;
            } else {
                break;
            }
        }
        return lvl;
    }

    /** 当前已达等级总数 - 已消费等级数 = 可领奖次数。 */
    public static int unspentLevels(long masteryExp, int masteryLevelsSpent) {
        return Math.max(0, currentLevel(masteryExp) - masteryLevelsSpent);
    }

    /**
     * SDV parity (Farmer.cs:3041)：技能 Lv10 后再获得的技能经验中，Farming=howMuch/2，其余按全额。
     * 至少 1 点。
     */
    public static int masteryExpFromSkillGain(int skillId, int skillExpGained) {
        if (skillExpGained <= 0) return 0;
        int amount = (skillId == 0) ? (skillExpGained / 2) : skillExpGained;
        return Math.max(1, amount);
    }
}
