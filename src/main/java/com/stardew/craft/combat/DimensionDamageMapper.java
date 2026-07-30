package com.stardew.craft.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 维度数值边界
 * 
 * 核心设计：
 * - 在星露谷维度内，使用原版星露谷数值（100HP、武器伤害10-100+等）
 * - 星露谷武器与攻击伤害在所有维度都保持同一数值，不做削弱
 * - 仅生命显示仍需在星露谷100HP与Minecraft 20HP之间换算
 * 
 * 映射比例：
 * - 伤害: 1:1（其他维度允许星露谷武器保持高强度）
 * - 生命: 5:1 （星露谷100HP = MC 20HP = 10颗心）
 */
public class DimensionDamageMapper {
    
    // 星露谷维度的资源路径
    private static final ResourceLocation STARDEW_VALLEY_DIMENSION =
        ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_valley");
    private static final ResourceLocation STARDEW_MINING_DIMENSION =
        ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_mining");
    
    // 生命映射比例
    private static final float HEALTH_RATIO = 5.0f;
    
    /**
     * 检查玩家是否在星露谷维度
     */
    public static boolean isInStardewDimension(ServerPlayer player) {
        ResourceLocation id = player.level().dimension().location();
        return STARDEW_VALLEY_DIMENSION.equals(id) || STARDEW_MINING_DIMENSION.equals(id);
    }
    
    /**
     * 检查实体是否在星露谷维度
     */
    public static boolean isInStardewDimension(LivingEntity entity) {
        ResourceLocation id = entity.level().dimension().location();
        return STARDEW_VALLEY_DIMENSION.equals(id) || STARDEW_MINING_DIMENSION.equals(id);
    }
    
    /**
     * 映射伤害值
     * 
     * @param stardewDamage 星露谷原版伤害值
     * @param isInStardewDimension 是否在星露谷维度
     * @return 实际应用的伤害值
     */
    public static float mapDamage(float stardewDamage, boolean isInStardewDimension) {
        return stardewDamage;
    }
    
    /**
     * 映射生命值
     * 
     * @param stardewHealth 星露谷原版生命值
     * @param isInStardewDimension 是否在星露谷维度
     * @return 实际应用的生命值
     */
    public static float mapHealth(float stardewHealth, boolean isInStardewDimension) {
        if (isInStardewDimension) {
            return stardewHealth;
        } else {
            return stardewHealth / HEALTH_RATIO;
        }
    }
    
    /** 伤害不再进行反向维度换算；保留入口以兼容现有调用。 */
    public static float reverseMapDamage(float mcDamage, boolean isInStardewDimension) {
        return mcDamage;
    }
    
    /**
     * 获取伤害映射比例
     */
    public static float getDamageRatio() {
        return 1.0f;
    }
    
    /**
     * 获取生命映射比例
     */
    public static float getHealthRatio() {
        return HEALTH_RATIO;
    }
    
    /**
     * 获取星露谷维度ID
     */
    public static ResourceLocation getStardewDimensionId() {
        return STARDEW_VALLEY_DIMENSION;
    }

    /**
     * 获取所有星露谷维度ID
     */
    public static ResourceLocation[] getStardewDimensionIds() {
        return new ResourceLocation[] { STARDEW_VALLEY_DIMENSION, STARDEW_MINING_DIMENSION };
    }
    
    // ==================== 伤害类型处理 ====================
    
    /**
     * 伤害模式
     */
    public enum DamageMode {
        /** 星露谷模式：使用100HP系统，高数值伤害 */
        STARDEW,
        /** Minecraft模式：使用20HP系统，低数值伤害 */
        MINECRAFT
    }
    
    /**
     * 获取当前伤害模式
     */
    public static DamageMode getDamageMode(LivingEntity entity) {
        if (isInStardewDimension(entity)) {
            return DamageMode.STARDEW;
        } else {
            return DamageMode.MINECRAFT;
        }
    }
}
