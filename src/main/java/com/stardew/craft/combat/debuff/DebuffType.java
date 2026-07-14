package com.stardew.craft.combat.debuff;

import net.minecraft.network.chat.Component;

/**
 * Debuff类型枚举
 * 参考星露谷物语中怪物可以施加的负面效果
 */
public enum DebuffType {
    // 减益效果
    SLIMED("slimed", 4000, true),              // 史莱姆粘液 - 减速
    JINXED("jinxed", 8000, true),              // 诅咒 - 降低防御
    WEAKNESS("weakness", 8000, true),            // 虚弱 - 降低攻击
    DARKNESS("darkness", 6000, true),            // 黑暗 - 降低命中
    FROZEN("frozen", 2000, false),             // 冻结 - 无法移动
    BURNING("burning", 6000, true),             // 燃烧 - 持续伤害
    NAUSEATED("nauseated", 5000, true),           // 恶心 - 无法恢复体力
    STUNNED("stunned", 1500, false);            // 眩晕 - 无法行动
    
    private final String id;
    private final int defaultDuration;  // 默认持续时间（毫秒）
    private final boolean canReduce;    // 是否可以通过免疫降低持续时间
    
    DebuffType(String id, int defaultDuration, boolean canReduce) {
        this.id = id;
        this.defaultDuration = defaultDuration;
        this.canReduce = canReduce;
    }
    
    public String getTranslationKey() { return "stardewcraft.combat.debuff." + id; }
    public Component getDisplayName() { return Component.translatable(getTranslationKey()); }
    public int getDefaultDuration() { return defaultDuration; }
    public boolean canReduce() { return canReduce; }
}
