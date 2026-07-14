package com.stardew.craft.player;

import net.minecraft.network.chat.Component;

/**
 * 技能类型枚举
 * 对应星露谷物语的5种技能
 */
public enum SkillType {
    FARMING(0, "farming"),
    FISHING(1, "fishing"),
    FORAGING(2, "foraging"),
    MINING(3, "mining"),
    COMBAT(4, "combat");
    
    private final int id;
    private final String name;
    SkillType(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getTranslationKey() {
        return "stardewcraft.skill." + name;
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
    
    /**
     * 根据ID获取技能类型
     */
    public static SkillType fromId(int id) {
        for (SkillType skill : values()) {
            if (skill.id == id) {
                return skill;
            }
        }
        return FARMING;
    }
    
    /**
     * 根据名称获取技能类型
     */
    public static SkillType fromName(String name) {
        for (SkillType skill : values()) {
            if (skill.name.equalsIgnoreCase(name)) {
                return skill;
            }
        }
        return null;
    }
}
