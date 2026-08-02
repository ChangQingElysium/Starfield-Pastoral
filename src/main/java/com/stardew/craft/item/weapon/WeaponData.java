package com.stardew.craft.item.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.StardewWeaponSpeedRules;
import com.stardew.craft.combat.StardewWeaponKnockbackRules;
import com.stardew.craft.combat.WeaponType;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 武器数据类
 * 包含所有武器属性和技能信息
 */
public class WeaponData {
    
    private final String id;
    private final String legacyName;
    private final WeaponType weaponType;
    private final int level;
    private final int damageMin;
    private final int damageMax;
    private final double critChance;
    private final double critMultiplier;
    private final int speed;
    private final int rawSpeed;
    private final int defense;
    private final int precision;
    private final double knockback;
    private final WeaponSkillData skill1;
    private final WeaponSkillData skill2;
    private final String legacyLoreKey;
    
    private WeaponData(Builder builder) {
        this.id = builder.id;
        this.legacyName = builder.legacyName;
        this.weaponType = Objects.requireNonNull(builder.weaponType, "weaponType");
        this.level = builder.level;
        this.damageMin = builder.damageMin;
        this.damageMax = builder.damageMax;
        this.critChance = builder.critChance;
        this.critMultiplier = builder.critMultiplier;
        this.speed = builder.speed;
        this.rawSpeed = builder.rawSpeed != null
                ? builder.rawSpeed
                : StardewWeaponSpeedRules.rawSpeed(weaponType, speed);
        this.defense = builder.defense;
        this.precision = builder.precision;
        this.knockback = builder.knockback != null
                ? builder.knockback
                : StardewWeaponKnockbackRules.defaultRawKnockback(weaponType);
        this.skill1 = builder.skill1;
        this.skill2 = builder.skill2;
        this.legacyLoreKey = builder.legacyLoreKey;
    }
    
    // Getters
    public String getId() { return id; }
    /** @deprecated Display names are translation-driven; retained for API compatibility. */
    @Deprecated(forRemoval = false)
    public String getName() { return legacyName; }
    public WeaponType getWeaponType() { return weaponType; }
    public int getLevel() { return level; }
    public int getDamageMin() { return damageMin; }
    public int getDamageMax() { return damageMax; }
    public double getCritChance() { return critChance; }
    public double getCritMultiplier() { return critMultiplier; }
    /** @deprecated Use {@link #getCritMultiplier()}. */
    @Deprecated(forRemoval = false)
    public double getCritPower() { return critMultiplier; }
    public int getSpeed() { return speed; }
    public int getRawSpeed() { return rawSpeed; }
    public int getDefense() { return defense; }
    public int getPrecision() { return precision; }
    public double getKnockback() { return knockback; }
    /** @deprecated Use {@link #getKnockback()}. */
    @Deprecated(forRemoval = false)
    public double getWeight() { return knockback; }
    public WeaponSkillData getSkill1() { return skill1; }
    public WeaponSkillData getSkill2() { return skill2; }
    /** @deprecated Lore is translation-driven; retained for API compatibility. */
    @Deprecated(forRemoval = false)
    public String getLoreKey() { return legacyLoreKey; }

    public WeaponSkillData getSkill(boolean majorSkill) {
        return majorSkill ? skill2 : skill1;
    }
    
    /**
     * 获取武器稀有度
     */
    public WeaponRarity getRarity() {
        return WeaponRarity.fromLevel(level);
    }
    
    /**
     * 获取平均伤害
     */
    public double getAverageDamage() {
        return (damageMin + damageMax) / 2.0;
    }
    
    public static Builder builder(String id) {
        return new Builder(id);
    }
    
