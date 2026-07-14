package com.stardew.craft.combat;

/**
 * 伤害计算结果
 * 记录伤害计算过程中的所有中间值，用于调试和UI显示
 */
public class DamageResult {
    
    private final float baseDamage;
    private final boolean isCrit;
    private final float critMultiplier;
    private final float professionMultiplier;
    private final float skillMultiplier;
    private final float buffMultiplier;
    private final float damageVariance;
    private final String skillId;
    private final float defenseReduction;
    private final float finalDamage;
    private final boolean dodged;
    private final boolean inStardewDimension;  // 是否在星露谷维度
    
    private DamageResult(Builder builder) {
        this.baseDamage = builder.baseDamage;
        this.isCrit = builder.isCrit;
        this.critMultiplier = builder.critMultiplier;
        this.professionMultiplier = builder.professionMultiplier;
        this.skillMultiplier = builder.skillMultiplier;
        this.buffMultiplier = builder.buffMultiplier;
        this.damageVariance = builder.damageVariance;
        this.skillId = builder.skillId;
        this.defenseReduction = builder.defenseReduction;
        this.finalDamage = builder.finalDamage;
        this.dodged = builder.dodged;
        this.inStardewDimension = builder.inStardewDimension;
    }
    
    // Getters
    public float getBaseDamage() { return baseDamage; }
    public boolean isCrit() { return isCrit; }
    public float getCritMultiplier() { return critMultiplier; }
    public float getProfessionMultiplier() { return professionMultiplier; }
    public float getSkillMultiplier() { return skillMultiplier; }
    public float getBuffMultiplier() { return buffMultiplier; }
    public float getDamageVariance() { return damageVariance; }
    public String getSkillId() { return skillId; }
    public float getDefenseReduction() { return defenseReduction; }
    public float getFinalDamage() { return finalDamage; }
    public boolean isDodged() { return dodged; }
    public boolean isInStardewDimension() { return inStardewDimension; }
    
    /**
     * 获取四舍五入后的最终伤害 (用于实际应用)
     */
    public int getFinalDamageInt() {
        return Math.round(finalDamage);
    }
    
    /**
     * 生成伤害计算详情字符串 (用于调试)
     */
    public String toDetailString() {
        if (dodged) {
            return "MISS! (Dodged)";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(inStardewDimension ? "[Stardew Valley] " : "[Overworld] ");
        sb.append(String.format("Base: %.1f", baseDamage));
        
        if (isCrit) {
            sb.append(String.format(" × CRIT(%.1fx)", critMultiplier));
        }
        if (professionMultiplier != 1.0f) {
            sb.append(String.format(" × Prof(%.2fx)", professionMultiplier));
        }
        if (skillMultiplier != 1.0f) {
            sb.append(String.format(" × Skill(%.2fx)[%s]", skillMultiplier, skillId));
        }
        if (buffMultiplier != 1.0f) {
            sb.append(String.format(" × Buff(%.2fx)", buffMultiplier));
        }
        sb.append(String.format(" × Var(%.3f)", damageVariance));
        if (defenseReduction > 0) {
            sb.append(String.format(" - Def(%.1f)", defenseReduction));
        }
        sb.append(String.format(" = %.1f", finalDamage));
        
        return sb.toString();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float baseDamage = 0;
        private boolean isCrit = false;
        private float critMultiplier = 1.0f;
        private float professionMultiplier = 1.0f;
        private float skillMultiplier = 1.0f;
        private float buffMultiplier = 1.0f;
        private float damageVariance = 1.0f;
        private String skillId = null;
        private float defenseReduction = 0;
        private float finalDamage = 0;
        private boolean dodged = false;
        private boolean inStardewDimension = true;
        
        public Builder baseDamage(float val) { this.baseDamage = val; return this; }
        public Builder isCrit(boolean val) { this.isCrit = val; return this; }
        public Builder critMultiplier(float val) { this.critMultiplier = val; return this; }
        public Builder professionMultiplier(float val) { this.professionMultiplier = val; return this; }
        public Builder skillMultiplier(float val) { this.skillMultiplier = val; return this; }
        public Builder buffMultiplier(float val) { this.buffMultiplier = val; return this; }
        public Builder damageVariance(float val) { this.damageVariance = val; return this; }
        public Builder skillId(String val) { this.skillId = val; return this; }
        public Builder defenseReduction(float val) { this.defenseReduction = val; return this; }
        public Builder finalDamage(float val) { this.finalDamage = val; return this; }
        public Builder dodged(boolean val) { this.dodged = val; return this; }
        public Builder inStardewDimension(boolean val) { this.inStardewDimension = val; return this; }
        
        public DamageResult build() {
            return new DamageResult(this);
        }
    }
}
