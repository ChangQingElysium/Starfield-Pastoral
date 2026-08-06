package com.stardew.craft.block;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * 肥料类型枚举
 */
public enum FertilizerType implements StringRepresentable {
    BASIC_FERTILIZER("basic_fertilizer", 1, 0.0f, 0.0f, false),
    QUALITY_FERTILIZER("quality_fertilizer", 2, 0.0f, 0.0f, false),
    DELUXE_FERTILIZER("deluxe_fertilizer", 3, 0.0f, 0.0f, true),
    BASIC_RETAINING_SOIL("basic_retaining_soil", 0, 0.33f, 0.0f, true),
    QUALITY_RETAINING_SOIL("quality_retaining_soil", 0, 0.66f, 0.0f, true),
    DELUXE_RETAINING_SOIL("deluxe_retaining_soil", 0, 1.0f, 0.0f, true),
    SPEED_GRO("speed_gro", 0, 0.0f, 0.10f, true),
    DELUXE_SPEED_GRO("deluxe_speed_gro", 0, 0.0f, 0.25f, true),
    HYPER_SPEED_GRO("hyper_speed_gro", 0, 0.0f, 0.33f, true);

    private final String name;
    private final int qualityLevel;      // 品质等级 (0-3, 0表示无效果)
    private final float waterRetention;  // 水分保持率 (0.0-1.0)
    private final float speedBoost;      // 生长速度加成 (0.0-1.0)
    private final boolean canApplyToSproutedCrop;

    FertilizerType(
            String name,
            int qualityLevel,
            float waterRetention,
            float speedBoost,
            boolean canApplyToSproutedCrop
    ) {
        this.name = name;
        this.qualityLevel = qualityLevel;
        this.waterRetention = waterRetention;
        this.speedBoost = speedBoost;
        this.canApplyToSproutedCrop = canApplyToSproutedCrop;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    /**
     * 获取品质等级 (仅对肥料类型有效)
     * 0 = 无效果, 1 = 基础, 2 = 高级, 3 = 豪华
     */
    public int getQualityLevel() {
        return qualityLevel;
    }

    /**
     * 获取水分保持率 (仅对保水土壤有效)
     * 返回值范围: 0.0-1.0
     */
    public float getWaterRetention() {
        return waterRetention;
    }

    /**
     * 获取生长速度加成 (仅对生长激素有效)
     * 返回值范围: 0.0-1.0
     */
    public float getSpeedBoost() {
        return speedBoost;
    }

    /**
     * 判断是否是品质肥料
     */
    public boolean isQualityFertilizer() {
        return qualityLevel > 0;
    }

    /**
     * 判断是否是保水土壤
     */
    public boolean isRetainingSoil() {
        return waterRetention > 0;
    }

    /**
     * 判断是否是生长激素
     */
    public boolean isSpeedGro() {
        return speedBoost > 0;
    }

    /**
     * 原版只禁止在作物发芽后施加基础肥料和优质肥料；豪华肥料不受此限制。
     */
    public boolean canApplyToSproutedCrop() {
        return canApplyToSproutedCrop;
    }

    /** Stable parser shared by saves and network payloads. */
    @Nullable
    public static FertilizerType bySerializedName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (FertilizerType type : values()) {
            if (type.name.equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
