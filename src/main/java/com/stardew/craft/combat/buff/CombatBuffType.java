package com.stardew.craft.combat.buff;

import net.minecraft.network.chat.Component;

/**
 * 战斗相关Buff类型
 * 
 * 这里只定义影响战斗的buff，其他buff（如钓鱼、采集等）
 * 应该在对应的系统中定义
 */
public enum CombatBuffType {
    // 攻击相关
    ATTACK("attack", true),         // 增加攻击力
    CRITICAL("critical", true),       // 增加暴击率
    
    // 防御相关
    DEFENSE("defense", true),        // 增加防御力
    IMMUNITY("immunity", true),       // 增加免疫值
    
    // 移动相关
    SPEED("speed", true),          // 增加移动速度
    
    // 战士技能buff
    WARRIOR_ENERGY("warrior_energy", true),     // 战士技能 - 攻击时恢复体力
    
    // 侦察技能buff
    ACROBAT_COOLDOWN("acrobat_cooldown", true),     // 特殊移动技能冷却减少
    
    // 特殊食物buff
    ROCK_CANDY("rock_candy", true),          // +250最大体力
    MONSTER_MUSK("monster_musk", false);        // 增加怪物遭遇率
    
    private final String id;
    private final boolean isBeneficial;  // 是否有益buff
    
    CombatBuffType(String id, boolean isBeneficial) {
        this.id = id;
        this.isBeneficial = isBeneficial;
    }
    
    public String getTranslationKey() { return "stardewcraft.combat.buff." + id; }
    public Component getDisplayName() { return Component.translatable(getTranslationKey()); }
    public boolean isBeneficial() { return isBeneficial; }
}
