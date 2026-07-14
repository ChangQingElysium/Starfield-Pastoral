package com.stardew.craft.block.utility.totem;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nonnull;

/**
 * 图腾柱 / 传送图腾的三种类型，对应星露谷原版的三种传送图腾。
 */
@SuppressWarnings("null")
public enum TotemType implements StringRepresentable {
    FARM("farm", 0xFF55FF55),
    MOUNTAIN("mountain", 0xFFFF5555),
    BEACH("beach", 0xFF5555FF),
    DESERT("desert", 0xFFFFD355);

    private final String id;
    private final int textColor;

    TotemType(String id, int textColor) {
        this.id = id;
        this.textColor = textColor;
    }

    public String getId() {
        return id;
    }

    /** ARGB 颜色，用于浮动文字渲染 */
    public int getTextColor() {
        return textColor;
    }

    /** 系统柱默认名称 */
    public String getDefaultNameKey() {
        return "stardewcraft.totem.type." + id;
    }

    /** SDV sprinkleColor: 传送动画的粒子颜色（RGB，无alpha） */
    public int getSprinkleColor() {
        return switch (this) {
            case FARM -> 0x32CD32;      // LimeGreen
            case MOUNTAIN -> 0xFF4500;  // OrangeRed
            case BEACH -> 0xADD8E6;     // LightBlue
            case DESERT -> 0xF4A460;    // SandyBrown
        };
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return id;
    }

    public static TotemType fromId(String id) {
        for (TotemType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return FARM;
    }
}
