package com.stardew.craft.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 怪物属性
 * 星露谷怪物的战斗属性
 */
public class MonsterStats {
    
    // NBT标签名（用于自定义怪物数据）
    public static final String TAG_STARDEW_MONSTER = "StardewMonster";
    public static final String TAG_DATA_VERSION = "DataVersion";
    public static final String TAG_DAMAGE = "Damage";
    public static final String TAG_RESILIENCE = "Resilience";  // 韧性/护甲
    public static final String TAG_MISS_CHANCE = "MissChance"; // 闪避率
    public static final String TAG_EXPERIENCE = "Experience";
    public static final String TAG_IS_DANGEROUS = "IsDangerous"; // 是否危险版本
    public static final int CURRENT_DATA_VERSION = 1;
    
    private final float damage;          // 对玩家造成的伤害
    private final float resilience;      // 韧性（减伤）
    private final float missChance;      // 闪避率
    private final int experience;        // 击杀经验
    private final boolean isDangerous;   // 是否危险模式怪物
    
    private MonsterStats(Builder builder) {
        this.damage = builder.damage;
        this.resilience = builder.resilience;
        this.missChance = builder.missChance;
        this.experience = builder.experience;
        this.isDangerous = builder.isDangerous;
    }
    
    /**
     * 从实体读取怪物属性
     */
    public static MonsterStats fromEntity(LivingEntity entity) {
        if (entity == null) {
            return empty();
        }
        
        // 尝试从NBT读取自定义属性
        if (entity instanceof Mob mob) {
            CompoundTag persistentData = mob.getPersistentData();
            if (persistentData.contains(TAG_STARDEW_MONSTER)) {
                CompoundTag monsterTag = persistentData.getCompound(TAG_STARDEW_MONSTER);
                return fromNBT(monsterTag);
            }
        }
        
        // 原版怪物：返回默认属性（可根据实体类型映射）
        return fromVanillaEntity(entity);
    }
    
    /**
     * 从NBT读取
     */
    public static MonsterStats fromNBT(CompoundTag tag) {
        return builder()
            .damage(tag.getFloat(TAG_DAMAGE))
            .resilience(tag.getFloat(TAG_RESILIENCE))
            .missChance(tag.getFloat(TAG_MISS_CHANCE))
            .experience(tag.getInt(TAG_EXPERIENCE))
            .isDangerous(tag.getBoolean(TAG_IS_DANGEROUS))
            .build();
    }
    
    /**
     * 写入NBT
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putFloat(TAG_DAMAGE, damage);
        tag.putFloat(TAG_RESILIENCE, resilience);
        tag.putFloat(TAG_MISS_CHANCE, missChance);
        tag.putInt(TAG_EXPERIENCE, experience);
        tag.putBoolean(TAG_IS_DANGEROUS, isDangerous);
        return tag;
    }
    
    /**
     * 将属性写入实体
     */
    @SuppressWarnings("null")
    public void writeToEntity(Mob mob) {
        mob.getPersistentData().put(TAG_STARDEW_MONSTER, toNBT());
    }
    
    /**
     * 原版实体的默认属性映射
     */
    private static MonsterStats fromVanillaEntity(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        return builder()
            .damage(attack == null ? 0.0f : (float) attack.getValue())
            .resilience(armor == null ? 0.0f : (float) armor.getValue())
            .missChance(0)
            .experience(0)
            .build();
    }
    
    /**
     * 空属性
     */
    public static MonsterStats empty() {
        return builder().build();
    }
    
    // Getters
    public float getDamage() { return damage; }
    public float getResilience() { return resilience; }
    public float getMissChance() { return missChance; }
    public int getExperience() { return experience; }
    public boolean isDangerous() { return isDangerous; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float damage = 0;
        private float resilience = 0;
        private float missChance = 0;
        private int experience = 0;
        private boolean isDangerous = false;
        
        public Builder damage(float val) { this.damage = val; return this; }
        public Builder resilience(float val) { this.resilience = val; return this; }
        public Builder missChance(float val) { this.missChance = val; return this; }
        public Builder experience(int val) { this.experience = val; return this; }
        public Builder isDangerous(boolean val) { this.isDangerous = val; return this; }
        
        public MonsterStats build() {
            return new MonsterStats(this);
        }
    }
}