    public static class Builder {
        private final String id;
        private String legacyName = "";
        private WeaponType weaponType = WeaponType.SWORD;
        private int level = 1;
        private int damageMin = 1;
        private int damageMax = 1;
        private double critChance = 0.02;
        private double critMultiplier = 3.0;
        private int speed = 0;
        private Integer rawSpeed;
        private int defense = 0;
        private int precision = 0;
        private Double knockback;
        private WeaponSkillData skill1 = null;
        private WeaponSkillData skill2 = null;
        private String legacyLoreKey = "";
        
        public Builder(String id) {
            this.id = id;
        }

        /** @deprecated Display names are translation-driven; retained for API compatibility. */
        @Deprecated(forRemoval = false)
        public Builder name(String name) {
            this.legacyName = name == null ? "" : name;
            return this;
        }
        
        public Builder type(WeaponType type) {
            this.weaponType = Objects.requireNonNull(type, "type");
            return this;
        }
        
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        
        public Builder damage(int min, int max) {
            this.damageMin = min;
            this.damageMax = max;
            return this;
        }
        
        public Builder critChance(double chance) {
            this.critChance = chance;
            return this;
        }
        
        /** Sets Stardew's direct critical damage multiplier (3.0 = default). */
        public Builder critMultiplier(double multiplier) {
            this.critMultiplier = multiplier;
            return this;
        }

        /** @deprecated Use {@link #critMultiplier(double)}. */
        @Deprecated(forRemoval = false)
        public Builder critPower(double power) {
            return critMultiplier(power);
        }
        
        public Builder speed(int speed) {
            this.speed = speed;
            return this;
        }

        /** Sets Stardew's runtime Speed when Tooltip integer division is lossy. */
        public Builder rawSpeed(int rawSpeed) {
            this.rawSpeed = rawSpeed;
            return this;
        }
        
        public Builder defense(int defense) {
            this.defense = defense;
            return this;
        }

        public Builder precision(int precision) {
            this.precision = precision;
            return this;
        }
        
        /** Sets Stardew's raw weapon Knockback, not the Tooltip weight delta. */
        public Builder knockback(double knockback) {
            this.knockback = knockback;
            return this;
        }

        /** @deprecated Use {@link #knockback(double)}. */
        @Deprecated(forRemoval = false)
        public Builder weight(double weight) {
            return knockback(weight);
        }
        
        public Builder skill1(WeaponSkillData skill) {
            this.skill1 = skill;
            return this;
        }
        
        public Builder skill2(WeaponSkillData skill) {
            this.skill2 = skill;
            return this;
        }

        /** @deprecated Lore is translation-driven; retained for API compatibility. */
        @Deprecated(forRemoval = false)
        public Builder loreKey(String loreKey) {
            this.legacyLoreKey = loreKey == null ? "" : loreKey;
            return this;
        }
        
        public WeaponData build() {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Weapon id must not be blank");
            }
            if (ResourceLocation.tryBuild(StardewCraft.MODID, id) == null) {
                throw new IllegalStateException("Invalid built-in weapon id: " + id);
            }
            if (level < 0) {
                throw new IllegalStateException("Weapon level must not be negative: " + id);
            }
            if (damageMin < 0 || damageMax < damageMin) {
                throw new IllegalStateException(
                        "Invalid weapon damage range for " + id + ": " + damageMin + "-" + damageMax
                );
            }
            if (!Double.isFinite(critChance) || critChance < 0.0 || critChance > 1.0) {
                throw new IllegalStateException("Invalid weapon critical chance for " + id + ": " + critChance);
            }
            if (!Double.isFinite(critMultiplier) || critMultiplier < 0.0) {
                throw new IllegalStateException(
                        "Invalid weapon critical multiplier for " + id + ": " + critMultiplier
                );
            }
            if (precision < 0) {
                throw new IllegalStateException("Weapon precision must not be negative: " + id);
            }
            if (knockback != null
                    && (!Double.isFinite(knockback) || knockback < 0.0)) {
                throw new IllegalStateException("Invalid weapon knockback for " + id + ": " + knockback);
            }
            return new WeaponData(this);
        }
    }

}
