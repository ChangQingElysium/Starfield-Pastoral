package com.stardew.craft.item.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 武器稀有度等级
 * 决定武器名称颜色和tooltip边框
 */
public enum WeaponRarity {
    COMMON(1, 4, ChatFormatting.WHITE, "common"),
    UNCOMMON(5, 8, ChatFormatting.GREEN, "uncommon"),
    RARE(9, 12, ChatFormatting.BLUE, "rare"),
    EPIC(13, 16, ChatFormatting.DARK_PURPLE, "epic"),
    LEGENDARY(17, Integer.MAX_VALUE, ChatFormatting.GOLD, "legendary");
    
    private final int minLevel;
    private final int maxLevel;
    private final ChatFormatting color;
    private final String id;
    
    WeaponRarity(int minLevel, int maxLevel, ChatFormatting color, String id) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.color = color;
        this.id = id;
    }
    
    /**
     * 根据武器等级获取稀有度
     */
    public static WeaponRarity fromLevel(int level) {
        for (WeaponRarity rarity : values()) {
            if (level >= rarity.minLevel && level <= rarity.maxLevel) {
                return rarity;
            }
        }
        return COMMON;
    }
    
    public ChatFormatting getColor() {
        return color;
    }
    
    public String getTranslationKey() {
        return "stardewcraft.weapon.rarity." + id;
    }
    
    @SuppressWarnings("null")
    public MutableComponent getDisplayComponent() {
        return Component.translatable(getTranslationKey()).withStyle(color);
    }
}
