package com.stardew.craft.combat.skill;

/**
 * 技能上下文
 * 当武器释放小技能/大技能时传入，用于计算技能伤害乘区
 */
public class SkillContext {
    
    private final String skillId;           // 技能ID
    private final SkillTier tier;           // 技能等级（小技能/大技能）
    private final float damageMultiplier;   // 伤害倍率
    private final boolean ignoreDefense;    // 是否无视防御
    private final boolean guaranteedCrit;   // 是否必定暴击
    private final float critChanceBonus;    // 额外暴击率加成
    private final boolean defaultKnockback; // 是否使用武器默认击退
    
    private SkillContext(Builder builder) {
        this.skillId = builder.skillId;
        this.tier = builder.tier;
        this.damageMultiplier = builder.damageMultiplier;
        this.ignoreDefense = builder.ignoreDefense;
        this.guaranteedCrit = builder.guaranteedCrit;
        this.critChanceBonus = builder.critChanceBonus;
        this.defaultKnockback = builder.defaultKnockback;
    }
    
    // Getters
    public String getSkillId() { return skillId; }
    public SkillTier getTier() { return tier; }
    public float getDamageMultiplier() { return damageMultiplier; }
    public boolean isIgnoreDefense() { return ignoreDefense; }
    public boolean isGuaranteedCrit() { return guaranteedCrit; }
    public float getCritChanceBonus() { return critChanceBonus; }
    public boolean usesDefaultKnockback() { return defaultKnockback; }
    
    /**
     * 技能等级
     */
    public enum SkillTier {
        NORMAL(1.0f),    // 普通攻击
        MINOR(1.0f),     // 小技能
        MAJOR(1.0f);     // 大技能
        
        private final float baseMultiplier;
        
        SkillTier(float baseMultiplier) {
            this.baseMultiplier = baseMultiplier;
        }
        
        public float getBaseMultiplier() {
            return baseMultiplier;
        }
    }
    
    /**
     * 创建普通攻击上下文
     */
    public static SkillContext normalAttack() {
        return builder()
            .skillId("normal")
            .tier(SkillTier.NORMAL)
            .damageMultiplier(1.0f)
            .build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String skillId = "normal";
        private SkillTier tier = SkillTier.NORMAL;
        private float damageMultiplier = 1.0f;
        private boolean ignoreDefense = false;
        private boolean guaranteedCrit = false;
        private float critChanceBonus = 0.0f;
        private boolean defaultKnockback = true;
        
        public Builder skillId(String val) { this.skillId = val; return this; }
        public Builder tier(SkillTier val) { this.tier = val; return this; }
        public Builder damageMultiplier(float val) { this.damageMultiplier = val; return this; }
        public Builder ignoreDefense(boolean val) { this.ignoreDefense = val; return this; }
        public Builder guaranteedCrit(boolean val) { this.guaranteedCrit = val; return this; }
        public Builder critChanceBonus(float val) { this.critChanceBonus = val; return this; }
        public Builder defaultKnockback(boolean val) { this.defaultKnockback = val; return this; }
        
        public SkillContext build() {
            return new SkillContext(this);
        }
    }
}
